package dev.alaindustrial.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.KnobSnapshot;
import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Every machine recipe family's draw per tick follows the server's snapshot AFTER {@link ModRecipes} has
 * been initialised (MOD-743).
 *
 * <p>The recipe viewers divide a card's EU by {@link MachineRecipeFamily#euPerTick()}, and the server's
 * numbers reach the client in {@link ServerBalance} only after the classes are long loaded — and again on
 * every {@code /ala config reload}. A rate read once, in {@code <clinit>} or in a family's constructor, is
 * the local file forever; neither {@code ArchitectureRules.recipeDataReadsNoBalanceKnob} (it sees only
 * {@code Config} reads, and this one would be a {@code ServerBalance} read) nor the gametest
 * {@code tcRecie13} (it checks the mirrors' energy, not the rates) can tell it from a live read.
 *
 * <p>Runs on the NeoForge L1.5 lane, not L1: {@link ModRecipes} names recipe and block classes, which need
 * the Minecraft jar the MC-free L1 lane does not have. {@link ServerBalance} is global; this lane runs its
 * tests one at a time, and the snapshot is reset after the test.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class RecipeFamilyRateFollowsServerBalanceTest {

	/** A rate no knob defaults to, so a family still on the local number cannot pass by accident. */
	private static final int SERVER_RATE = 997;

	@AfterEach
	void forgetTheServer() {
		ServerBalance.reset();
	}

	/**
	 * @implements MOD-743-RATE — a family's rate is read from {@code ServerBalance} each time it is asked,
	 *     so a snapshot received after the class initialised (a join, a config reload) is the one shown
	 * @covers MOD-743
	 */
	@Test
	void everyFamilyRateFollowsASnapshotReceivedAfterInitialisation() {
		List<MachineRecipeFamily<?, ?, ?>> families = new ArrayList<>();
		for (RecipeFamily<?> family : ModRecipes.families()) {
			if (family instanceof MachineRecipeFamily<?, ?, ?> machine) {
				families.add(machine);
				// Initialised and read on the local numbers first, as the client does before it joins.
				assertTrue(machine.euPerTick() != SERVER_RATE, machine.id() + " already reads " + SERVER_RATE);
			}
		}
		assertFalse(families.isEmpty(), "ModRecipes declares no machine recipe family");

		// Every rate knob a family reads, set to the same value: a family on a knob not listed here fails
		// too, which is the prompt to list it.
		assertTrue(ServerBalance.receive(KnobSnapshot.of(Map.of(
				"machineEuPerTick", SERVER_RATE,
				"thermalCentrifugeEuPerTick", SERVER_RATE,
				"incubatorEuPerTick", SERVER_RATE,
				"alloySmelterEuPerTick", SERVER_RATE)).encode()));

		for (MachineRecipeFamily<?, ?, ?> family : families) {
			assertEquals(SERVER_RATE, family.euPerTick(), family.id() + ": the rate the viewer divides by");
			assertEquals(1, family.ticksFor(SERVER_RATE), family.id() + ": the time printed for its EU");
		}
	}
}
