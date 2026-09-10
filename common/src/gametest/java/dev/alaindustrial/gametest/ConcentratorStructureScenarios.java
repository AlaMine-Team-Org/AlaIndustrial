package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.ConcentratorPart;
import dev.alaindustrial.block.ConcentratorSectionBlock;
import dev.alaindustrial.block.ConcentratorStructure;
import dev.alaindustrial.block.RadiantSolarPanelBlock;
import dev.alaindustrial.block.entity.RadiantSolarPanelBlockEntity;
import dev.alaindustrial.core.energy.EnergyPort;
import dev.alaindustrial.core.energy.EnergyPortHost;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Mirror Concentrator's two-by-two-by-two assembly (MOD-603).
 *
 * <p><b>Why these scenarios call the assembler directly.</b> Programmatic placement reaches neither
 * {@code setPlacedBy} nor the neighbour update a real placement causes (MOD-015), so a test that
 * merely wrote the sections into the world would assert on a machine the harness had no way to
 * build. The Workstation's scenarios call {@code tryAssemble} for exactly this reason and this is
 * the same trade: what is covered here is the ASSEMBLY RULE — which boxes count, which do not, and
 * what happens when one is broken — while the two hooks that reach it are covered by playing.
 *
 * <p>Disassembly is different and is NOT helped along: breaking a cell here goes through the same
 * {@code updateShape} cascade a pickaxe causes, so those assertions do test the wiring.
 *
 * <p>The core sits at {@link #CORE} so that all four boxes it could grow into, and the neighbours
 * they touch, stay inside the force-loaded rig.
 */
public final class ConcentratorStructureScenarios {

	private ConcentratorStructureScenarios() {
	}

	/** Bottom corner the panel stands in. Chosen so every rotation of the box fits the rig. */
	private static final BlockPos CORE = new BlockPos(3, 2, 3);

	/** Put a grown, un-assembled concentrator at {@link #CORE}. */
	private static void placeCore(GameTestHelper helper) {
		helper.setBlock(CORE, ModContent.RADIANT_SOLAR_PANEL.get().defaultBlockState());
	}

	/** Drop a loose section into every cell of the box but the core's. */
	private static void placeSections(GameTestHelper helper, Direction facing, ConcentratorPart skip) {
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part == ConcentratorPart.CORE || part == skip) {
				continue;
			}
			helper.setBlock(CORE.offset(part.worldOffset(facing)),
					ModContent.CONCENTRATOR_SECTION.get().defaultBlockState());
		}
	}

	/** Run the assembler on the core, the way a placement hook would. */
	private static void assemble(GameTestHelper helper) {
		ConcentratorStructure.tryAssemble(helper.getLevel(), helper.absolutePos(CORE));
	}

	private static BlockState stateAt(GameTestHelper helper, BlockPos relative) {
		return helper.getLevel().getBlockState(helper.absolutePos(relative));
	}

	/** Assert that every cell of the box carries the part and facing it should. */
	private static void assertAssembled(GameTestHelper helper, Direction facing) {
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			BlockPos cell = CORE.offset(part.worldOffset(facing));
			if (!ConcentratorStructure.isMember(stateAt(helper, cell), part, facing)) {
				helper.fail("cell " + part + " of the " + facing + " box did not assemble: "
						+ stateAt(helper, cell));
			}
		}
	}

	/**
	 * Seven sections around the grown panel make the machine — and they make it in the box the player
	 * actually filled, not in a fixed one.
	 *
	 * @implements MOD-603 — assembly, and the direction coming from where the sections went
	 */
	public static void assemblesInTheBoxThatWasFilled(GameTestHelper helper) {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			// Clear the whole neighbourhood so the previous rotation cannot be mistaken for this one.
			clearArea(helper);
			placeCore(helper);
			placeSections(helper, facing, null);
			assemble(helper);
			assertAssembled(helper, facing);
		}
		helper.succeed();
	}

	/**
	 * Six sections are not seven: the machine must refuse to be most of the way built.
	 *
	 * @implements MOD-603 — an incomplete box does not assemble
	 */
	public static void refusesToAssembleWithACellMissing(GameTestHelper helper) {
		clearArea(helper);
		placeCore(helper);
		placeSections(helper, Direction.NORTH, ConcentratorPart.TOP_BACK_RIGHT);
		assemble(helper);
		if (stateAt(helper, CORE).getValue(RadiantSolarPanelBlock.ASSEMBLED)) {
			helper.fail("the panel assembled with one cell still empty");
		}
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part == ConcentratorPart.CORE || part == ConcentratorPart.TOP_BACK_RIGHT) {
				continue;
			}
			BlockPos cell = CORE.offset(part.worldOffset(Direction.NORTH));
			if (stateAt(helper, cell).getValue(ConcentratorSectionBlock.PART)
					!= ConcentratorPart.LOOSE) {
				helper.fail("section " + part + " claimed a structure that was never completed");
			}
		}
		helper.succeed();
	}

	/**
	 * Breaking ANY one of the eight cells takes the whole machine apart, and leaves every survivor in
	 * the form the player originally put down.
	 *
	 * @implements MOD-603 — disassembly by degradation, from each cell in turn
	 */
	public static void breakingAnyCellDisassemblesAll(GameTestHelper helper) {
		for (ConcentratorPart broken : ConcentratorPart.CELLS) {
			clearArea(helper);
			placeCore(helper);
			placeSections(helper, Direction.NORTH, null);
			assemble(helper);
			assertAssembled(helper, Direction.NORTH);

			helper.setBlock(CORE.offset(broken.worldOffset(Direction.NORTH)), Blocks.AIR);
			for (ConcentratorPart part : ConcentratorPart.CELLS) {
				if (part == broken) {
					continue;
				}
				BlockPos cell = CORE.offset(part.worldOffset(Direction.NORTH));
				BlockState state = stateAt(helper, cell);
				if (ConcentratorStructure.partOf(state) != null) {
					helper.fail("breaking " + broken + " left " + part + " still assembled: " + state);
				}
			}
		}
		helper.succeed();
	}

	/**
	 * Energy stays on the core. The seven sections have no block entity at all, so a cable reaching
	 * one of them finds nothing to draw from — the machine has exactly one face, wherever the player
	 * happens to tap it.
	 *
	 * @implements MOD-603 — only the core carries energy
	 */
	public static void onlyTheCoreCarriesEnergy(GameTestHelper helper) {
		clearArea(helper);
		placeCore(helper);
		placeSections(helper, Direction.NORTH, null);
		assemble(helper);
		assertAssembled(helper, Direction.NORTH);

		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(CORE))
				instanceof RadiantSolarPanelBlockEntity core)) {
			helper.fail("the assembled core lost its block entity");
			return;
		}
		if (!(core instanceof EnergyPortHost host)) {
			helper.fail("the assembled core is no longer an energy host");
			return;
		}
		// A side face, not the top: the top of a solar panel is the working surface and never emits,
		// assembled or not (the one-block scenarios assert that rule directly).
		EnergyPort side = host.energyPort(Direction.NORTH);
		if (side == null || !side.supportsExtraction()) {
			helper.fail("the core stopped emitting EU once assembled");
		}
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			if (part == ConcentratorPart.CORE) {
				continue;
			}
			BlockPos cell = CORE.offset(part.worldOffset(Direction.NORTH));
			if (helper.getLevel().getBlockEntity(helper.absolutePos(cell)) != null) {
				helper.fail("cell " + part + " has a block entity — sections must be inert");
			}
		}
		helper.succeed();
	}

	/**
	 * Assembling must never make the machine WORSE.
	 *
	 * <p>The two knobs are equal by design — the structure is the concentrator at its proper size,
	 * not a higher tier — so this cannot demand that one exceed the other. What it can demand is the
	 * floor: whatever a future balance pass does to either number, a player who spent seven sections
	 * must not end up generating less than before they built. That is the invariant a careless edit
	 * would break, and the only one the pair genuinely has.
	 *
	 * <p>Asserted on the CONFIGURED values, not on literals: hard-coding today's 8 would turn every
	 * balance change into a red test instead of the balance change it is.
	 *
	 * @implements MOD-603 — building the structure is never a downgrade
	 */
	public static void assembledOutputIsNeverADowngrade(GameTestHelper helper) {
		clearArea(helper);
		placeCore(helper);
		placeSections(helper, Direction.NORTH, null);
		assemble(helper);
		assertAssembled(helper, Direction.NORTH);
		if (Config.radiantAssembledEuPerTick < Config.radiantEuPerTick) {
			helper.fail("the assembled knob (" + Config.radiantAssembledEuPerTick
					+ ") is below the one-block knob (" + Config.radiantEuPerTick
					+ ") — seven sections would buy the player a weaker machine");
		}
		helper.succeed();
	}

	/**
	 * A roof over ANY of the four columns stops the machine, and the machine does not shade itself.
	 *
	 * <p>Both halves matter and both were wrong at first. Covering three corners out of four did
	 * nothing, because only the core's own column was ever read; and once every column was read, the
	 * core found its own top cell overhead and would have run at half power under an open sky.
	 *
	 * @implements MOD-603 — sky is judged over the whole footprint, and above the structure
	 */
	public static void anyCoveredColumnStopsTheMachine(GameTestHelper helper) {
		for (ConcentratorPart covered : ConcentratorPart.CELLS) {
			Vec3i cell = covered.canonicalOffset();
			if (cell == null || cell.getY() != 1) {
				continue; // only the four top cells carry a column of their own
			}
			clearArea(helper);
			placeCore(helper);
			placeSections(helper, Direction.NORTH, null);
			assemble(helper);
			assertAssembled(helper, Direction.NORTH);

			BlockState core = stateAt(helper, CORE);
			BlockPos absoluteCore = helper.absolutePos(CORE);
			if (!(core.getBlock() instanceof RadiantSolarPanelBlock block)) {
				helper.fail("the core stopped being a concentrator");
				return;
			}
			if (!block.isWorking(helper.getLevel(), absoluteCore, core)) {
				helper.fail("the assembled machine is idle under an open sky — it is shading itself");
				return;
			}

			BlockPos roof = CORE.offset(covered.worldOffset(Direction.NORTH)).above();
			helper.setBlock(roof, Blocks.STONE);
			if (block.isWorking(helper.getLevel(), absoluteCore, stateAt(helper, CORE))) {
				helper.fail("a roof over " + covered + " did not stop the machine");
			}
			helper.setBlock(roof, Blocks.AIR);
		}
		helper.succeed();
	}

	/**
	 * The middle the wings turn about is the middle of the cells the structure actually occupies.
	 *
	 * <p>This is the assertion that was missing when the mirrors flew off the machine. The structure
	 * grows to ONE side of the core, and which side depends on the facing, so its middle sits at a
	 * different place relative to the core on each of the four — {@code (1, 1)} north, {@code (0, 1)}
	 * east, {@code (0, 0)} south, {@code (1, 0)} west. A hard-coded {@code (1, 1)} looked right on
	 * north, which is the one facing every render and every earlier scenario happened to use.
	 *
	 * <p>Asserted against the CELLS, not against a second copy of the formula: the cells are where the
	 * blocks really are, so a wrong formula cannot agree with them by construction.
	 *
	 * @implements MOD-603 — the wings turn about the structure's real middle on every facing
	 */
	public static void structureCentreMatchesItsCells(GameTestHelper helper) {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (ConcentratorPart part : ConcentratorPart.CELLS) {
				Vec3i offset = part.worldOffset(facing);
				minX = Math.min(minX, offset.getX());
				maxX = Math.max(maxX, offset.getX());
				minZ = Math.min(minZ, offset.getZ());
				maxZ = Math.max(maxZ, offset.getZ());
			}
			// Cells are unit cubes, so the box runs from min to max + 1 and its middle is the average.
			float expectedX = (minX + maxX + 1) / 2.0f;
			float expectedZ = (minZ + maxZ + 1) / 2.0f;
			float[] centre = ConcentratorPart.structureCentre(facing);
			if (Math.abs(centre[0] - expectedX) > 1.0e-4f
					|| Math.abs(centre[1] - expectedZ) > 1.0e-4f) {
				helper.fail("facing " + facing + ": structureCentre says ("
						+ centre[0] + ", " + centre[1] + ") but the cells sit around ("
						+ expectedX + ", " + expectedZ + ")");
			}
		}
		helper.succeed();
	}

	/**
	 * The transform the wings are drawn with lands every cell exactly where its blocks stand.
	 *
	 * <p>This is the assertion that was missing while the mirrors kept flying off, and it is stated
	 * against the CELLS — where the blocks really are — rather than against a second copy of the
	 * formula, so a wrong transform cannot agree with it. It would have caught both mistakes: the
	 * hard-coded middle, and using the world middle where the canonical one belonged.
	 *
	 * @implements MOD-603 — canonical-to-world mapping of the structure, on every facing
	 */
	public static void canonicalMappingLandsOnTheCells(GameTestHelper helper) {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			for (ConcentratorPart part : ConcentratorPart.CELLS) {
				Vec3i canonical = part.canonicalOffset();
				Vec3i world = part.worldOffset(facing);
				if (canonical == null) {
					continue;
				}
				// Middle of this cell, canonical and actual: cells are unit cubes, so it is +0.5.
				float[] mapped = ConcentratorPart.canonicalToWorld(facing,
						canonical.getX() + 0.5f, canonical.getZ() + 0.5f);
				float expectedX = world.getX() + 0.5f;
				float expectedZ = world.getZ() + 0.5f;
				if (Math.abs(mapped[0] - expectedX) > 1.0e-4f
						|| Math.abs(mapped[1] - expectedZ) > 1.0e-4f) {
					helper.fail("facing " + facing + ", cell " + part + ": the drawing transform puts it"
							+ " at (" + mapped[0] + ", " + mapped[1] + ") but its block stands at ("
							+ expectedX + ", " + expectedZ + ")");
				}
			}
		}
		helper.succeed();
	}

	/** Air out every cell any rotation could touch, plus the core. */
	private static void clearArea(GameTestHelper helper) {
		for (int dx = -1; dx <= 1; dx++) {
			// Up to dy = 2: the roofs this suite lays down sit one block above the top cells, and a
			// leftover roof would make the next rotation look like a machine that never starts.
			for (int dy = 0; dy <= 2; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					helper.setBlock(CORE.offset(dx, dy, dz), Blocks.AIR);
				}
			}
		}
	}
}
