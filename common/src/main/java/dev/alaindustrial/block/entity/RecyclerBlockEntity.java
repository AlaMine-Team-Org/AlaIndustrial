package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.waste.BladeTier;
import dev.alaindustrial.core.waste.SlagGrade;
import dev.alaindustrial.core.waste.WasteClassifier;
import dev.alaindustrial.core.waste.WasteFraction;
import dev.alaindustrial.block.RecyclerBlock;
import dev.alaindustrial.menu.RecyclerMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
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
 * The Recycler (MOD-145): it does not convert one item into another, it runs a BATCH.
 *
 * <p>Every item fed in carries a slag mass and belongs to a fraction ({@link WasteClassifier}). The
 * machine grinds them one at a time, accumulating mass, and casts a briquette when the batch reaches
 * {@link Config#recyclerBatchMass}. Which briquette it casts is decided by how VARIED the batch was
 * ({@link SlagGrade}) — a stack of one thing pays ballast, a proper mix pays the grade the later matter
 * chain will want. There is no dice roll anywhere in the machine.
 *
 * <p>Two upkeep gates, both deliberate (MOD-145 design round 3):
 * <ul>
 *   <li><b>Blades.</b> Without a blade in {@link #BLADE_SLOT} nothing runs at all, the way a wind mill
 *       without a rotor produces nothing. The grade sets speed, the penalty on scrap metal and how much
 *       ash a batch leaves, so a better set means LESS servicing, not merely faster work.</li>
 *   <li><b>Ash.</b> Every batch drops ash into {@link #ASH_SLOT}. A nearly full bin slows the machine
 *       down, a full one stops it — and a hopper under the machine empties it, so the upkeep is
 *       automatable rather than a walk to the base.</li>
 * </ul>
 */
public class RecyclerBlockEntity extends MachineBlockEntity implements MenuProvider {
	public static final int INPUT_SLOT = 0;
	public static final int SLAG_SLOT = 1;
	public static final int ASH_SLOT = 2;
	public static final int BLADE_SLOT = 3;
	/** Machine inventory size — goes into {@code super(...)} AND sizes the menu's client stub (MOD-439). */
	public static final int SLOT_COUNT = 4;

	/** Base channels plus batch mass, three fractions, ash count and the status line. */
	public static final int DATA_COUNT = MachineBlockEntity.DATA_COUNT + 6;
	public static final int DATA_BATCH_MASS = 4;
	public static final int DATA_MINERAL = 5;
	public static final int DATA_METAL = 6;
	public static final int DATA_COMBUSTIBLE = 7;
	public static final int DATA_ASH = 8;
	public static final int DATA_STATUS = 9;

	/** Above this share of a full ash bin the machine drags before it stops entirely. */
	private static final float ASH_SLOWDOWN_SHARE = 0.75f;
	private static final float ASH_SLOWDOWN_FACTOR = 1.5f;

	private final ProcessingCycle cycle = new ProcessingCycle(this);

	private int massMineral;
	private int massMetal;
	private int massCombustible;
	private int massOther;
	private RecyclerStatus status = RecyclerStatus.READY;

	public RecyclerBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.RECYCLER_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.machineBuffer, EnergyTier.LV.maxVoltage(), 0L);
		this.maxProgress = Config.recyclerDuration;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		ItemStack input = items.get(INPUT_SLOT);
		BladeTier blade = BladeTier.of(items.get(BLADE_SLOT));
		WasteClassifier.WasteProfile profile = WasteClassifier.classify(input);

		int baseDuration = blade == null
				? Config.recyclerDuration
				: Math.round(blade.durationFor(Config.recyclerDuration, profile.fraction()) * ashDrag());
		ProcessingCycle.Job job = cycle.job(Config.recyclerEuPerTick, baseDuration);

		boolean hasWork = blade != null && !input.isEmpty() && profile.mass() > 0;
		boolean ashRoom = ashRoom(blade);
		boolean slagRoom = canCastSomewhere(profile);
		boolean powered = energy.getAmount() >= job.euPerTick();
		boolean canWork = hasWork && ashRoom && slagRoom && powered;

		status = resolveStatus(blade, input, ashRoom, slagRoom, powered);

		return job.canWork(canWork)
				// Progress is bought for THIS item: swap the input and the accumulated ticks are void.
				.readyExceptEnergy(hasWork && ashRoom && slagRoom)
				.jobIntact(hasWork)
				.run(level, () -> commit(input, profile, blade, ashRoom, slagRoom));
	}

	/**
	 * One item ground: its mass joins the batch, the blades wear, and a full batch casts a briquette.
	 *
	 * <p>The gates are re-read here because the cycle can reach a completion with {@code canWork} already
	 * false: the Resilient Cycle skill (MOD-483) lets an operation past halfway finish on the machine's own
	 * charge, and it waives the demand for a SUPPLY, not this machine's own conditions. Without the guard a
	 * blade pulled out at half progress would grind the item for free, and a bin that filled meanwhile
	 * would be grown past its stack limit by {@code addOutput}, which does not re-check.
	 */
	private void commit(ItemStack input, WasteClassifier.WasteProfile profile, BladeTier blade,
			boolean ashRoom, boolean slagRoom) {
		if (blade == null || input.isEmpty() || profile.mass() <= 0 || !ashRoom || !slagRoom) {
			return;
		}
		input.shrink(1);
		addMass(profile);
		wearBlade();
		if (batchMass() >= Config.recyclerBatchMass) {
			castBriquette(blade);
		}
		syncLamps();
	}

	/** Fractions the batch has collected — the number of lamps burning on the front panel. */
	private int activeFractions() {
		return (massMineral > 0 ? 1 : 0) + (massMetal > 0 ? 1 : 0) + (massCombustible > 0 ? 1 : 0);
	}

	/**
	 * Publish the lamp count to the blockstate, and only when it actually changed: every write here is a
	 * block update to the neighbours, and a machine grinding a stack would otherwise send one per item.
	 */
	private void syncLamps() {
		if (level == null || level.isClientSide()) {
			return;
		}
		BlockState state = getBlockState();
		int lamps = activeFractions();
		if (state.hasProperty(RecyclerBlock.LAMPS) && state.getValue(RecyclerBlock.LAMPS) != lamps) {
			level.setBlock(worldPosition, state.setValue(RecyclerBlock.LAMPS, lamps),
					net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
		}
	}

	private void addMass(WasteClassifier.WasteProfile profile) {
		switch (profile.fraction()) {
			case MINERAL -> massMineral += profile.mass();
			case METAL -> massMetal += profile.mass();
			case COMBUSTIBLE -> massCombustible += profile.mass();
			case OTHER -> massOther += profile.mass();
		}
	}

	/** Casts the briquette the batch earned, drops the ash and clears the counters. */
	private void castBriquette(BladeTier blade) {
		SlagGrade grade = SlagGrade.of(massMineral, massMetal, massCombustible);
		addOutput(SLAG_SLOT, new ItemStack(slagItem(grade)));
		int ash = ashPerBatch(blade);
		if (ash > 0) {
			addOutput(ASH_SLOT, new ItemStack(ModContent.ASH.get(), ash));
		}
		massMineral = 0;
		massMetal = 0;
		massCombustible = 0;
		massOther = 0;
	}

	private static net.minecraft.world.item.Item slagItem(SlagGrade grade) {
		return switch (grade) {
			case POOR -> ModContent.SLAG_POOR.get();
			case COMMON -> ModContent.SLAG.get();
			case RICH -> ModContent.SLAG_RICH.get();
		};
	}

	/** One item ground is one point of blade wear; a spent blade simply disappears, as a rotor does. */
	private void wearBlade() {
		ItemStack blade = items.get(BLADE_SLOT);
		if (blade.isEmpty() || !blade.isDamageableItem()) {
			return;
		}
		int damage = blade.getDamageValue() + 1;
		if (damage >= blade.getMaxDamage()) {
			items.set(BLADE_SLOT, ItemStack.EMPTY);
		} else {
			blade.setDamageValue(damage);
		}
	}

	/** Total mass held by the current batch, graded fractions and unknown junk alike. */
	public int batchMass() {
		return massMineral + massMetal + massCombustible + massOther;
	}

	/** Slowdown factor from a nearly full ash bin: 1.0 while there is room to spare. */
	private float ashDrag() {
		ItemStack ash = items.get(ASH_SLOT);
		if (ash.isEmpty()) {
			return 1.0f;
		}
		float share = (float) ash.getCount() / ash.getMaxStackSize();
		return share >= ASH_SLOWDOWN_SHARE ? ASH_SLOWDOWN_FACTOR : 1.0f;
	}

	/**
	 * Whether the ash bin can take one more batch worth of ash — measured against the ash THESE blades
	 * leave, not against the smallest figure in the table. Reserving the diamond set's two while an iron
	 * set was fitted let a bin holding 62 pass the gate and then be grown to 66 by {@link #addOutput},
	 * which does not re-check its own precondition.
	 */
	private boolean ashRoom(BladeTier blade) {
		ItemStack ash = items.get(ASH_SLOT);
		return ash.isEmpty() || ash.getCount() + ashPerBatch(blade) <= ash.getMaxStackSize();
	}

	/**
	 * Whether the briquette THIS operation would cast has somewhere to go.
	 *
	 * <p>The question is about the cast this very item triggers, not about the batch as it stands. An
	 * operation that leaves the batch below the threshold casts nothing, so a briquette of another grade
	 * lying in the slot is no reason to stop grinding — asking about the current grade instead stopped a
	 * machine dead with one leftover poor briquette in an otherwise empty slot, the moment a second
	 * fraction turned the batch COMMON. When the item DOES fill the batch, the grade asked about is the
	 * one {@link #castBriquette} will compute a moment later, so the check and the write cannot disagree.
	 */
	private boolean canCastSomewhere(WasteClassifier.WasteProfile profile) {
		if (batchMass() + profile.mass() < Config.recyclerBatchMass) {
			return true;
		}
		return canOutput(SLAG_SLOT, new ItemStack(slagItem(gradeAfter(profile))));
	}

	/** The grade the batch would earn once this item's mass has joined it. */
	private SlagGrade gradeAfter(WasteClassifier.WasteProfile profile) {
		int mass = profile.mass();
		return SlagGrade.of(
				massMineral + (profile.fraction() == WasteFraction.MINERAL ? mass : 0),
				massMetal + (profile.fraction() == WasteFraction.METAL ? mass : 0),
				massCombustible + (profile.fraction() == WasteFraction.COMBUSTIBLE ? mass : 0));
	}

	/** Ash one batch leaves with these blades; a missing set is priced as the dirtiest one. */
	private static int ashPerBatch(BladeTier blade) {
		return blade == null ? BladeTier.IRON.ashPerBatch() : blade.ashPerBatch();
	}

	private RecyclerStatus resolveStatus(BladeTier blade, ItemStack input, boolean ashRoom,
			boolean slagRoom, boolean powered) {
		if (blade == null) {
			return RecyclerStatus.NO_BLADES;
		}
		if (!ashRoom) {
			return RecyclerStatus.ASH_FULL;
		}
		if (!slagRoom) {
			return RecyclerStatus.OUTPUT_FULL;
		}
		if (input.isEmpty()) {
			return RecyclerStatus.NO_INPUT;
		}
		if (!powered) {
			return RecyclerStatus.NO_ENERGY;
		}
		return RecyclerStatus.READY;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
			// Greedy on purpose (owner decision): the machine eats anything. The one exception is a spare
			// blade — automation feeding the input must not grind the machine's own replacement parts.
			case INPUT_SLOT -> !stack.is(ModTags.Items.RECYCLER_BLADES);
			case BLADE_SLOT -> stack.is(ModTags.Items.RECYCLER_BLADES);
			default -> false;
		};
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == SLAG_SLOT || slot == ASH_SLOT;
	}

	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	@Override
	protected boolean resetProgressOnInputChange() {
		return true;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("MassMineral", massMineral);
		output.putInt("MassMetal", massMetal);
		output.putInt("MassCombustible", massCombustible);
		output.putInt("MassOther", massOther);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		massMineral = input.getIntOr("MassMineral", 0);
		massMetal = input.getIntOr("MassMetal", 0);
		massCombustible = input.getIntOr("MassCombustible", 0);
		massOther = input.getIntOr("MassOther", 0);
	}

	/** Ten-wide data: the four base channels plus the batch, its three fractions, ash and status. */
	private final ContainerData recyclerData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_BATCH_MASS -> batchMass();
				case DATA_MINERAL -> massMineral;
				case DATA_METAL -> massMetal;
				case DATA_COMBUSTIBLE -> massCombustible;
				case DATA_ASH -> items.get(ASH_SLOT).getCount();
				case DATA_STATUS -> status.ordinal();
				default -> RecyclerBlockEntity.super.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case DATA_MINERAL -> massMineral = value;
				case DATA_METAL -> massMetal = value;
				case DATA_COMBUSTIBLE -> massCombustible = value;
				case DATA_STATUS -> status = RecyclerStatus.byOrdinal(value);
				default -> RecyclerBlockEntity.super.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return recyclerData;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.recycler");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new RecyclerMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
