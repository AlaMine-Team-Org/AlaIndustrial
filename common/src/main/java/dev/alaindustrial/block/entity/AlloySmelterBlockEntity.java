package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.AlloySmelterMenu;
import dev.alaindustrial.recipe.AlloyRecipeInput;
import dev.alaindustrial.recipe.AlloyingRecipe;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModRecipes;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Three-input LV machine that melts two or three metals into one alloy (MOD-064).
 *
 * <p>Extends {@link MachineBlockEntity} directly rather than
 * {@link AbstractProcessingMachineBlockEntity}, following the Vulcanizer and the Galvanic Bath: that
 * base is built around one input slot at index 0 and a hard-coded {@code shrink(1)}, neither of which
 * fits a machine whose components can sit in any of three slots and are consumed several at a time.
 * The five machines on that base are untouched by this one.
 *
 * <p>The one genuinely new rule here is the input filter — see {@link #canPlaceItem}.
 */
public final class AlloySmelterBlockEntity extends MachineBlockEntity implements MenuProvider {
	public static final int INPUT_SLOT_0 = 0;
	public static final int INPUT_SLOT_1 = 1;
	public static final int INPUT_SLOT_2 = 2;
	public static final int OUTPUT_SLOT = 3;
	public static final int SLOT_COUNT = 4;
	private static final int OUTPUT_MAX = 64;
	private static final int[] NO_SLOTS = new int[0];

	private final RecipeManager.CachedCheck<AlloyRecipeInput, AlloyingRecipe> recipeCheck =
			ModRecipes.ALLOYING.newCheck();

	public AlloySmelterBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.ALLOY_SMELTER_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
		this.maxProgress = Config.scaledDuration(Config.alloySmelterDuration);
	}

	/**
	 * EU drawn per working tick — the smelter's own tariff, scaled by the global speed knob so one
	 * operation keeps costing what the recipe says (see {@link #onServerTick}).
	 */
	private static int euPerTick() {
		return Math.max(1, Math.round(Config.alloySmelterEuPerTick * Config.globalMachineSpeedMultiplier));
	}

	/** The three input slots as the recipe layer sees them — position carries no meaning. */
	private AlloyRecipeInput currentInput() {
		return new AlloyRecipeInput(items.get(INPUT_SLOT_0), items.get(INPUT_SLOT_1), items.get(INPUT_SLOT_2));
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		// The smelter is one of the machines with its own tariff, so it does NOT read
		// machineEuPerTickEffective() — that is the shared 2 EU/t rate, and using it here would silently
		// quarter the price the balance docs and the recipe viewers both state.
		//
		// The speed multiplier is applied to the RATE as well as the duration, exactly as the incubator
		// does it. Config calls the knob "energy-neutral", and it only is if both move together: scaling
		// the duration alone would make a 2x-speed smelter run a 1200 EU recipe for 600 EU, while the
		// docs and both recipe viewers went on printing 1200.
		int euPerTick = euPerTick();
		AlloyRecipeInput input = currentInput();
		AlloyingRecipe recipe = level instanceof ServerLevel server
				? ModRecipes.lookup(recipeCheck, server, input)
				: null;

		// Duration divides by the UNSCALED rate; scaledDuration then applies the multiplier once. Using
		// the scaled rate here would apply it twice and cancel the speed-up entirely.
		int baseDuration = recipe != null && recipe.energy() > 0
				? Math.max(1, recipe.energy() / Math.max(1, Config.alloySmelterEuPerTick))
				: Config.alloySmelterDuration;
		maxProgress = Config.scaledDuration(baseDuration);

		if (recipe == null) {
			if (progress != 0) {
				progress = 0;
				setChanged();
			}
			updateLit(false);
			return IDLE_SLEEP_TICKS;
		}

		// Recomputed with counts: the same recipe can match (right metals) yet not be affordable (too
		// few of one of them). The assignment is captured so consumption takes each component from the
		// very slot the affordability check approved.
		int[] assignment = recipe.assign(input, true);
		ItemStack result = recipe.resultStack();
		boolean canWork = assignment != null && energy.amount >= euPerTick && canOutput(result);

		updateLit(canWork);
		if (!canWork) {
			return IDLE_SLEEP_TICKS;
		}

		energy.amount -= euPerTick;
		progress++;
		if (progress >= maxProgress) {
			recipe.consume(List.of(items.get(INPUT_SLOT_0), items.get(INPUT_SLOT_1), items.get(INPUT_SLOT_2)),
					assignment);
			addOutput(result);
			progress = 0;
			creditUsefulWork(level, (long) euPerTick * maxProgress); // MOD-133: completed op → XP
		}
		setChanged();
		return 0;
	}

	private boolean canOutput(ItemStack result) {
		if (result.isEmpty()) {
			return false;
		}
		ItemStack out = items.get(OUTPUT_SLOT);
		return out.isEmpty() || (ItemStack.isSameItem(out, result)
				&& out.getCount() + result.getCount() <= Math.min(OUTPUT_MAX, out.getMaxStackSize()));
	}

	private void addOutput(ItemStack result) {
		ItemStack out = items.get(OUTPUT_SLOT);
		if (out.isEmpty()) {
			items.set(OUTPUT_SLOT, result.copy());
		} else {
			out.grow(result.getCount());
		}
	}

	/** Whether {@code slot} is one of the three interchangeable input slots. */
	public static boolean isInputSlot(int slot) {
		return slot == INPUT_SLOT_0 || slot == INPUT_SLOT_1 || slot == INPUT_SLOT_2;
	}

	/**
	 * Accept a stack only if some alloy recipe wants it, no other input slot already holds the same item,
	 * and filling this slot cannot strand the machine.
	 *
	 * <p>All three clauses exist to stop the machine deadlocking itself, because a hopper only ever
	 * pushes — it cannot pull a mistake back out, so any state no recipe matches is permanent until a
	 * player intervenes.
	 *
	 * <ul>
	 * <li><b>No duplicates.</b> Every slot takes every component, so a hopper full of copper would
	 * otherwise fill all three inputs with copper. Refusing it leaves the surplus visibly backing up in
	 * the hopper and keeps the slots free for the tin the recipe still needs.</li>
	 * <li><b>No more filled slots than any recipe can use.</b> Distinct metals slip past the duplicate
	 * rule: copper, tin and nickel are each a valid component, so three hoppers would happily occupy all
	 * three inputs — and with no three-component alloy shipped, nothing matches. The cap is read from the
	 * loaded recipes rather than hardcoded, so the day a three-component alloy is added the rule relaxes
	 * on its own.</li>
	 * </ul>
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (!isInputSlot(slot) || stack.isEmpty()) {
			return false;
		}
		int filledOthers = 0;
		for (int other : new int[] {INPUT_SLOT_0, INPUT_SLOT_1, INPUT_SLOT_2}) {
			if (other == slot) {
				continue;
			}
			if (ItemStack.isSameItem(items.get(other), stack)) {
				return false;
			}
			if (!items.get(other).isEmpty()) {
				filledOthers++;
			}
		}
		// Topping up a slot that already holds this item does not occupy a new one, so it is never capped.
		if (items.get(slot).isEmpty() && filledOthers + 1 > maxRecipeComponents()) {
			return false;
		}
		return isRecipeComponent(stack);
	}

	/**
	 * The largest component count among the loaded alloy recipes — how many inputs it is ever useful to
	 * occupy. Falls back to the full slot count off-server, where the recipes are not readable.
	 */
	private int maxRecipeComponents() {
		if (!(level instanceof ServerLevel server)) {
			return AlloyRecipeInput.SLOT_COUNT;
		}
		int max = 0;
		for (RecipeHolder<?> holder : server.recipeAccess().getRecipes()) {
			if (holder.value() instanceof AlloyingRecipe alloy) {
				max = Math.max(max, alloy.components().size());
			}
		}
		return max == 0 ? AlloyRecipeInput.SLOT_COUNT : max;
	}

	/**
	 * Whether any loaded alloying recipe uses {@code stack} as one of its components.
	 *
	 * <p>Walks {@code getRecipes()} and filters by type rather than asking for the alloying type
	 * directly: {@code RecipeMap#byType} would be the narrower call, but the only public route to a
	 * {@code RecipeMap} in 26.2 is NeoForge's patched {@code RecipeManager#recipeMap()}, which
	 * {@code common/} must not depend on (verified against the mojmap jar with {@code javap}).
	 */
	private boolean isRecipeComponent(ItemStack stack) {
		if (!(level instanceof ServerLevel server)) {
			// Off-server the recipes are not readable. The GUI does not reach this branch — the client
			// menu holds a SimpleContainer and predicts with the ingot tag instead (AlloySmelterMenu) —
			// so this only answers non-GUI callers, and the server re-checks every real insert.
			return true;
		}
		for (RecipeHolder<?> holder : server.recipeAccess().getRecipes()) {
			if (holder.value() instanceof AlloyingRecipe alloy && alloy.acceptsAnywhere(stack)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == OUTPUT_SLOT;
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		if (side == Direction.DOWN) {
			return NO_SLOTS;
		}
		return super.getSlotsForFace(side);
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return side != Direction.DOWN && super.canPlaceItemThroughFace(slot, stack, side);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return side != Direction.DOWN && super.canTakeItemThroughFace(slot, stack, side);
	}

	/**
	 * Restart the operation when the composition of the inputs changes.
	 *
	 * <p>The shared base only watches slot 0 (it was written for the one-input machines), which here
	 * would let a player swap the metal in slot 1 or 2 mid-melt and keep the progress paid for the old
	 * mix — the same exploit {@link VulcanizerBlockEntity} closes the same way.
	 */
	@Override
	public void setItem(int slot, ItemStack stack) {
		if (isInputSlot(slot) && !ItemStack.isSameItem(items.get(slot), stack)) {
			progress = 0;
		}
		super.setItem(slot, stack);
	}

	/** Consumer: every face accepts energy except the inert FACING front (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.alloy_smelter");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new AlloySmelterMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
