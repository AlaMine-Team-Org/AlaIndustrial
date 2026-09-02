package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.food.CanningMath;
import dev.alaindustrial.core.food.CanningRules;
import dev.alaindustrial.menu.CanningMachineMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Two-input LV machine that renders any food down to identical rations (MOD-383).
 *
 * <p><b>Why this one has no recipe type.</b> Every other processing machine here matches a JSON
 * recipe on a fixed ingredient. This one accepts anything carrying a food component, and what comes
 * out never varies, so there is nothing for a recipe to say. The exchange lives in
 * {@link CanningMath} instead, where the L1 lane can reach it without a game.
 *
 * <p><b>Two steps, and only the second one costs.</b> Absorbing food into the buffer is free,
 * instant, and unconditional (MOD-488) — it runs even with no can loaded yet, no power, or a full
 * output, so a player can pre-feed calories before the machine can press anything. Pressing a
 * ration is the one timed, paid cycle, and it alone still needs a can, power and output room.
 * Charging per item swallowed instead would make canning five sweet berries cost five times what one
 * steak costs — punishing precisely the scrap-consolidation this machine exists for.
 */
public final class CanningMachineBlockEntity extends MachineBlockEntity
		implements Overclockable, MenuProvider {
	public static final int FOOD_SLOT = 0;
	public static final int CAN_SLOT = 1;
	public static final int OUTPUT_SLOT = 2;
	public static final int SLOT_COUNT = 3;
	/** Base four channels plus the buffer and the rate behind it, so the gauge can draw itself. */
	public static final int DATA_COUNT = MachineBlockEntity.DATA_COUNT + 2;
	private static final int DATA_FOOD_BUFFER = 4;
	private static final int DATA_VALUE_PER_RATION = 5;

	/** Accumulated food value in tenths; survives save/load and is shown as the calorie gauge. */
	private int foodBuffer;

	/** The shared eight-step tick loop (MOD-557). */
	private final ProcessingCycle cycle = new ProcessingCycle(this);

	public CanningMachineBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.CANNING_MACHINE_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
		this.maxProgress = Config.scaledDuration(Config.canningMachineDuration);
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		int valuePerRation = Config.canningFoodValuePerCan;
		// The cycle derives the draw and the length through the instance helpers, so a player's
		// overclocker chip is actually seen (MOD-392) — Config's statics cannot see the upgrade panel.
		ProcessingCycle.Job job = cycle.job(Config.machineEuPerTick, Config.canningMachineDuration);

		// Everything the PRESS needs apart from the calories themselves.
		boolean pressReady = !items.get(CAN_SLOT).isEmpty()
				&& canOutput()
				&& energy.getAmount() >= job.euPerTick();

		// Absorption is unconditional (MOD-488): a player pre-feeding food before the first empty can
		// arrives should see it banked as calories right away, not sitting untouched in the slot. Only
		// the paid PRESS step still needs a can, power and output room.
		boolean absorbed = absorbFood(valuePerRation);
		boolean canWork = pressReady && CanningMath.hasFullRation(foodBuffer, valuePerRation);

		// The shared cycle (MOD-557) owns the lit state, the rate report, the drain, the progress step,
		// the operation counter, the XP credit and the sleep answer.
		//
		// Unlike the recipe machines, ANY stall abandons the press rather than freezing it — hence
		// jobIntact(canWork). Nothing is lost by that: the calories stay banked in the buffer and the can
		// stays in its slot, so the only thing thrown away is a partially pressed lid.
		//
		// Absorption happens outside the cycle and can move items on a tick that does no work, so it is
		// declared here instead of calling setChanged() a second time.
		return job.canWork(canWork)
				.jobIntact(canWork)
				.alreadyChanged(absorbed)
				.run(level, () -> {
					foodBuffer = CanningMath.consumeRation(foodBuffer, valuePerRation);
					items.get(CAN_SLOT).shrink(1);
					if (items.get(CAN_SLOT).isEmpty()) {
						items.set(CAN_SLOT, ItemStack.EMPTY);
					}
					addRation();
				});
	}

	/**
	 * Pull food off the input slot until the buffer covers one ration. Returns whether anything moved.
	 *
	 * <p>The loop stops on a zero-value item rather than dropping it, so a food whose nutrition a mod
	 * declared as zero jams the slot visibly instead of being voided one item per tick.
	 */
	private boolean absorbFood(int valuePerRation) {
		boolean changed = false;
		while (CanningMath.wantsMoreFood(foodBuffer, valuePerRation)) {
			ItemStack food = items.get(FOOD_SLOT);
			if (food.isEmpty()) {
				break;
			}
			int value = CanningRules.foodValue(food);
			if (value <= 0 || !CanningRules.isCannable(food)) {
				break;
			}
			foodBuffer = CanningMath.addToBuffer(foodBuffer, value);
			food.shrink(1);
			if (food.isEmpty()) {
				items.set(FOOD_SLOT, ItemStack.EMPTY);
			}
			changed = true;
		}
		return changed;
	}

	private boolean canOutput() {
		ItemStack out = items.get(OUTPUT_SLOT);
		if (out.isEmpty()) {
			return true;
		}
		return out.is(ModContent.CANNED_RATION.get())
				&& out.getCount() + 1 <= Math.min(OUTPUT_MAX, out.getMaxStackSize());
	}

	private void addRation() {
		ItemStack out = items.get(OUTPUT_SLOT);
		if (out.isEmpty()) {
			items.set(OUTPUT_SLOT, new ItemStack(ModContent.CANNED_RATION.get()));
		} else {
			out.grow(1);
		}
	}

	/** Calories banked so far, in tenths — read by the menu for the gauge. */
	public int foodBuffer() {
		return foodBuffer;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			case FOOD_SLOT -> CanningRules.isCannable(stack);
			case CAN_SLOT -> stack.is(ModContent.EMPTY_CAN.get());
			default -> false;
		};
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == OUTPUT_SLOT;
	}

	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	private final ContainerData canningData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_FOOD_BUFFER -> foodBuffer;
				case DATA_VALUE_PER_RATION -> Config.canningFoodValuePerCan;
				default -> CanningMachineBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			if (index != DATA_FOOD_BUFFER && index != DATA_VALUE_PER_RATION) {
				CanningMachineBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return canningData;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("FoodBuffer", foodBuffer);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		// Clamped on the way in as well as on the way up: a hand-edited or mod-corrupted save must not
		// hand the GUI sync a value the 16-bit channel cannot carry.
		foodBuffer = Math.max(0, Math.min(CanningMath.MAX_BUFFER, input.getIntOr("FoodBuffer", 0)));
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.canning_machine");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new CanningMachineMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
