package dev.alaindustrial.client.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.Config;
import dev.alaindustrial.KnobSnapshot;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.core.machine.MachineRates;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * L1 for the cost a recipe card prints (MOD-743): the electric furnace's default operation is priced from
 * the server's balance when the card is drawn, a recipe that states its energy prints it as written, and
 * only the furnace's family turns a zero into the furnace's cost.
 */
class RecipeViewerCostTest {

	@AfterEach
	void forgetTheServer() {
		ServerBalance.reset();
	}

	/**
	 * @implements MOD-743-COST — a mirrored vanilla smelt is priced from the server's snapshot: the EU the
	 *     server's furnace ticks away and the server's furnace duration, not the player's own file
	 * @covers MOD-743
	 */
	@Test
	void furnaceDefaultFollowsTheServerSnapshot() {
		int duration = Config.electricFurnaceDuration + 40;
		int rate = Config.machineEuPerTick + 2;
		float speed = Config.globalMachineSpeedMultiplier * 2.0f;
		int serverEu = MachineRates.vanillaSmeltEu(duration, rate, speed);
		int localEu = MachineRates.vanillaSmeltEu(Config.electricFurnaceDuration, Config.machineEuPerTick,
				Config.globalMachineSpeedMultiplier);
		// Otherwise the assertions below could not tell the server's numbers from the local ones.
		assertNotEquals(localEu, serverEu, "the server's furnace cost must differ from the local one");
		assertNotEquals(Config.electricFurnaceDuration, duration);

		assertTrue(ServerBalance.receive(KnobSnapshot.of(Map.of("electricFurnaceDuration", duration,
				"machineEuPerTick", rate, "globalMachineSpeedMultiplier", speed)).encode()));

		RecipeViewerCost cost = RecipeViewerCost.resolve(true, 0, rate);
		assertEquals(serverEu, cost.energy(), "EU of a vanilla smelt on the server's balance");
		assertEquals(duration, cost.ticks(), "base time of a vanilla smelt is the server's furnace duration");
		assertEquals(RecipeViewerLayout.costLabel(serverEu, duration), cost.label());
		assertEquals(cost, RecipeViewerCost.electricFurnaceDefault());
	}

	/**
	 * @implements MOD-743-FALLBACK — no snapshot (an older server, or single player before one arrives)
	 *     prices the furnace's default operation from the local numbers
	 * @covers MOD-743
	 */
	@Test
	void noSnapshotShowsTheLocalNumbers() {
		assertFalse(ServerBalance.fromServer());
		RecipeViewerCost cost = RecipeViewerCost.resolve(true, 0, Config.machineEuPerTick);
		assertEquals(MachineRates.vanillaSmeltEu(Config.electricFurnaceDuration, Config.machineEuPerTick,
				Config.globalMachineSpeedMultiplier), cost.energy());
		assertEquals(Config.electricFurnaceDuration, cost.ticks());
	}

	/**
	 * @implements MOD-743-STATED — a recipe that states its energy prints it as written, timed at its
	 *     family's rate, in the furnace's family as in any other
	 * @covers MOD-743
	 */
	@Test
	void recipeEnergyIsShownAsWritten() {
		// A server snapshot unlike the local file: a stated cost must not be swapped for the furnace's.
		assertTrue(ServerBalance.receive(KnobSnapshot.of(Map.of("electricFurnaceDuration",
				Config.electricFurnaceDuration + 40, "machineEuPerTick", Config.machineEuPerTick + 2)).encode()));

		assertEquals(new RecipeViewerCost(200, 50), RecipeViewerCost.resolve(true, 200, 4));
		assertEquals(new RecipeViewerCost(2400, 300), RecipeViewerCost.resolve(false, 2400, 8));
		assertEquals("200 EU · 2.5 s", RecipeViewerCost.resolve(true, 200, 4).label());
		// A rate of zero (a broken snapshot) must not divide by zero, and a cost below one tick still shows one.
		assertEquals(new RecipeViewerCost(200, 200), RecipeViewerCost.of(200, 0));
		assertEquals(new RecipeViewerCost(1, 1), RecipeViewerCost.of(1, 8));
	}

	/**
	 * @implements MOD-743-STATED — "energy 0 means the machine's default" holds for the electric furnace
	 *     only: a zero in another family is not priced as a vanilla smelt
	 * @covers MOD-743
	 */
	@Test
	void onlyTheFurnaceFamilyHasADefaultCost() {
		assertEquals(new RecipeViewerCost(0, 1), RecipeViewerCost.resolve(false, 0, 8));
		assertEquals(new RecipeViewerCost(-5, 1), RecipeViewerCost.resolve(false, -5, 2));
		assertEquals(RecipeViewerCost.electricFurnaceDefault(), RecipeViewerCost.resolve(true, -5, 2));
	}

	/** The multipliers the review named (MOD-743), on the default furnace and on a retuned one. */
	static List<Arguments> serverBalances() {
		List<Arguments> grid = new ArrayList<>();
		for (int[] furnace : new int[][] {{100, 2}, {140, 3}}) {
			for (float speed : new float[] {0.25f, 0.3f, 1.0f, 3.0f}) {
				grid.add(Arguments.of(furnace[0], furnace[1], speed));
			}
		}
		return grid;
	}

	private static void serverRuns(int duration, int rate, float speed) {
		assertTrue(ServerBalance.receive(KnobSnapshot.of(Map.of("electricFurnaceDuration", duration,
				"machineEuPerTick", rate, "globalMachineSpeedMultiplier", speed)).encode()));
	}

	/**
	 * @implements MOD-743-COST — the vanilla-smelt card prints the same EU and time in JEI (the mirror's
	 *     own zero) and in REI (the price the server's filler states), and both are what the machine does:
	 *     the EU the furnace ticks away at that multiplier, and the operation's length at a multiplier of 1
	 * @covers MOD-743
	 */
	@ParameterizedTest(name = "duration {0}, rate {1}, multiplier {2}")
	@MethodSource("serverBalances")
	void vanillaSmeltCardAgreesInBothViewersAndWithTheMachine(int duration, int rate, float speed) {
		serverRuns(duration, rate, speed);

		// The machine: the vanilla fallback states no energy (0), so it runs its default duration, scaled
		// by the multiplier, at its scaled draw (AbstractProcessingMachineBlockEntity, no chips).
		int machineBase = MachineRates.baseDuration(0, rate, duration);
		int machineEu = MachineRates.duration(machineBase, speed) * MachineRates.euPerTick(rate, speed);

		// JEI: the mirror's own FURNACE_DEFAULT_ENERGY (0), resolved when the card is drawn.
		RecipeViewerCost jei = RecipeViewerCost.resolve(true, 0, rate);
		// REI: the server's filler states the price from its own Config (AlaReiCommonPlugin.vanillaSmeltEu).
		int stated = MachineRates.vanillaSmeltEu(duration, rate, speed);
		RecipeViewerCost rei = RecipeViewerCost.resolveServerStated(true, stated, rate);

		assertEquals(machineEu, jei.energy(), "JEI: EU the furnace really spends on one vanilla smelt");
		assertEquals(machineBase, jei.ticks(), "JEI: the smelt's length at a multiplier of 1");
		assertEquals(jei, rei, "REI must print what JEI prints for the same mirror");
	}

	/**
	 * @implements MOD-743-STATED — a mod recipe's card prints the same EU and time in both viewers, and its
	 *     time is the operation's length on the machine at a multiplier of 1
	 * @covers MOD-743
	 */
	@ParameterizedTest(name = "duration {0}, rate {1}, multiplier {2}")
	@MethodSource("serverBalances")
	void modRecipeCardAgreesInBothViewersAndWithTheMachine(int duration, int rate, float speed) {
		serverRuns(duration, rate, speed);
		int mirrorPrice = ServerBalance.electricFurnaceVanillaSmeltEu();
		for (int energy : new int[] {200, 260, 1000, 2400}) {
			RecipeViewerCost jei = RecipeViewerCost.resolve(true, energy, rate);
			assertEquals(energy, jei.energy());
			assertEquals(MachineRates.baseDuration(energy, rate, duration), jei.ticks(),
					energy + " EU: the machine's operation length at a multiplier of 1");
			if (energy != mirrorPrice) { // the one EU REI reads as a mirror: see the next test
				assertEquals(jei, RecipeViewerCost.resolveServerStated(true, energy, rate), energy + " EU in REI");
			}
		}
	}

	/**
	 * @implements MOD-743-STATED — REI's known limit, pinned where it is harmless: a furnace recipe that
	 *     costs exactly the vanilla-smelt price is shown as a vanilla smelt, which at a multiplier of 1 prints
	 *     the very same EU and time
	 * @covers MOD-743
	 */
	@Test
	void aRecipePricedLikeTheMirrorPrintsTheSameAtMultiplierOne() {
		serverRuns(100, 2, 1.0f);
		int price = ServerBalance.electricFurnaceVanillaSmeltEu();
		assertEquals(RecipeViewerCost.of(price, 2), RecipeViewerCost.resolveServerStated(true, price, 2));
		// Outside the furnace's family the price means nothing.
		assertEquals(RecipeViewerCost.of(price, 8), RecipeViewerCost.resolveServerStated(false, price, 8));
	}
}
