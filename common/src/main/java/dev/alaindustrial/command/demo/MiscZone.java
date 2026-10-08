package dev.alaindustrial.command.demo;

import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.KokSagyzRootBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.material.Fluids;

/**
 * Zone <b>misc</b> (rows z=20 and z=24, east of the machines): the storage cabinet, the pump chain,
 * the torches, the charging station, the garden and the pools of every fluid the mod adds.
 *
 * <p>Each exhibit is a compact group that has to stay together — a cabinet whose two storage modules
 * must touch to merge, a pump that must touch the generator it feeds, a torch that hangs on its post
 * — and MOD-659 spread the GROUPS apart instead: two to three free cells between one exhibit and the
 * next, where they used to touch.
 *
 * <p>Domain (coding standard, section 1): cross-cutting view (a mixed row); the exhibits belong to
 * the domains of their own blocks.
 */
final class MiscZone implements DemoZone {
	private static final Block FLOOR = StandLayout.FLOOR;

	/** Camera of {@code /ala demo tp misc}. */
	static final DemoStand.TpPoint MISC_CAMERA =
			new DemoStand.TpPoint("misc", 70.0, 7.0, 14.0, 0.0f, 32.0f, false);

	/** The first row of this zone; the second row is at z=24. */
	private static final int Z = 20;

	@Override
	public void build(StandWriter w) {
		storageCabinet(w);
		pumpChain(w);
		torchAndChargers(w);
		blockBreaker(w);
		garden(w);
		fluidPools(w);
		teleporterStation(w);
	}

	/** The storage cabinet, x=62..65, and the tempered iron block west of it. */
	private static void storageCabinet(StandWriter w) {
		// The storage cabinet, x=62..65: the four chest tiers on the floor (iron, silver, gold, electrum —
		// a ladder that reads left to right), plate blocks on the shelf above and a third shelf on top.
		w.set(62, 1, Z, ModContent.IRON_CHEST.get());
		w.set(63, 1, Z, ModContent.SILVER_CHEST.get());
		w.set(64, 1, Z, ModContent.GOLD_CHEST.get());
		w.set(65, 1, Z, ModContent.ELECTRUM_CHEST.get());
		// Plate blocks (MOD-225): machine casing + two decorative plate panels, on the shelf above the chests.
		// MOD-292 puts the MV casing directly on top of the LV one so the tier step is visible side by side.
		w.set(62, 3, Z, ModContent.ADVANCED_MACHINE_CASING.get());
		w.set(62, 2, Z, ModContent.MACHINE_CASING.get());
		// Reinforced Energy Storage (MOD-351): next to the MV casing it is built from, so the shelf reads
		// as the MV column — casing, and the first block assembled on top of it.
		w.set(63, 3, Z, ModContent.CESU.get());
		w.set(63, 2, Z, ModContent.SILVER_PLATE_BLOCK.get());
		w.set(64, 2, Z, ModContent.TEMPERED_IRON_PLATE_BLOCK.get());
		// Industrial Workbench (MOD-062): the Industrialist villager's job-site block on display.
		w.set(65, 2, Z, ModContent.INDUSTRIAL_WORKBENCH.get());
		// MOD-287: two storage modules side by side on the top shelf — adjacent on purpose, so the stand
		// shows them merged into one warehouse rather than two separate ones (the gametest walks the cluster
		// from the first one and expects both).
		w.set(64, 3, Z, ModContent.STORAGE_MODULE.get());
		w.set(65, 3, Z, ModContent.STORAGE_MODULE.get());
		// The tempered iron block stands alone beside the galvanic bath, west of the cabinet.
		w.set(56, 1, Z, ModContent.TEMPERED_IRON_BLOCK.get());
	}

	/** The pump chain, x=69..71, with the diamond chest and the mob repeller tier ladder on top. */
	private static void pumpChain(StandWriter w) {
		// The pump chain, x=69..71: pump → geothermal generator → tank, touching so the pump's tank pushes
		// straight into the generator's. The diamond chest (MOD-599) and the mob repeller tier ladder
		// (MOD-278) stand on top of it. The repellers are placed unpowered: a live field would shove this
		// stand's own test mobs; their trim (iron / silver / electrum) IS the tier readout in the world.
		//
		// The pump draws from the block IN FRONT of it (FACING is its only intake face, the way
		// PumpBlockEntity#acquireFluid starts its search), so it faces NORTH — the default — into a small
		// lava cistern raised on the floor: one source, three walls. A pump set over a pool BELOW it, as the
		// stand did until MOD-659, stands over nothing it can drink and never moved a drop.
		w.set(69, 1, Z, ModContent.PUMP.get());
		w.chargeBuffer(69, 1, Z);
		w.set(69, 1, Z - 1, Blocks.LAVA);
		w.set(68, 1, Z - 1, FLOOR);
		w.set(70, 1, Z - 1, FLOOR);
		w.set(69, 1, Z - 2, FLOOR);
		w.set(70, 1, Z, ModContent.GEOTHERMAL_GENERATOR.get());
		w.set(71, 1, Z, ModContent.FLUID_TANK.get());
		w.fillTank(71, 1, Z, FluidTankBlockEntity.class, be -> be.fluidTank, Fluids.WATER, capacity -> capacity / 2);
		w.set(69, 2, Z, ModContent.DIAMOND_CHEST.get());
		w.set(69, 3, Z, ModContent.MOB_REPELLER.get());
		w.set(70, 3, Z, ModContent.MOB_REPELLER_MV.get());
		w.set(71, 3, Z, ModContent.MOB_REPELLER_HV.get());
	}

	/**
	 * The block breaker (MOD-787), x=73, between the pump chain and the torches. Placed by the stand,
	 * not a player, so it has no owner and stands still; it is here to be looked at, not to dig the stand.
	 */
	private static void blockBreaker(StandWriter w) {
		w.set(73, 1, Z, ModContent.BLOCK_BREAKER.get());
	}

	/** The standing and wall torches, the charging station and the energy condenser. */
	private static void torchAndChargers(StandWriter w) {
		BlockPos origin = w.origin();
		// Enriched Uranium Torch (MOD-085): the standing torch on the floor, and the wall variant mounted
		// on a small stone post (facing WEST → supported by the post block to its east) so both survive.
		w.set(75, 1, Z, ModContent.ENRICHED_URANIUM_TORCH.get());
		// Charging Station (MOD-274): banked full, so a visitor can step straight onto the stand's copy
		// and watch their gear fill — an empty one would only ever show the red "no power" indicator.
		// Sits at floor level under the wall torch; it is a 4px plate, so nothing above it moves.
		w.set(78, 1, Z, ModContent.CHARGE_PAD.get());
		w.chargeBuffer(78, 1, Z);
		// Energy condenser (MOD-546): banked full, so the stand's copy shows the top-tier crystal
		// and a tier-III clot already sitting in its slot — an empty one would just be a dark frame.
		w.set(79, 1, Z, ModContent.ENERGY_CONDENSER.get());
		w.chargeBuffer(79, 1, Z);
		w.set(79, 2, Z, FLOOR);
		w.place(origin.offset(78, 2, Z),
				ModContent.ENRICHED_URANIUM_WALL_TORCH.get().defaultBlockState()
						.setValue(WallTorchBlock.FACING, Direction.WEST));
	}

	/** The garden, x=83..84: trellis, kok-sagyz cross-sections and the drone dock. */
	private static void garden(StandWriter w) {
		BlockPos origin = w.origin();
		// The garden, x=83..84: the drone dock beside a ripe cotton trellis, with the kok-sagyz cross-sections
		// on the row behind them.
		//
		// Cotton trellis (MOD-280): a ripe plant on moist farmland — the stand has to show the crop at
		// its most recognisable stage, with the soil it actually needs. Placed via the vanilla two-block
		// helper so both halves appear; the age is written to BOTH halves, since the upper one carries it
		// only to keep its model in step with the lower.
		w.ripenTrellis(84, Z);
		// Kok-sagyz column (MOD-537): shown as an exposed soil cross-section beside the trellis —
		// tip at the bottom, upper root above it, mature puff on top — so the stand answers "where
		// does the rubber come from" the way the plant itself does: dig the bottom block.
		w.place(origin.offset(84, 0, Z - 1), ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.KokSagyzRootBlock.TIP, true));
		w.place(origin.offset(84, 1, Z - 1), ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState());
		w.place(origin.offset(84, 2, Z - 1), ModContent.KOK_SAGYZ.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.KokSagyzBlock.AGE, dev.alaindustrial.block.KokSagyzBlock.AGE_MATURE));
		// MOD-584: a short harvestable root beside the full column, both in their original soil. Every
		// column of this exhibit lies inside WIDTH: a column past it would survive `clear`.
		w.set(83, 0, Z - 1, FLOOR);
		w.place(origin.offset(83, 1, Z - 1), ModContent.KOK_SAGYZ_ROOT.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.KokSagyzRootBlock.TIP, true));
		w.configure(83, 1, Z - 1, KokSagyzRootBlockEntity.class, "kok-sagyz root to bed in sand",
				root -> root.setSoil(Blocks.SAND.defaultBlockState()));
		w.place(origin.offset(83, 2, Z - 1), ModContent.KOK_SAGYZ.get().defaultBlockState()
				.setValue(dev.alaindustrial.block.KokSagyzBlock.AGE, dev.alaindustrial.block.KokSagyzBlock.AGE_MATURE));
		// Garden Drone Station (MOD-277): the dock beside the trellis plot, charged so its status light
		// reads "powered" rather than "no EU". Placed next to farmland on purpose — the stand should show
		// the block in the context it works in.
		w.set(83, 1, Z, ModContent.GARDEN_DRONE_STATION.get());
		w.chargeBuffer(83, 1, Z);
		// Deliberately NOT stocked with a drone and a hoe. It was, in MOD-659's first cut, and the station
		// did exactly what it is for: it harvested every ripe crop in its zone — the two kok-sagyz plants
		// and the ripe trellis this exhibit exists to SHOW — within a minute of the build. The drone, the
		// seeds, the bone meal and a hoe are in the bench's chests for whoever wants to watch it work.
	}

	/** Second row: the sunken one-block pools of every fluid the mod adds, and the soot and oil fire. */
	private static void fluidPools(StandWriter w) {
		// Second row (z=24): the teleporter station and the pools, each one-block basin a cell of its own.
		//
		// Oil (MOD-238): a sunken one-block oil pool. Kept non-adjacent to the lava and torches on purpose
		// — a directly neighbouring igniter would set it on fire (OilLiquidBlock ignition mechanic) and the
		// stand would showcase a fire block instead.
		w.set(68, -1, 24, FLOOR);
		w.set(68, 0, 24, ModContent.OIL_BLOCK.get());
		// Distillation fractions (MOD-251): the same sunken-pool pattern for diesel and fuel oil —
		// water-like fluids, so no ignition spacing worries.
		w.set(72, -1, 24, FLOOR);
		w.set(72, 0, 24, ModContent.DIESEL_BLOCK.get());
		w.set(76, -1, 24, FLOOR);
		w.set(76, 0, 24, ModContent.FUEL_OIL_BLOCK.get());
		// The organic chain's two fluids (MOD-146/MOD-525), same sunken-basin pattern. The solution stands
		// by the fermenter it comes from, and the sprinkler (built with the machines) sits between the
		// fuel-oil pool and the biofuel.
		w.set(80, -1, 24, FLOOR);
		w.set(80, 0, 24, ModContent.BIOFUEL_BLOCK.get());
		w.set(44, -1, 24, FLOOR);
		w.set(44, 0, 24, ModContent.NUTRIENT_SOLUTION_BLOCK.get());
		// Burning oil and its soot (MOD-638), on the east edge of the floor, away from the oil pool and
		// from anything flammable. The fire is live: it burns out within seconds of the build and may
		// itself leave soot — the coverage scan runs on the build tick, while it still stands.
		w.set(86, 1, 28, ModContent.SOOT_LAYER.get());
		w.set(86, 1, 32, ModContent.OIL_FIRE.get());
	}

	/** The teleporter station: a charged battery box, a cable, the station and its glass capsule. */
	private static void teleporterStation(StandWriter w) {
		// Teleporter station (MOD-091): a charged battery box feeds it through a cable, so the stand
		// shows it actually taking EU rather than standing there inert. It has no GUI and cannot jump
		// yet — the remote is MOD-092. Hidden from the creative tab until MOD-093, so the demo stand
		// and /give are the only ways to see it right now.
		//
		// The box's rotation is explicit and load-bearing: it emits ONLY from the face opposite its
		// FACING and is inert on the other four (single-axis IO, MOD-006 — see
		// BatteryBoxBlockEntity#energyRoleForFace). The cable sits to its east, so the box must face
		// WEST for its output face to meet it. Placed with the default state (FACING=NORTH) it would
		// emit southward into thin air, the cable would not even connect, and the station would sit
		// there dead next to a full battery.
		w.placeFacing(62, 1, 24, ModContent.BATTERY_BOX.get(), Direction.WEST);
		w.chargeBuffer(62, 1, 24);
		w.set(63, 1, 24, ModContent.COPPER_CABLE.get());
		w.set(64, 1, 24, ModContent.TELEPORTER.get());
		// MOD-112: a station is a jump destination only with its capsule, so the stand builds one from
		// two glass blocks. The glass goes through the ledger; assembling swaps it in place.
		w.assembleCapsule(64, 1, 24);
	}
}
