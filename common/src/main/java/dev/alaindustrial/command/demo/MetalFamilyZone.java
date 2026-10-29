package dev.alaindustrial.command.demo;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.ConcreteRailBeamBlock;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;

/**
 * Zone <b>metals</b> (MOD-796, x=61..85, rows z=26..32): the storage block of every ingot of the mod and the
 * stairs, slab and wall cut from it — one column per metal, a free column between two metals, so a row
 * reads as "the same shape, every metal" and a column as "every shape of one metal". The tempered iron
 * fence runs north-south beside the last column.
 *
 * <p>Rows, nearest the camera first: blocks (z=26), stairs (z=28), slabs (z=30), walls (z=32). The stairs
 * face south, so the step faces the camera — facing north they would read as a plain cube from it. The
 * block row repeats the tempered iron block that already stands in the misc zone: here it completes the
 * column, there it stands beside the cabinet.
 *
 * <p>Clear of its neighbours by one free row or column on every side: the misc row at z=24, the lab plaque
 * at z=34 and the garden at x=83..84 to the north. The {@code monitor} camera hangs over row z=32 at height
 * seven, above anything here.
 *
 * <p>MOD-795 adds a row behind the lab plaque (z=36, x=74..84, one block every other column): reinforced
 * glass, the industrial light, then the tempered iron bars, ladder, trapdoor and chain. The ladder hangs on a
 * tempered iron block at z=37, or it would have nothing to stand on. Row z=35 stays free between it and the
 * plaque, and the monitoring wall (x 66..70) is west of it.
 *
 * <p>MOD-804 puts a pair of sound absorbers two rows behind it (z=38, x=78..79): touching, so the stand shows
 * them joined into one tier-II panel with two lamps lit, not two single blocks.
 *
 * <p>MOD-927 puts the quarry's building blocks in two rows behind them (x=72..84, one block every other column):
 * concrete fresh and set, its slab and stairs, the hazard stripes, the concrete rail beam lying along x and the
 * scaffold at z=40; the three quarry foundations, the reinforced plate block with its slab and stairs and the plate
 * rail beam, also along x, at z=42. The reactor row ends near x=40, far to the west of z=42.
 *
 * <p>Domain (coding standard, section 1): Decor.
 */
final class MetalFamilyZone implements DemoZone {
	/** The metals of the family, in the creative-tab order — the same table as tools/gen_metal_blocks.py. */
	static final List<String> METALS = List.of("tin", "silver", "nickel", "uranium", "palladium", "bronze", "invar",
			"cupronickel", "electrum", "netherite_alloy", "shielding_alloy", "tempered_iron");

	static final DemoStand.TpPoint METALS_CAMERA =
			new DemoStand.TpPoint("metals", 72.0, 6.0, 21.0, 0.0f, 35.0f, false);

	private static final int X0 = 61;
	private static final int STEP = 2;
	private static final int FENCE_X = 85;
	/** MOD-795: the row of glass, light and tempered iron fittings. */
	private static final int FITTINGS_Z = 36;
	/** MOD-804: the sound absorber pair. */
	private static final int ABSORBER_Z = 38;
	/** MOD-927: the concrete row and the foundation-and-plate row of the quarry's building blocks. */
	private static final int CONCRETE_Z = 40;
	private static final int FOUNDATION_Z = 42;
	private static final int QUARRY_X0 = 72;

	@Override
	public void build(StandWriter w) {
		for (int i = 0; i < METALS.size(); i++) {
			String metal = METALS.get(i);
			int x = X0 + STEP * i;
			w.set(x, 1, 26, block(metal + "_block"));
			w.place(w.origin().offset(x, 1, 28),
					block(metal + "_stairs").defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
			w.set(x, 1, 30, block(metal + "_slab"));
			w.set(x, 1, 32, block(metal + "_wall"));
		}
		for (int z = 26; z <= 30; z++) {
			w.set(FENCE_X, 1, z, block("tempered_iron_fence"));
		}
		w.set(74, 1, FITTINGS_Z, block("reinforced_glass"));
		w.set(76, 1, FITTINGS_Z, block("industrial_light"));
		w.set(78, 1, FITTINGS_Z, block("tempered_iron_bars"));
		w.set(80, 1, FITTINGS_Z + 1, block("tempered_iron_block"));
		w.place(w.origin().offset(80, 1, FITTINGS_Z),
				block("tempered_iron_ladder").defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		w.set(82, 1, FITTINGS_Z, block("tempered_iron_trapdoor"));
		w.set(84, 1, FITTINGS_Z, block("tempered_iron_chain"));
		w.set(78, 1, ABSORBER_Z, block("sound_absorber"));
		w.set(79, 1, ABSORBER_Z, block("sound_absorber"));
		quarryConstruction(w);
	}

	/** MOD-927: the quarry's building blocks, released as decor ahead of the quarry. */
	private static void quarryConstruction(StandWriter w) {
		w.set(QUARRY_X0, 1, CONCRETE_Z, block("fresh_concrete"));
		w.set(QUARRY_X0 + STEP, 1, CONCRETE_Z, block("industrial_concrete"));
		w.set(QUARRY_X0 + 2 * STEP, 1, CONCRETE_Z, block("industrial_concrete_slab"));
		w.place(w.origin().offset(QUARRY_X0 + 3 * STEP, 1, CONCRETE_Z), block("industrial_concrete_stairs")
				.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		w.set(QUARRY_X0 + 4 * STEP, 1, CONCRETE_Z, block("hazard_stripe_block"));
		// Along x, so the rails read across the camera's view.
		w.place(w.origin().offset(QUARRY_X0 + 5 * STEP, 1, CONCRETE_Z), block("concrete_rail_beam")
				.defaultBlockState().setValue(ConcreteRailBeamBlock.AXIS, Direction.Axis.X));
		w.set(QUARRY_X0 + 6 * STEP, 1, CONCRETE_Z, block("tempered_iron_scaffold"));
		w.place(w.origin().offset(QUARRY_X0 + 6 * STEP, 1, FOUNDATION_Z), block("plate_rail_beam")
				.defaultBlockState().setValue(ConcreteRailBeamBlock.AXIS, Direction.Axis.X));
		w.set(QUARRY_X0, 1, FOUNDATION_Z, block("quarry_foundation"));
		w.set(QUARRY_X0 + STEP, 1, FOUNDATION_Z, block("invar_quarry_foundation"));
		w.set(QUARRY_X0 + 2 * STEP, 1, FOUNDATION_Z, block("netherite_alloy_quarry_foundation"));
		w.set(QUARRY_X0 + 3 * STEP, 1, FOUNDATION_Z, block("reinforced_plate_block"));
		w.set(QUARRY_X0 + 4 * STEP, 1, FOUNDATION_Z, block("reinforced_plate_slab"));
		w.place(w.origin().offset(QUARRY_X0 + 5 * STEP, 1, FOUNDATION_Z), block("reinforced_plate_stairs")
				.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
	}

	/** A block of the family by its id; a missing one throws rather than leaving a hole in the row. */
	static Block block(String path) {
		return BuiltInRegistries.BLOCK.getOptional(Industrialization.id(path))
				.orElseThrow(() -> new IllegalStateException("metal family block missing: " + path));
	}
}
