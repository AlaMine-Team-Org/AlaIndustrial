package dev.alaindustrial.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The format of a registry-snapshot block line (MOD-741): token order, the rule that a conditional token is
 * printed exactly when it differs from the vanilla default, and the number format the reference has always
 * used. {@link RegistrySnapshotBlockLine} holds no Minecraft type, so this runs on the L1 lane; what the
 * scenario READS is checked by the scenario itself against the reviewed reference.
 */
class RegistrySnapshotBlockLineTest {

	/** A machine with every conditional property at its vanilla default. */
	private static RegistrySnapshotBlockLine machine() {
		return new RegistrySnapshotBlockLine("generator", 3.0f, true, 0, 0, "PUSH_PULL", true, "METAL", 6.0f, "NONE",
				0.6f, 1.0f, 1.0f, 0.0f, false, false, false, "alaindustrial:blocks/generator");
	}

	private static RegistrySnapshotBlockLine with(int lightMax, float friction, float speed, float jump, float bounce,
			boolean lavaIgnites, boolean replaceable, boolean liquid) {
		RegistrySnapshotBlockLine m = machine();
		return new RegistrySnapshotBlockLine(m.id(), m.destroy(), m.tool(), m.light(), lightMax, m.push(),
				m.occludes(), m.sound(), m.blast(), m.map(), friction, speed, jump, bounce, lavaIgnites, replaceable,
				liquid, m.loot());
	}

	@Test
	void defaultsPrintOnlyTheTokensEveryLineCarries() {
		assertEquals("block generator destroy=3.0 tool=yes light=0 push=PUSH_PULL occludes=yes sound=METAL blast=6.0"
				+ " map=NONE loot=alaindustrial:blocks/generator", machine().toLine());
	}

	@Test
	void everyConditionalTokenAppearsInItsFixedOrderBeforeLoot() {
		String line = with(13, 0.98f, 0.4f, 0.5f, 0.8f, true, true, true).toLine();
		assertEquals("block generator destroy=3.0 tool=yes light=0 push=PUSH_PULL occludes=yes sound=METAL blast=6.0"
				+ " map=NONE lightmax=13 friction=0.98 speed=0.4 jump=0.5 bounce=0.8 lava_ignites replaceable liquid"
				+ " loot=alaindustrial:blocks/generator", line);
	}

	@Test
	void eachConditionalTokenIsPrintedAloneWhenOnlyItDiffers() {
		List<String> expected = List.of(" lightmax=13 ", " friction=0.98 ", " speed=0.4 ", " jump=0.5 ", " bounce=0.8 ",
				" lava_ignites ", " replaceable ", " liquid ");
		List<RegistrySnapshotBlockLine> lines = List.of(
				with(13, 0.6f, 1.0f, 1.0f, 0.0f, false, false, false),
				with(0, 0.98f, 1.0f, 1.0f, 0.0f, false, false, false),
				with(0, 0.6f, 0.4f, 1.0f, 0.0f, false, false, false),
				with(0, 0.6f, 1.0f, 0.5f, 0.0f, false, false, false),
				with(0, 0.6f, 1.0f, 1.0f, 0.8f, false, false, false),
				with(0, 0.6f, 1.0f, 1.0f, 0.0f, true, false, false),
				with(0, 0.6f, 1.0f, 1.0f, 0.0f, false, true, false),
				with(0, 0.6f, 1.0f, 1.0f, 0.0f, false, false, true));
		String base = machine().toLine();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).toLine();
			String token = expected.get(i);
			assertTrue(line.contains(token), "expected" + token + "in " + line);
			assertEquals(base, line.replace(token, " "), "only" + token + "may differ from the default line");
		}
	}

	@Test
	void lightMaxIsPrintedOnlyAboveTheDefaultStatesLight() {
		RegistrySnapshotBlockLine torch = new RegistrySnapshotBlockLine("torch", 0.0f, false, 14, 14, "POPPED", false,
				"WOOD", 0.0f, "NONE", 0.6f, 1.0f, 1.0f, 0.0f, false, false, false, "none");
		assertFalse(torch.toLine().contains("lightmax="), torch.toLine());
		assertFalse(with(0, 0.6f, 1.0f, 1.0f, 0.0f, false, false, false).toLine().contains("lightmax="));
		assertTrue(with(1, 0.6f, 1.0f, 1.0f, 0.0f, false, false, false).toLine().contains(" lightmax=1 "));
	}

	@Test
	void numbersUseTheReferencesFloatAndIntFormat() {
		RegistrySnapshotBlockLine chest = new RegistrySnapshotBlockLine("diamond_chest", 3.0f, true, 0, 0, "PUSH_PULL",
				false, "METAL", 1200.0f, "NONE", 0.6f, 1.0f, 1.0f, 0.0f, false, false, false, "none");
		assertTrue(chest.toLine().contains(" destroy=3.0 "), chest.toLine());
		assertTrue(chest.toLine().contains(" blast=1200.0 "), chest.toLine());
		RegistrySnapshotBlockLine cable = new RegistrySnapshotBlockLine("copper_cable", 0.2f, true, 0, 0, "PUSH_PULL",
				false, "COPPER", 0.5f, "NONE", 0.6f, 1.0f, 1.0f, 0.0f, false, false, false, "none");
		assertTrue(cable.toLine().contains(" destroy=0.2 "), cable.toLine());
		assertTrue(cable.toLine().contains(" blast=0.5 "), cable.toLine());
	}

	@Test
	void toolIsYesOrNo() {
		RegistrySnapshotBlockLine m = machine();
		RegistrySnapshotBlockLine byHand = new RegistrySnapshotBlockLine(m.id(), m.destroy(), false, m.light(),
				m.lightMax(), m.push(), m.occludes(), m.sound(), m.blast(), m.map(), m.friction(), m.speed(), m.jump(),
				m.bounce(), m.lavaIgnites(), m.replaceable(), m.liquid(), m.loot());
		assertTrue(m.toLine().contains(" tool=yes "));
		assertTrue(byHand.toLine().contains(" tool=no "));
	}

	@Test
	void negativeZeroIsADifferentValue() {
		assertTrue(with(0, 0.6f, 1.0f, 1.0f, -0.0f, false, false, false).toLine().contains(" bounce=-0.0 "));
	}
}
