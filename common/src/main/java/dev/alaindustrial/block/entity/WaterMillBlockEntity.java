package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.environment.WaterMillClearance;
import dev.alaindustrial.core.environment.WaterMillInterference;
import dev.alaindustrial.core.environment.WaterMillOutput;
import dev.alaindustrial.core.environment.WaterMillWheelGeometry;
import dev.alaindustrial.core.machine.ComponentTier;
import dev.alaindustrial.menu.WaterMillMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * LV water mill (spec: alaindustrial:water_mill) — a passive, fuel-free generator. Each tick it counts
 * <b>flowing</b> water (a current — not a still source, MOD-188) in the four cells the <b>wheel</b>
 * sweeps through — above, below and to each side of the front cell, NOT around the mill block itself
 * (MOD-352) — and produces {@link Config#waterMillEuPerTick} EU/t per driven side: 0–4 EU/t,
 * continuous. A crafted
 * {@code water_mill_wheel} must be installed in the single component slot. Since MOD-189 the
 * wheel is a durability component: it wears out only while the mill produces EU (wear proportional to
 * output) and breaks when spent — see {@link AbstractGeneratorBlockEntity#wearComponent}.
 * The remaining production calculation is a stateless world read. Buffer
 * {@link Config#waterMillBuffer}, LV output.
 *
 * <p>It never touches the fluid-tank/{@code FluidStorage} system; it reads {@code level.getFluidState}
 * directly. Energy persists via {@link MachineBlockEntity}; the water mill adds no NBT of its own.
 *
 * <p>Sync channels: 0 energy, 1 capacity, 2 driven-side count (the wheel renderer's speed input),
 * 3 mode, 4 current production in EU/t (MOD-348, shown in the GUI status row).
 */
public class WaterMillBlockEntity extends AbstractGeneratorBlockEntity implements MenuProvider {
	public static final int WHEEL_SLOT = 0;
	/** Machine-slot count (indices before the upgrade block) — the client menu stub sizes its container from this (MOD-439). */
	public static final int SLOT_COUNT = 1;

	/**
	 * Status carried by the {@code maxProgress} sync channel (slot 3) — the wheel renderer hides the
	 * wheel on any non-{@link #MODE_OK} mode and the GUI shows the matching label. Kept minimal: the
	 * water mill has none of the wind mill's weather/altitude modes, only "running normally", "wheels
	 * clash" and "wheel blocked by a solid block" (MOD-179). Obstruction masks interference (mirrors
	 * the wind mill's priority order): clear the solid block first, then worry about spacing.
	 */
	public static final int MODE_OK = 0;
	public static final int MODE_INTERFERENCE = 1;
	public static final int MODE_OBSTRUCTED = 2;
	/**
	 * Wheel installed and free to spin, but no current in any of the four cells the wheel sweeps — the
	 * player-facing hint "place water next to the mill" (MOD-179 feedback). Since MOD-352 those are the
	 * cells around the WHEEL, not around the mill block. Unlike interference/obstruction this does NOT
	 * hide the wheel: a dry wheel stands still but stays rendered.
	 */
	public static final int MODE_NO_WATER = 3;
	/**
	 * No wheel in the slot — the mill cannot run at all, and says so.
	 *
	 * <p>Before MOD-430 a bare mill reported {@link #MODE_OK}, on the reasoning that "an empty slot is
	 * its own message". In the GUI it was not: the status row simply stayed blank, so a player who had
	 * not yet learned that the mill needs a wheel saw a working-looking screen that produced nothing
	 * and offered no clue. The Wind Turbine has told that story since day one with
	 * {@code MODE_NO_ROTOR}; this is the same answer to the same question.
	 *
	 * <p>It also splits a conflated meaning: {@code MODE_OK} used to cover both "running normally" and
	 * "nothing to run with", which forced the screen to re-ask the slot to tell them apart.
	 */
	public static final int MODE_NO_WHEEL = 4;

	/** LV output packet cap (32 EU/t), shared with the other LV generators. */
	private static final int MAX_EXTRACT = 32;

	/**
	 * Ticks between wheel-interference scans. The 7×7×7 neighbour sweep is too heavy to run every tick,
	 * and interference only changes when a player places/breaks a nearby mill or wheel, so a coarse
	 * cadence is plenty; the cached result is reused in between.
	 */
	private static final int SCAN_INTERVAL = 20;

	private int scanCounter;
	private boolean cachedInterfered;
	/**
	 * Effective generation rate in EU/t, projected on sync channel 4 (MOD-348). "Effective" = after
	 * {@link Config#globalEuRateMultiplier} (MOD-356), i.e. the rate the buffer gains while it has room,
	 * rather than {@link #produce}'s mechanical output. Transient: it is recomputed every tick — see
	 * {@link #publishEffectiveRate} — so it is never serialised and never read off a client block entity;
	 * the open screen gets it through the menu's {@link ContainerData} sync, not through the block-entity
	 * update packet.
	 */
	private int productionRate;

	public WaterMillBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.WATER_MILL_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT, Config.waterMillBuffer, MAX_EXTRACT);
	}

	/**
	 * Count the four cells the <b>wheel</b> sweeps through that carry <b>flowing</b> water (MOD-352).
	 *
	 * <p><b>The cells are around the wheel, not around the block.</b> The wheel is not inside the mill:
	 * the renderer pushes it {@link WaterMillWheelGeometry#DISC_PUSH} blocks along {@code FACING} and
	 * gives it a rim radius of {@link WaterMillWheelGeometry#DISC_HALF_SIZE}, so its box spans the
	 * <em>front</em> cell plus one cell above, below and to each side of it. Counting the mill block's
	 * own horizontal neighbours (what this did before MOD-352) meant three of the four counted cells —
	 * including the back one, which carries the output port for the cable — never touched the wheel at
	 * all, while the four cells the rim genuinely passes through counted for nothing. Players read the
	 * model off the picture, and the picture is the wheel: the mill block itself can be buried in the
	 * bank with only the wheel sticking out.
	 *
	 * <p>The four cells map onto how real water wheels are driven: below is undershot (the classic —
	 * the lower arc dips into the current), above is overshot (water poured over the top), and the two
	 * sides are the horizontal rim sweep. The hub cell itself is deliberately NOT counted: it is where
	 * the axle sits, and leaving it out keeps the maximum at a clean 4 EU/t with one unit per side of
	 * the wheel.
	 *
	 * <p>Only a current turns a wheel, so a still source block ({@link FluidState#isSource()}) does NOT
	 * count — only flowing/falling water (MOD-188). That closes the "drop it in a still pool and it
	 * powers itself" exploit. Vanilla rivers and oceans are mostly source blocks, so the player must
	 * supply an actual current (a source feeding a channel with a drain, or a waterfall).
	 */
	private static int waterSides(Level level, BlockPos pos, Direction facing) {
		BlockPos front = pos.relative(facing);
		BlockPos[] driveCells = {
			front.above(),
			front.below(),
			front.relative(facing.getClockWise()),
			front.relative(facing.getCounterClockWise()),
		};
		int sides = 0;
		for (BlockPos cell : driveCells) {
			FluidState fluid = level.getFluidState(cell);
			if (fluid.is(FluidTags.WATER) && !fluid.isSource()) {
				sides++;
			}
		}
		return sides;
	}

	@Override
	protected int produce(Level level, BlockPos pos, BlockState state) {
		if (items.get(WHEEL_SLOT).isEmpty()) {
			// No wheel → nothing rendered, nothing to clash with. Force a rescan when a wheel returns.
			cachedInterfered = false;
			scanCounter = 0;
			setState(0, MODE_NO_WHEEL);
			return 0;
		}
		Direction facing = state.hasProperty(HorizontalMachineBlock.FACING)
				? state.getValue(HorizontalMachineBlock.FACING)
				: Direction.NORTH;
		// Wheel clearance (MOD-179): a solid block in the swept area makes the wheel clip through it.
		// Cheap (6 block reads), so checked every tick like the wind mill's blade clearance. Also closes
		// the face-to-face-with-no-gap blind spot of the AABB interference test: the neighbour mill's
		// casing sits in our front cell.
		boolean obstructed = WaterMillClearance.hasObstruction(level, pos, facing);
		// Wheel interference (MOD-175): a neighbouring mill's wheel overlapping ours makes both look
		// broken (z-fighting) and stalls both. The 7×7×7 scan is too heavy per tick — sample it on a
		// cadence and cache between. Only meaningful once a wheel is installed (checked above), and
		// masked by an obstruction (blocked wheels cannot clash — fix the wall first).
		if (scanCounter % SCAN_INTERVAL == 0) {
			cachedInterfered = !obstructed && WaterMillInterference.hasInterference(level, pos, facing);
		}
		scanCounter++;
		if (obstructed) {
			// Wheel hidden by the renderer, generation halted until the blocking block is removed.
			setState(0, MODE_OBSTRUCTED);
			return 0;
		}
		if (cachedInterfered) {
			// Wheel hidden by the renderer, generation halted — both interfering mills stall (symmetric).
			setState(0, MODE_INTERFERENCE);
			return 0;
		}
		int sides = waterSides(level, pos, facing);
		if (sides == 0) {
			// Wheel present and free, but no current reaches it: show the "no water" hint instead of a
			// silent idle (MOD-179 feedback) — the player sees exactly what is missing.
			setState(0, MODE_NO_WATER);
			return 0;
		}
		// Wheel grade (MOD-385): resolved from the slot, folded into euFor so a better wheel raises the
		// rate itself rather than being applied on top of an already-computed number.
		ComponentTier wheelTier = tierOf(items.get(WHEEL_SLOT), ComponentTier.WATER_MILL_WHEEL);
		int made = WaterMillOutput.euFor(sides, Config.waterMillEuPerTick, wheelTier.outputMultiplier());
		setState(sides, MODE_OK);
		// Wheel wear (MOD-189): wear accrues only while the wheel actually turns water into EU (made > 0).
		// No weather stress on the water mill, so the weather multiplier is a flat 1.0. EU-per-damage is
		// the grade's own (MOD-385) — `made` already carries the grade's output multiplier.
		wearComponent(level, pos, WHEEL_SLOT, made, 1.0f, wheelTier.euPerDamage());
		return made;
	}

	/**
	 * Unlike the default generator (5-face output), the water mill emits EU only from its back face
	 * (opposite of FACING) — the same single-output contract as the wind mill (R-NRG-03). The front
	 * carries the wheel and stays inert; the four sides stand in the water and are inert too, so a
	 * cable (or a battery box) must connect to the back — the face with the output-port texture.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		Direction facing = getBlockState().getValue(HorizontalMachineBlock.FACING);
		if (worldFace == facing.getOpposite()) {
			return EnergyRole.OUT;
		}
		return EnergyRole.NONE;
	}

	/**
	 * Store the wheel's spin input ({@code progress} = driven-side count, the value the wheel renderer
	 * reads via {@code dataAccess} slot 2) and the status {@code mode} (the {@code maxProgress} channel,
	 * slot 3), and push a block-entity update to watching clients whenever either changes.
	 *
	 * <p>The water mill has no {@code lit} blockstate (it is a passive generator), so the base generator's
	 * {@code updateLit} is a no-op and never syncs. Without this explicit sync the server would refresh
	 * these every tick but never notify the client, leaving the wheel renderer reading the stale
	 * chunk-load value (usually 0) — the wheel would freeze or stay visible during interference. Sync only
	 * on change, so a steady water setup does not spam block updates (mirrors the wind-mill rotor fix).
	 */
	private void setState(int sides, int mode) {
		if (this.progress != sides || this.maxProgress != mode) {
			this.progress = sides;
			this.maxProgress = mode;
			syncBlockEntityToClient();
		}
	}

	/**
	 * The mill's readout rides channel 4 (MOD-348), which since MOD-356 carries the <b>effective</b> rate —
	 * {@link #produce}'s mechanical output after {@link Config#globalEuRateMultiplier}, i.e. the rate the
	 * buffer gains while it has room. Assigned unconditionally and deliberately kept out of {@link #setState}'s
	 * change test: it reaches an open screen through the menu's ContainerData sync, which runs every tick
	 * on its own, and no client-side consumer reads it off the block entity, so folding it into the test
	 * would only widen the block-update trigger. The base generator calls this every tick — including with
	 * 0 on every early return in {@code produce()} — so a mill that stops never leaves a stale reading.
	 */
	@Override
	protected void publishEffectiveRate(int effectiveEuPerTick) {
		this.productionRate = effectiveEuPerTick;
	}

	/**
	 * Five-wide data — hides {@link MachineBlockEntity#DATA_COUNT} so {@code WaterMillBlockEntity.DATA_COUNT}
	 * names this machine's width for the bridge below and for {@code WaterMillMenu}'s client stub (MOD-235).
	 */
	public static final int DATA_COUNT = 5;

	/**
	 * Five-wide data: the shared base 0..3 (energy, capacity, water-face count, mode) plus the effective
	 * production rate on channel 4 (MOD-348, made post-multiplier by MOD-356).
	 *
	 * <p><b>Why the rate needs a channel of its own.</b> Channel 2 already carries the <em>water-face
	 * count</em> (0..4), which {@code WaterMillWheelBlockEntityRenderer} turns into the wheel's angular
	 * speed via {@code Math.min(production, 4)} — so it cannot double as EU/t without capping the
	 * in-world wheel at {@code waterMillEuPerTick} faces. Deriving EU/t on the client instead
	 * ({@code faces × waterMillEuPerTick}) is not an option either: {@link Config} is loaded per side
	 * and never synced, so a dedicated server with retuned balance would feed every client a number
	 * from its own local config file.
	 */
	private final ContainerData waterMillData = new ContainerData() {
		@Override
		public int get(int index) {
			return index == 4 ? productionRate : WaterMillBlockEntity.this.dataAccess.get(index);
		}

		@Override
		public void set(int index, int value) {
			if (index == 4) {
				productionRate = value;
			} else {
				WaterMillBlockEntity.this.dataAccess.set(index, value);
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	@Override
	public ContainerData getDataAccess() {
		return waterMillData;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		// MOD-385: accepts every wheel grade via the shared tag. canPlaceItemThroughFace below still
		// demands an EMPTY slot (MOD-179), so automation cannot stack wheels or hot-swap through a pipe.
		return slot == WHEEL_SLOT && stack.is(ModTags.Items.WATER_MILL_WHEELS);
	}

	/**
	 * Automation may only insert a wheel while the slot is empty (MOD-179). The "one wheel" limit
	 * otherwise lives solely in the GUI slot's {@code getMaxStackSize} — a hopper bypasses the menu and
	 * would happily top the slot up to a full stack of wheels. Manual GUI placement is unaffected
	 * ({@code canPlaceItem}), so swapping a wheel by hand still works.
	 */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return super.canPlaceItemThroughFace(slot, stack, side) && items.get(WHEEL_SLOT).isEmpty();
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.water_mill");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new WaterMillMenu(syncId, inventory, this, ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}
}
