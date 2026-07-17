package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.EnergyRole;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.core.EnergyTransactions;
import dev.alaindustrial.core.FluidAmounts;
import dev.alaindustrial.core.FluidHolder;
import dev.alaindustrial.core.FluidLookup;
import dev.alaindustrial.core.FluidMover;
import dev.alaindustrial.core.FluidPort;
import dev.alaindustrial.core.FluidPortHost;
import dev.alaindustrial.core.FluidTank;
import dev.alaindustrial.item.ItemFluidBridge;
import dev.alaindustrial.menu.PumpMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * LV electric pump: an EU consumer that draws fluid from the world and pushes it on. It is one of the
 * most energy-hungry machines in the mod ({@link Config#pumpEuPerBucket} EU per bucket moved).
 *
 * <p><b>Fluid intake is single-variant.</b> The internal {@link FluidTank} accepts any whitelisted
 * fluid (currently lava + water) but the tank is single-variant — once it holds lava it refuses water
 * (and vice-versa) until it empties. This is enforced by the tank core ({@link FluidTank#insert}), so
 * mixing is impossible by construction.
 *
 * <p><b>Acquisition is FACING-only.</b> The pump draws fluid only from the block directly in front of
 * it (its {@link HorizontalMachineBlock#FACING} face): either a world fluid source block (which it
 * <b>irrevocably drains</b> — the source block is replaced with air) or an adjacent extractable
 * {@link FluidPort} (e.g. another tank). Every other face accepts EU and may receive the pumped fluid.
 *
 * <p><b>Bucket feed and drain.</b> The pump GUI has four bucket slots in a 2×2 grid:
 * the top row fills the tank (a lava/water bucket is emptied in, the empty bucket drops out) and the
 * bottom row drains it (an empty bucket is filled from the tank, the full bucket drops out). Both are
 * free of EU cost — manual handling, not pumping.
 *
 * <p><b>GUI.</b> Four slots and a {@link PumpMenu} screen showing the tank level and energy buffer.
 * The fluid level/type are projected through a 7-wide {@link ContainerData} (permille + denominator +
 * fluid-type id) so the client can render the right colour without a custom packet.
 *
 * <p>Draws EU from the energy network like other machines: it exposes a neutral
 * {@link dev.alaindustrial.core.EnergyPort} with {@code maxInsert > 0}, so the
 * {@link dev.alaindustrial.core.EnergyNetwork} discovers it as a consumer automatically.
 *
 * <p><b>MOD-028 multiloader migration.</b> Lives in {@code common}: the loader-specific fluid APIs are
 * replaced by the neutral {@link FluidTank}/{@link FluidPort}/{@link FluidLookup}/
 * {@link EnergyTransactions}. Each loader supplies its own adapter
 * ({@code TankAsFluidStorage}/{@code TankAsResourceHandler}) — see {@link FluidPort} class doc.
 */
public class PumpBlockEntity extends MachineBlockEntity implements FluidPortHost, MenuProvider {
	/** Top row — fill the tank: a full lava/water bucket placed here is emptied into the tank. */
	public static final int FILL_INPUT_SLOT = 0;
	/** Top row — fill the tank: the empty bucket drops here after filling. */
	public static final int FILL_OUTPUT_SLOT = 1;
	/** Bottom row — drain the tank: an empty bucket placed here is filled from the tank. */
	public static final int DRAIN_INPUT_SLOT = 2;
	/** Bottom row — drain the tank: the filled bucket drops here after draining. */
	public static final int DRAIN_OUTPUT_SLOT = 3;

	/** Internal fluid tank: 10 buckets, extractable from any side (so neighbours may pull from it). */
	public static final long TANK_CAPACITY = FluidAmounts.BUCKET * 10;

	/**
	 * Sentinel for an empty tank on the sync channel (registry-id slot): the vanilla {@code IdMap}
	 * default. MOD-099: the tank now holds any fluid, so channel 6 carries the fluid's
	 * {@link BuiltInRegistries#FLUID} registry id (resolved back to a fluid client-side) instead of
	 * the old fixed 0/1/2 = none/lava/water encoding.
	 */
	public static final int FLUID_ID_NONE = net.minecraft.core.IdMap.DEFAULT;

	public final FluidTank fluidTank = new FluidTank(TANK_CAPACITY,
			PumpBlockEntity::isPumpableFluid,
			fluid -> true,
			this::setChanged);

	/** Ticks remaining before the pump is allowed to run a BFS source scan again. */
	private int scanCooldown = 0;

	public PumpBlockEntity(BlockPos pos, BlockState state) {
		// EU consumer: maxInsert = tier voltage (so the network sees a consumer), maxExtract = 0.
		// Buffer (Config.pumpBuffer) must be >= pumpEuPerBucket so the pump can actually accumulate a
		// full bucket's cost — machineBuffer (800) is below the 1000 EU/bucket threshold and would leave
		// the pump permanently starved. Four bucket slots: fill-in/out + drain-in/out.
		super(ModContent.PUMP_BE.get(), pos, state, EnergyTier.LV, 4,
				Config.pumpBuffer, EnergyTier.LV.maxVoltage(), 0L);
	}

	/**
	 * MOD-099: the pump accepts <em>any</em> non-empty fluid, so a Vacuum Capsule (or another mod's
	 * container) can push whatever fluid it carries into the tank — not just lava/water. The tank stays
	 * single-variant (enforced by {@link FluidTank#insert}), so mixing is still impossible by
	 * construction.
	 */
	private static boolean isPumpableFluid(FluidHolder fluid) {
		return !fluid.isEmpty();
	}

	/**
	 * EU consumer: every face except {@code FACING} accepts energy (R-NRG-03), none emits. The front
	 * face is the fluid-intake side and is energy-inert, matching the {@code HorizontalMachineBlock}
	 * cable rule ({@code FACING} draws no cable arm). Fluid intake is a separate subsystem and keeps
	 * reading {@code FACING} directly ({@link #acquireFluid}), so this only changes the energy/cable
	 * contract on that one face — it does not change which way the pump draws fluid from.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return facingAwareRole(worldFace, EnergyRole.IN);
	}

	/** Every face exposes the same single tank — the pump has no per-face fluid restriction. */
	@Override
	public FluidPort fluidPort(Direction side) {
		return fluidTank;
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		boolean worked = false;
		int euPerBucket = Math.max(1, Config.pumpEuPerBucket);

		// 1) Container handling (no EU cost — manual refill/drain, not pumping): top row empties a full
		//    container INTO the tank; bottom row fills an empty container FROM the tank. Buckets, our
		//    capsule and foreign containers all ride the same item fluid capability (MOD-107).
		worked |= emptyContainerIntoTank();
		worked |= fillContainerFromTank();

		// Update BFS scan cooldown
		if (scanCooldown > 0) {
			scanCooldown--;
		}

		// 2) Acquire fluid from the FACING block if we have power and tank room.
		BlockPos acquiredFrom = null;
		if (energy.amount >= euPerBucket && fluidTank.amount + FluidAmounts.BUCKET <= TANK_CAPACITY) {
			if (scanCooldown <= 0) {
				acquiredFrom = acquireFluid(level, pos, state);
				if (acquiredFrom != null) {
					energy.amount -= euPerBucket;
					worked = true;
					scanCooldown = 20; // 1 second cooldown on success to balance speed and reduce BFS frequency
				} else {
					scanCooldown = 20; // 1 second cooldown on failure to avoid ticking BFS
				}
			}
		}

		// 3) Push tank fluid into adjacent insertable fluid ports (every face except the FACING intake, so
		//    we never push straight back into the source we just drained). Skip the neighbour we just
		//    pulled from (same-tick push-back guard, MOD-032 D8 fix).
		if (fluidTank.amount > 0) {
			worked |= pushFluid(level, pos, state, acquiredFrom);
		}

		// Project tank level/type onto progress so the screen can render it (solar-panel-style channel
		// projection is in pumpData below; progress/maxProgress keep the base contract meaningful too).
		this.progress = (int) Math.min(Integer.MAX_VALUE, fluidTank.amount);
		this.maxProgress = (int) Math.min(Integer.MAX_VALUE, TANK_CAPACITY);

		updateLit(worked);
		if (worked) {
			setChanged();
		}
		// The pump reacts to world fluid state (sources / adjacent tanks) that change without a
		// block-entity wake event, so it stays awake every tick rather than sleep (R-29).
		return 0;
	}

	/**
	 * Empty a fluid container from {@link #FILL_INPUT_SLOT} into the tank; the emptied container drops into
	 * {@link #FILL_OUTPUT_SLOT}. No EU cost (manual handling).
	 *
	 * <p>MOD-107: this runs through {@link ItemFluidBridge}, i.e. the loader's item fluid capability, rather
	 * than a hardcoded lava/water bucket pair. Both loaders publish that capability for vanilla buckets, for
	 * our own Vacuum Capsule, and every other mod does for its own containers — so one mechanism now serves
	 * all three, and the tank's any-fluid rule (MOD-099) finally has a way to be fed by hand.
	 */
	private boolean emptyContainerIntoTank() {
		return ItemFluidBridge.get()
				.drainSlotIntoTank(this, FILL_INPUT_SLOT, FILL_OUTPUT_SLOT, fluidTank, FluidAmounts.BUCKET) > 0;
	}

	/**
	 * Fill an empty container from {@link #DRAIN_INPUT_SLOT} with the tank's fluid; the filled container
	 * drops into {@link #DRAIN_OUTPUT_SLOT}. No EU cost (manual handling). The tank is single-variant, so
	 * the filled container always matches its content.
	 *
	 * <p>MOD-107: goes through {@link ItemFluidBridge} — see {@link #emptyContainerIntoTank}. This is also
	 * what lets a modded fluid leave the tank by hand: a vanilla bucket only exists for lava and water, but
	 * a capsule (or a foreign cell) takes any fluid the tank holds.
	 */
	private boolean fillContainerFromTank() {
		return ItemFluidBridge.get()
				.fillSlotFromTank(this, DRAIN_INPUT_SLOT, DRAIN_OUTPUT_SLOT, fluidTank, FluidAmounts.BUCKET) > 0;
	}

	/**
	 * Try to put one bucket of fluid into the tank, drawn from the world (searching connected flowing blocks
	 * via BFS for the closest source block) or from an adjacent extractable fluid port.
	 * Returns the {@link BlockPos} that supplied the fluid (for same-tick push exclusion), or {@code null}.
	 *
	 * @param level the level the pump is in
	 * @param pos   the pump block position
	 * @param state the pump block state
	 * @return the block position that supplied the fluid, or null
	 */
	private BlockPos acquireFluid(Level level, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(HorizontalMachineBlock.FACING);
		BlockPos np = pos.relative(facing);

		// 1) World fluid search. Start BFS from the block directly in front of the pump. MOD-099: the
		//    pump draws any world fluid source (not just lava/water), so an isPumpableFluid gate is no
		//    longer fluid-specific — it just rejects an empty fluid state.
		FluidState startState = level.getFluidState(np);
		if (!startState.isEmpty()) {
			Fluid startFluid = startState.getType();
			Fluid sourceFluid = (startFluid instanceof FlowingFluid flowing) ? flowing.getSource() : startFluid;
			FluidHolder holder = FluidHolder.of(sourceFluid);
			if (isPumpableFluid(holder)) {
				// Dry-run type match check (prevent BFS if tank already holds a different fluid)
				if (fluidTank.fluid.isEmpty() || fluidTank.fluid.equals(holder)) {
					// Perform BFS to find the closest source block
					BlockPos sourcePos = findClosestSource(level, pos, np, sourceFluid);
					if (sourcePos != null) {
						boolean[] acquired = {false};
						EnergyTransactions.get().runCommitting(txn -> {
							long inserted = fluidTank.insert(holder, FluidAmounts.BUCKET, txn);
							acquired[0] = inserted >= FluidAmounts.BUCKET;
						});
						if (acquired[0]) {
							// Irrevocably drain the source block
							level.setBlockAndUpdate(sourcePos, Blocks.AIR.defaultBlockState());
							return sourcePos;
						}
					}
				}
			}
		}

		// 2) Adjacent extractable fluid port in front of the pump. MOD-099: rather than guessing a
		//    fixed lava/water candidate set to prime an empty tank, read what the neighbour actually
		//    holds (src.fluid()); that lets the pump pull any fluid a donor tank exposes. When the tank
		//    already holds a variant, keep pumping that same one (single-variant tank).
		FluidPort src = FluidLookup.get().find(level, np, facing.getOpposite());
		if (src != null && src.supportsExtraction()) {
			FluidHolder current = fluidTank.fluid;
			FluidHolder candidate = current.isEmpty() ? src.fluid() : current;
			if (!candidate.isEmpty()) {
				long[] moved = {0};
				EnergyTransactions.get().runCommitting(
						txn -> moved[0] = FluidMover.move(src, fluidTank, candidate, FluidAmounts.BUCKET, txn));
				if (moved[0] > 0) {
					return np;
				}
			}
		}
		return null;
	}

	/**
	 * Finds the closest source block of targetSourceFluid connected to startPos using Breadth-First Search (BFS).
	 * Restricts search to loaded chunks, Manhattan distance <= 32, and visits at most 512 blocks.
	 *
	 * @param level              the level to search in
	 * @param pumpPos            the position of the pump block (used for distance constraint)
	 * @param startPos           the initial block position in front of the pump to start searching from
	 * @param targetSourceFluid the source representation of the fluid we are looking for
	 * @return the BlockPos of the closest source block, or null if none found
	 */
	private static BlockPos findClosestSource(Level level, BlockPos pumpPos, BlockPos startPos, Fluid targetSourceFluid) {
		java.util.Queue<BlockPos> queue = new java.util.ArrayDeque<>();
		java.util.Set<BlockPos> visited = new java.util.HashSet<>();

		queue.add(startPos);
		visited.add(startPos);

		try {
			while (!queue.isEmpty()) {
				BlockPos current = queue.poll();
				FluidState currentState = level.getFluidState(current);
				if (currentState.isEmpty()) {
					continue;
				}

				Fluid currentFluid = currentState.getType();
				if (isSameFluid(currentFluid, targetSourceFluid)) {
					if (currentState.isSource()) {
						return current;
					}

					for (Direction dir : Direction.values()) {
						BlockPos next = current.relative(dir);
						if (!visited.contains(next)) {
							// Distance check: Manhattan distance to pumpPos must be <= 32 blocks
							if (next.distManhattan(pumpPos) <= 32) {
								// Limit max visited blocks to 512 to avoid lag spikes
								if (visited.size() < 512) {
									visited.add(next);
									queue.add(next);
								}
							}
						}
					}
				}
			}
		} catch (Exception exception) {
			// Catch any exceptions to prevent server crashes, returning null
			return null;
		}
		return null;
	}

	/**
	 * Helper to check if two fluids share the same source type.
	 *
	 * @param a first fluid to compare
	 * @param b second fluid to compare
	 * @return true if both fluids share the same source type
	 */
	private static boolean isSameFluid(Fluid a, Fluid b) {
		Fluid aSource = (a instanceof FlowingFluid flowing) ? flowing.getSource() : a;
		Fluid bSource = (b instanceof FlowingFluid flowing) ? flowing.getSource() : b;
		return aSource == bSource;
	}

	/** Push tank fluid into any adjacent insertable fluid port (except the FACING face and {@code excludePos}). */
	private boolean pushFluid(Level level, BlockPos pos, BlockState state, BlockPos excludePos) {
		boolean moved = false;
		Direction facing = state.getValue(HorizontalMachineBlock.FACING);
		FluidHolder current = fluidTank.fluid;
		if (current.isEmpty()) {
			return false;
		}
		for (Direction dir : Direction.values()) {
			if (fluidTank.amount <= 0) {
				break;
			}
			if (dir == facing) {
				continue; // never push back through the intake face
			}
			BlockPos np = pos.relative(dir);
			if (np.equals(excludePos)) {
				continue; // MOD-032: don't push straight back into the neighbour we just pulled from
			}
			FluidPort dst = FluidLookup.get().find(level, np, dir.getOpposite());
			if (dst == null || !dst.supportsInsertion()) {
				continue;
			}
			long amountToPush = fluidTank.amount;
			long[] pushed = {0};
			EnergyTransactions.get().runCommitting(
					txn -> pushed[0] = FluidMover.move(fluidTank, dst, current, amountToPush, txn));
			if (pushed[0] > 0) {
				moved = true;
			}
		}
		return moved;
	}

	/**
	 * Seven-wide sync bridge: base channels 0..3 (energy/capacity/progress/maxProgress) plus tank permille
	 * (4), permille denominator 1000 (5), and the fluid's registry id (6). Channels 4..6 are derived,
	 * server-authoritative projections of the tank — nothing writes them back.
	 *
	 * <p>MOD-099: channel 6 is the fluid's {@link BuiltInRegistries#FLUID} registry id
	 * ({@link IdMap#DEFAULT} = empty) so the client can resolve any fluid, not just lava/water.
	 *
	 * <p><b>Every channel must fit a signed 16-bit short.</b> {@code ClientboundContainerSetDataPacket}
	 * writes each value with {@code FriendlyByteBuf.writeShort} and reads it back with {@code readShort}
	 * (verified against the 26.2 bytecode), so a value outside {@link Short#MIN_VALUE}..{@link Short#MAX_VALUE}
	 * silently arrives truncated to its low 16 bits. This is why the tank level is projected as a permille
	 * (0..1000) rather than raw mB, and why the fluid's display <em>colour</em> is <b>not</b> sent here: a
	 * packed ARGB is a 32-bit value and always truncates (lava's {@code 0xFFFF0000} arrived as {@code 0},
	 * water's {@code 0xFF4040FF} as alpha-zero). {@code PumpScreen} derives the colour from channel 6
	 * instead — it is a pure function of the fluid type, so it needs no syncing at all.
	 */
	private final ContainerData pumpData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 4 -> fluidTank.amount <= 0 ? 0
						: Math.max(1, (int) Math.min(fluidTank.amount * 1000L / TANK_CAPACITY, 1000));
				case 5 -> 1000;
				case 6 -> fluidRegistryId(fluidTank.fluid);
				default -> PumpBlockEntity.this.dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			if (index != 4 && index != 5 && index != 6) {
				PumpBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return 7;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return pumpData;
	}

	/**
	 * Every pump slot holds fluid containers — a vanilla bucket, a Vacuum Capsule, or another mod's cell
	 * (MOD-107; previously a hardcoded lava/water bucket list). Fill and drain are not distinguished here
	 * (a capsule is the same item full or empty, so "is it full?" is a per-tick question for the exchange
	 * itself, not a slot rule); the tank's own state decides what actually moves.
	 *
	 * <p><b>Output slots must answer true too</b>, because this method states what a slot can physically
	 * hold — and the loader's item-transfer API asks it before letting the machine put the emptied container
	 * there. Answering false for the output slots made the bridge silently move nothing: the swap had
	 * nowhere to land, so the exchange rolled back and even vanilla buckets stopped working. Automation
	 * policy is a separate question, answered by {@link #canPlaceItemThroughFace}.
	 *
	 * <p>This does <b>not</b> gate a player's click either: vanilla {@code Slot} accepts any item unless a
	 * {@code mayPlace} override says otherwise, which is why a capsule could always be dropped into the slot
	 * — it just sat there doing nothing before this task.
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return ItemFluidBridge.get().isFluidContainer(stack);
	}

	/**
	 * Hoppers and pipes may feed only the two input slots. The base class equates this with
	 * {@link #canPlaceItem}, which MOD-107 had to widen to the output slots (see there) — so the pump states
	 * the automation rule explicitly instead, keeping the pre-MOD-107 contract: automation fills the inputs,
	 * the machine fills the outputs, and {@code canTakeItemThroughFace} still lets automation pull the
	 * finished containers back out.
	 */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return (slot == FILL_INPUT_SLOT || slot == DRAIN_INPUT_SLOT) && canPlaceItem(slot, stack);
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		// Hoppers/automation may pull the buckets that drop out, never the inputs.
		return slot == FILL_OUTPUT_SLOT || slot == DRAIN_OUTPUT_SLOT;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.pump");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new PumpMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("FluidTankMb", fluidTank.amount);
		// Persist which fluid the tank holds so a reload restores the right variant, not just the amount.
		// MOD-099: the key is now the fluid's full registry id (e.g. "minecraft:lava"), so any fluid
		// survives a reload — not only lava/water. holderFromKey still accepts the legacy bare
		// "lava"/"water" spellings for pre-MOD-099 saves.
		output.putString("FluidTankFluid", fluidKey(fluidTank.fluid));
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		// MOD-028: prefer the new mB-valued key; fall back to the legacy Fabric v0.1.0 droplet-valued
		// "FluidTank" key, converting ÷81 (81000 droplets/bucket ÷ 81 = 1000 mB/bucket, exact).
		long amount = input.getLongOr("FluidTankMb", 0L);
		if (amount == 0L) {
			amount = input.getLong("FluidTank")
						.map(dr -> dr / FluidAmounts.FABRIC_DROPLETS_PER_MB).orElse(0L);
		}
		fluidTank.amount = Math.max(0L, Math.min(TANK_CAPACITY, amount));
		// Restore the fluid variant from the persisted registry key. The very first pump revision was
		// lava-only with no key at all; a non-zero amount with an empty/unresolved key is therefore a
		// legacy lava-only save → treat it as lava rather than dropping the contents.
		String key = input.getStringOr("FluidTankFluid", "");
		FluidHolder restored = holderFromKey(key);
		fluidTank.fluid = fluidTank.amount > 0 && restored.isEmpty()
				? FluidHolder.of(Fluids.LAVA) : restored;
		if (fluidTank.amount == 0) {
			fluidTank.fluid = FluidHolder.EMPTY;
		}
	}

	/**
	 * Registry key of {@code fluid} for persistence (e.g. {@code "minecraft:lava"}). MOD-099: now stores
	 * the full namespaced id so any fluid survives a reload, not just lava/water. The legacy bare
	 * {@code "lava"}/{@code "water"} spellings (pre-MOD-099 saves) are still accepted by
	 * {@link #holderFromKey} for backwards compatibility.
	 */
	private static String fluidKey(FluidHolder fluid) {
		if (fluid.isEmpty()) {
			return "";
		}
		return BuiltInRegistries.FLUID.getKey(fluid.fluid()).toString();
	}

	private static FluidHolder holderFromKey(String key) {
		if (key == null || key.isEmpty()) {
			return FluidHolder.EMPTY;
		}
		// Legacy pre-MOD-099 spellings.
		if ("lava".equals(key)) {
			return FluidHolder.of(Fluids.LAVA);
		}
		if ("water".equals(key)) {
			return FluidHolder.of(Fluids.WATER);
		}
		Identifier id = Identifier.tryParse(key);
		if (id == null) {
			return FluidHolder.EMPTY;
		}
		Fluid resolved = BuiltInRegistries.FLUID.getValue(id);
		return resolved == null ? FluidHolder.EMPTY : FluidHolder.of(resolved);
	}

	/**
	 * The {@link BuiltInRegistries#FLUID} registry id of {@code fluid} ({@link IdMap#DEFAULT} when the
	 * tank is empty), for the client to resolve the fluid type over ContainerData channel 6.
	 *
	 * <p>Ids above {@link Short#MAX_VALUE} report as empty rather than as the id's truncated low 16 bits,
	 * which the channel's short encoding (see {@link #pumpData}) would otherwise resolve to an unrelated
	 * fluid — a wrongly-labelled tank is worse than an unlabelled one. Only reachable in a pack with
	 * >32767 registered fluids; the tank itself is unaffected either way.
	 */
	private static int fluidRegistryId(FluidHolder fluid) {
		if (fluid.isEmpty()) {
			return FLUID_ID_NONE;
		}
		int id = BuiltInRegistries.FLUID.getId(fluid.fluid());
		return id > Short.MAX_VALUE ? FLUID_ID_NONE : id;
	}
}
