package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.machine.ComponentRepair;
import dev.alaindustrial.core.machine.ComponentTier;
import dev.alaindustrial.core.machine.RepairStatus;
import dev.alaindustrial.item.misc.DurableComponentItem;
import dev.alaindustrial.menu.ComponentRepairBenchMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LV Component Repair Bench (spec: alaindustrial:component_repair_bench, MOD-384) — restores a worn
 * wind-mill rotor or water-mill wheel instead of making the player craft a new one, at the price of a
 * permanently lower durability ceiling on that particular component.
 *
 * <p>One repair: wear back to zero, one item of the grade's material consumed, the grade's EU cost
 * spent, and the stack's ceiling dropped by {@link Config#repairBenchMaxDamageDecayPercent} of its
 * ORIGINAL value. Once the ceiling would reach zero the bench refuses — the decay, not the price, is
 * what keeps a consumable a consumable, and the allowance is derived from the step rather than
 * configured beside it (see {@link ComponentRepair#maxRepairs}).
 *
 * <p><b>Why this is not an {@link AbstractProcessingMachineBlockEntity}.</b> That base is built around
 * "input → recipe → a NEW stack in the output slot": {@code resolveInput} hands back a finished
 * {@code ItemStack} and the tick loop shrinks the input and grows the output. A repair produces no new
 * item at all — it mutates components on the very stack the player put in, and that stack must stay
 * put, keeping its own wear history. So the bench owns its tick loop and only mirrors the base's
 * <em>contract</em>: EU spent per working tick, progress frozen (not reset) on a stall, reset when the
 * job itself goes away, {@link #IDLE_SLEEP_TICKS} when idle. It needs no recipe type and no recipe JSON.
 *
 * <p><b>Automation.</b> The target slot is insertable by hand for ANY grade — including a spent one, so
 * the player can drop it in and read why it is refused — but a hopper may only insert a component that
 * is genuinely repairable, and may only extract one the bench is finished with
 * ({@link #isOutputSlot}). That asymmetry is what makes an automatic repair line possible without the
 * obvious loop of a hopper feeding back the component it just pulled out. Same shape as the wheel-slot
 * guard MOD-179 put on the water mill.
 *
 * <p>Sync channels: 0 energy, 1 capacity, 2 progress, 3 maxProgress, 4 {@link RepairStatus} code.
 */
public class ComponentRepairBenchBlockEntity extends MachineBlockEntity
		implements MenuProvider, Overclockable {

	/** The rotor/wheel being repaired. Stays in place across the whole operation. */
	public static final int TARGET_SLOT = 0;
	/** The grade's repair material; exactly one item is consumed per completed repair. */
	public static final int MATERIAL_SLOT = 1;

	private static final int SLOT_COUNT = 2;

	/** Eight-wide data — see the class javadoc. Hides {@link MachineBlockEntity#DATA_COUNT} (MOD-235). */
	public static final int DATA_COUNT = 8;
	/** Channel carrying the {@link RepairStatus} code the screen turns into its status line. */
	public static final int STATUS_CHANNEL = 4;
	/** EU one repair of the loaded grade costs; 0 with nothing loaded. */
	public static final int COST_CHANNEL = 5;
	/** Percent of the original ceiling one repair burns. */
	public static final int DECAY_CHANNEL = 6;
	/** The configured repair allowance. */
	public static final int MAX_REPAIRS_CHANNEL = 7;

	/**
	 * Balance figures the screen prints, published from the SERVER rather than read from the client's
	 * own {@link Config}.
	 *
	 * <p>{@link Config} is loaded per side and never synced (see {@code docs/SERVER_CONFIG.md}), so a
	 * tooltip that read these locally would quietly print the client's numbers on a dedicated server
	 * whose operator retuned the balance — the exact trap MOD-356 fixed for the generators' EU/t
	 * readout, and the reason {@code MachineScreen}'s overclock tooltip shows ratios instead of
	 * absolutes. Recomputed every tick, so a config reload reaches an open screen.
	 *
	 * <p>All three stay far inside the 16-bit range a {@code DataSlot} actually transmits (the shipped
	 * ceiling is 18 000); an operator who configures a cost above 32 767 would see a wrapped number in
	 * the tooltip while the machine itself keeps charging the real price.
	 */
	private int syncedCost;
	private int syncedDecayPercent;
	private int syncedMaxRepairs;

	/**
	 * Current status. Transient: recomputed every server tick and delivered to an open screen through
	 * the menu's {@link ContainerData}, so it is never serialised and never read off a client block
	 * entity.
	 */
	private RepairStatus status = RepairStatus.NO_TARGET;

	public ComponentRepairBenchBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.COMPONENT_REPAIR_BENCH_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
		this.maxProgress = Config.scaledDuration(defaultDuration());
	}

	/** The bench's own tariff — four times the shared machine rate, like the alloy smelter (MOD-384). */
	@Override
	public int baseEuPerTick() {
		return Config.repairBenchEuPerTick;
	}

	/**
	 * The item one repair of {@code tier} consumes. Deliberately an exhaustive switch over
	 * {@link ComponentTier}: a grade added later stops compiling here instead of silently becoming
	 * unrepairable, which is exactly how a new tier would otherwise slip past this machine.
	 *
	 * <p>Rotor and wheel of a grade share a material — the grade, not the family, is what the repair is
	 * priced on.
	 *
	 * <p>{@code public} so the screen can name the required material in its ghost hint and its arrow
	 * tooltip. Those hints exist because the bench is otherwise unteachable: two grey slots and an arrow
	 * say nothing about what goes where, and a second answer hard-coded client-side would be the usual
	 * way for the picture and the rule to drift apart.
	 */
	public static Item materialFor(ComponentTier tier) {
		return switch (tier) {
			case WINDMILL_ROTOR, WATER_MILL_WHEEL -> ModContent.IRON_PLATE.get();
			case WINDMILL_ROTOR_REINFORCED, WATER_MILL_WHEEL_REINFORCED -> ModContent.TEMPERED_IRON_PLATE.get();
			case WINDMILL_ROTOR_ADVANCED, WATER_MILL_WHEEL_ADVANCED -> ModContent.ELECTRONIC_CIRCUIT.get();
		};
	}

	/** Fallback operation length shown while the bench is empty, so the bar is never a stale 0. */
	private static int defaultDuration() {
		return Math.max(1, Config.repairBenchTier1EuCost / Math.max(1, Config.repairBenchEuPerTick));
	}

	/**
	 * Whether {@code stack} is a component this bench could still do something for: a recognised grade,
	 * actually worn, and not yet at its repair allowance. Drives both the automation guards and the
	 * freeze-or-reset decision.
	 */
	/** Repairs a component gets before its ceiling would hit zero — a pure function of the step. */
	private static int allowance() {
		return ComponentRepair.maxRepairs(Config.repairBenchMaxDamageDecayPercent);
	}

	private static boolean repairable(ItemStack stack) {
		ComponentTier tier = AbstractGeneratorBlockEntity.tierOf(stack, null);
		return tier != null && stack.isDamaged()
				&& ComponentRepair.canRepair(DurableComponentItem.repairs(stack), allowance());
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		int euPerTick = effectiveEuPerTick(baseEuPerTick());
		ItemStack target = items.get(TARGET_SLOT);
		ComponentTier tier = AbstractGeneratorBlockEntity.tierOf(target, null);
		int repairsDone = DurableComponentItem.repairs(target);
		boolean hasMaterial = tier != null && items.get(MATERIAL_SLOT).is(materialFor(tier));

		status = RepairStatus.resolve(tier != null, target.isDamaged(), repairsDone,
				allowance(), hasMaterial);
		// Publish what the screen prints (see the fields' javadoc: the client must not read Config).
		syncedCost = tier != null ? tier.repairEuCost() : 0;
		syncedDecayPercent = Config.repairBenchMaxDamageDecayPercent;
		syncedMaxRepairs = allowance();
		// Duration follows the grade in the slot, recomputed every tick. That also closes the swap
		// exploit: dropping a dearer component in near the end of a cheaper job raises maxProgress out
		// from under the accumulated progress instead of completing instantly.
		int baseDuration = tier != null
				? Math.max(1, tier.repairEuCost() / Math.max(1, baseEuPerTick()))
				: defaultDuration();
		this.maxProgress = effectiveDuration(baseDuration);

		boolean canWork = status.canWork() && energy.getAmount() >= euPerTick;
		updateLit(canWork);

		if (canWork) {
			energy.drainInternal(euPerTick);
			progress++;
			if (progress >= maxProgress) {
				progress = 0;
				items.get(MATERIAL_SLOT).shrink(1);
				applyRepair(target, tier, repairsDone);
				creditUsefulWork(level, (long) euPerTick * maxProgress); // MOD-133: completed op → XP
				// The repaired stack's components changed in place; without this the open screen and the
				// item in the slot keep drawing the old durability bar until something else dirties the
				// block entity.
				syncBlockEntityToClient();
			}
			setChanged();
		} else if (progress != 0 && !status.hasRepairableTarget()) {
			// The job itself is gone (component pulled, swapped for an undamaged or spent one) → start
			// over. A missing plate or a flat buffer leaves progress FROZEN and it resumes (R-NRG-10).
			progress = 0;
			setChanged();
		}
		return canWork ? 0 : IDLE_SLEEP_TICKS;
	}

	/**
	 * Apply one completed repair to {@code target}: wear cleared, ceiling lowered, counter advanced.
	 *
	 * <p>The ceiling is derived from the GRADE's registered durability and the new repair count, never
	 * from the stack's current ceiling — see {@link ComponentRepair} for why compounding would be both
	 * the wrong number and a rounding trap. {@code MAX_DAMAGE} is written before the damage value so the
	 * clamp inside {@code setDamageValue} already sees the new ceiling.
	 */
	private static void applyRepair(ItemStack target, ComponentTier tier, int repairsDone) {
		int repairs = repairsDone + 1;
		target.set(DataComponents.MAX_DAMAGE, ComponentRepair.maxDamageAfter(
				tier.maxDamage(), Config.repairBenchMaxDamageDecayPercent, repairs));
		target.setDamageValue(0);
		DurableComponentItem.setRepairs(target, repairs);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			// Any grade of either family, repairable or not: the player must be able to insert a spent
			// component and have the screen explain why it is refused (that is an acceptance criterion,
			// not a convenience). Automation is held to the stricter test below.
			case TARGET_SLOT -> stack.is(ModTags.Items.WINDMILL_ROTORS)
					|| stack.is(ModTags.Items.WATER_MILL_WHEELS);
			// Any grade's material. Which one is actually required depends on the component currently in
			// the target slot, so that check belongs in the tick (and its answer in the status line) —
			// gating it here would reject a plate the player is staging before inserting the rotor.
			case MATERIAL_SLOT -> isRepairMaterial(stack);
			default -> false;
		};
	}

	/** Whether {@code stack} is the repair material of ANY grade — derived, so a new grade follows. */
	private static boolean isRepairMaterial(ItemStack stack) {
		for (ComponentTier tier : ComponentTier.values()) {
			if (stack.is(materialFor(tier))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Automation may only insert a component the bench can actually work on. Without this a hopper pair
	 * would cycle for ever: {@link #isOutputSlot} hands the finished component out, and an unrestricted
	 * insert would push it straight back in. Manual placement is unaffected (see {@link #canPlaceItem}).
	 */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		if (!super.canPlaceItemThroughFace(slot, stack, side)) {
			return false;
		}
		return slot != TARGET_SLOT || repairable(stack);
	}

	/**
	 * The target slot becomes extractable exactly when the bench is finished with what is in it —
	 * repaired, or spent beyond its allowance. Mid-repair the component is not an output and automation
	 * cannot take it; the material slot never is.
	 */
	@Override
	protected boolean isOutputSlot(int slot) {
		if (slot != TARGET_SLOT) {
			return false;
		}
		ItemStack target = items.get(TARGET_SLOT);
		return !target.isEmpty() && !repairable(target);
	}

	/** Consumer: every face accepts energy except the inert FACING front (R-NRG-03). */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	/** Swapping the component restarts the operation (TC-MACH-001-FUN04 semantics). */
	@Override
	protected boolean resetProgressOnInputChange() {
		return true;
	}

	/**
	 * Eight-wide data: the shared base 0..3, the {@link RepairStatus} code on channel 4, and the three
	 * balance figures the tooltip prints on 5..7.
	 */
	private final ContainerData benchData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case STATUS_CHANNEL -> status.code();
				case COST_CHANNEL -> syncedCost;
				case DECAY_CHANNEL -> syncedDecayPercent;
				case MAX_REPAIRS_CHANNEL -> syncedMaxRepairs;
				default -> ComponentRepairBenchBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case STATUS_CHANNEL -> status = RepairStatus.byCode(value);
				case COST_CHANNEL -> syncedCost = value;
				case DECAY_CHANNEL -> syncedDecayPercent = value;
				case MAX_REPAIRS_CHANNEL -> syncedMaxRepairs = value;
				default -> ComponentRepairBenchBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return benchData;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.component_repair_bench");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new ComponentRepairBenchMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
