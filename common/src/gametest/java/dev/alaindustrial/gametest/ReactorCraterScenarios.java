package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.reactor.ReactorCrater;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;

/**
 * The crater a reactor leaves when its shell does not hold (MOD-736), carved in a block of stone at a scale that
 * stays inside one rig.
 *
 * <p><b>Small numbers, passed in rather than configured.</b> The shipped wave power would blow straight out of an
 * 8×8×8 rig into its neighbours, and the crater knobs are globals every exploding reactor in the batch reads at the
 * same moment — so this scenario builds its {@link ReactorCrater} with its own spacing and power and touches no
 * knob. The terrain resistance cap is the shipped one: that is part of what is under test.
 */
public final class ReactorCraterScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(ReactorCraterScenarios::aCraterGrowsOnlyFromWhatItDestroyed,
								"reactor_crater_grows_only_from_what_it_destroyed")
						.ticks(100));

		private Roster() {}
	}

	private ReactorCraterScenarios() {}

	/** Middle of the stone block, three cells from every face of the rig. */
	private static final BlockPos EPICENTRE = new BlockPos(4, 4, 4);

	/**
	 * A wave goes off only in a cell an earlier blast emptied, so a refused wave stops the crater on that side.
	 *
	 * <p>The stand-in detonator refuses every blast centred east of the epicentre — what a land claim over the
	 * eastern half would do to a real {@code level.explode}. The primary blast reaches one cell; the waves must
	 * then carry the crater past that to the west, every wave that goes off must stand in a cell an earlier blast
	 * destroyed, and the claim must have refused some. What a wave allowed west of the line does next is not
	 * asserted by distance: it flies through the cells already emptied almost for free, so it can reach two cells
	 * east of its own centre without breaking the rule (the first run of this scenario asserted otherwise and was
	 * wrong). A pig in the first western cell checks the other promise of the cascade: its waves break the base,
	 * they do not hurt anyone.
	 */
	public static void aCraterGrowsOnlyFromWhatItDestroyed(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				for (int z = 0; z < 8; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
				}
			}
		}
		BlockPos centre = helper.absolutePos(EPICENTRE);
		ReactorCrater[] holder = new ReactorCrater[1];
		List<BlockPos> outOfThinAir = new ArrayList<>();
		int[] refused = new int[1];
		ReactorCrater.Detonator claimOverTheEast = (level, at, power, fire, calculator, loud) -> {
			if (at.getX() > centre.getX()) {
				refused[0]++;
				return;
			}
			// The primary blast is the one at the epicentre: no wave is ever planned there (its lattice node is 0).
			if (!at.equals(centre) && !holder[0].destroyed().contains(at)) {
				outOfThinAir.add(at.subtract(centre));
			}
			ReactorCrater.WORLD.detonate(level, at, power, fire, calculator, loud);
		};
		ReactorCrater crater = new ReactorCrater(helper.getLevel(), centre, 3, 2, 2, claimOverTheEast);
		holder[0] = crater;

		// Water next to the core: with its own resistance of 100 it swallowed the blast and a reactor sunk in a box of
		// water harmed nothing (owner playtest, MOD-736). It must go like the stone around it.
		helper.setBlock(EPICENTRE.above(), Blocks.WATER.defaultBlockState());
		// No entity damage even for the primary blast here: at power 3 vanilla hurts entities up to six blocks away,
		// two blocks past this rig into its neighbours, where it once ate an experience orb another scenario counted.
		crater.blast(centre, 3.0f, false, false);
		if (!crater.destroyed().contains(centre.above())) {
			helper.fail("the water beside the core stopped the blast: " + helper.getBlockState(EPICENTRE.above()));
		}
		if (!crater.destroyed().contains(centre)) {
			helper.fail("the primary blast did not even empty its own cell: " + crater.destroyed());
		}
		for (BlockPos at : crater.destroyed()) {
			if (Math.abs(at.getX() - centre.getX()) >= 2 || Math.abs(at.getY() - centre.getY()) >= 2
					|| Math.abs(at.getZ() - centre.getZ()) >= 2) {
				helper.fail("the primary blast alone reached offset " + at.subtract(centre)
						+ " — then the waves' growth below proves nothing; lower its power");
			}
		}

		BlockPos west = centre.west();
		if (!crater.destroyed().contains(west)) {
			helper.fail("the primary blast did not open the western cell the pig goes in");
		}
		Pig pig = helper.spawn(EntityTypes.PIG, EPICENTRE.west());
		float health = pig.getHealth();
		while (!crater.step(64)) {
			// The whole plan in one go: the scenario is about where waves go off, not about the tick budget.
		}

		boolean grewWest = crater.destroyed().stream().anyMatch(at -> at.getX() <= centre.getX() - 2);
		if (!grewWest) {
			helper.fail("the waves never carried the crater past the primary blast to the west: "
					+ crater.destroyed().size() + " cells destroyed");
		}
		if (!outOfThinAir.isEmpty()) {
			helper.fail("waves went off in cells no earlier blast had destroyed, at offsets " + outOfThinAir);
		}
		if (refused[0] == 0) {
			helper.fail("no wave was planned east of the line, so the claim was never put to the test");
		}
		if (pig.isRemoved() || pig.getHealth() < health) {
			helper.fail("a wave of the cascade hurt the pig standing in its crater (" + pig.getHealth() + " of "
					+ health + "); only the primary blast may damage entities");
		}
		helper.succeed();
	}
}
