package dev.alaindustrial.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-485 — guard for the `#minecraft:tick` / `#minecraft:load` override in the `:neoforge:test` lane.
 *
 * <p><b>What is overridden and why.</b> The ephemeral server from
 * {@link EphemeralTestServerProvider} has NO level — a documented property of the lane. But
 * `MinecraftServer.tickServer` calls `ServerFunctionManager.tick()` every tick, which sets out to
 * run the functions of the `#minecraft:tick` tag and fails before the first command:
 * `NullPointerException: … CommandSourceStack.getLevel() is null`. The tag holds exactly one entry
 * and it is ours (`alaindustrial_veinminer:register`), so only our lane paid for it: measured at
 * **3652** failures per run and **8.7 MB** in `output-events.bin` — very nearly the worker's entire
 * output. That output travels the "test worker → daemon" channel whose breakage is reported as
 * `java.io.EOFException` (MOD-484).
 *
 * <p>Hence `neoforge/src/test/resources/data/minecraft/tags/function/` holds a `tick.json` and a
 * `load.json` with an empty list. ONLY the `:neoforge:test` lane picks that resource root up (it
 * appears in `-Dfml.modFolders` as `alaindustrial%%…/resources/test`; `runClient` and
 * `runGameTests` never see it), so in a real world the Ore Vein Miner ore registration works exactly
 * as it did.
 *
 * <p><b>Why this needs guarding.</b> The override rests on the `resources/test` root winning a path
 * collision inside ONE mod's union file system. MOD-322 showed that this mechanism is SILENT: back
 * then a fixture shadowed the real file just as quietly, and the test went green having checked
 * nothing. If the direction ever changes (roots move, a different FML version), the flood of NPEs
 * comes back just as quietly — this is where it must go red.
 *
 * <p>The second test is the negative control: the functions themselves must still be there. Without
 * it the first test would also pass if the `alaindustrial_veinminer` datapack were simply deleted —
 * that is, it would be checking something other than what it was written for.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class JUnitLaneFunctionTagOverrideTest {

	private static final Identifier TICK = Identifier.fromNamespaceAndPath("minecraft", "tick");
	private static final Identifier LOAD = Identifier.fromNamespaceAndPath("minecraft", "load");
	private static final Identifier REGISTER =
			Identifier.fromNamespaceAndPath("alaindustrial_veinminer", "register");
	private static final Identifier SETUP =
			Identifier.fromNamespaceAndPath("alaindustrial_veinminer", "setup");

	@Test
	void tickAndLoadTagsAreEmptyInThisLane(MinecraftServer server) {
		assertEquals(List.of(), server.getFunctions().getTag(TICK),
				"#minecraft:tick must be empty in the :neoforge:test lane — otherwise the level-less "
						+ "server starts failing with a NullPointerException every tick again (MOD-485). "
						+ "Check that neoforge/src/test/resources/data/minecraft/tags/function/tick.json "
						+ "still overrides the file from common/src/main/resources.");
		assertEquals(List.of(), server.getFunctions().getTag(LOAD),
				"#minecraft:load must be empty in the :neoforge:test lane for the same reason (MOD-485).");
	}

	@Test
	void theFunctionsThemselvesAreStillShipped(MinecraftServer server) {
		assertTrue(server.getFunctions().get(REGISTER).isPresent(),
				"the TAG is what gets overridden, not the datapack itself: the function "
						+ "alaindustrial_veinminer:register must stay in the function library, or the "
						+ "first test goes green for nothing.");
		assertTrue(server.getFunctions().get(SETUP).isPresent(),
				"same for alaindustrial_veinminer:setup.");
	}
}
