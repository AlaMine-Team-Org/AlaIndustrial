package dev.alaindustrial.gametest;

import dev.alaindustrial.client.ClientContentManifest;
import dev.alaindustrial.compat.client.Keyboard;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;

/**
 * L3 characterization of the mod's default keys (MOD-703, batch 0): every mapping of
 * {@link ClientContentManifest#KEY_MAPPINGS} defaults to the same physical key on both Minecraft lines.
 *
 * <p><b>Why the key NAME.</b> The default is declared as {@code InputConstants.KEY_*} in the input type
 * {@link Keyboard#keyMappingType()} names, and both differ per line: 26.3 moved input to SDL3, so {@code KEY_H}
 * is the scancode 11 in type {@code KEYBOARD} there and the GLFW key 72 in type {@code KEYSYM} on 26.2. What
 * both lines share is the name each registers for that value — {@code key.keyboard.h} (javap of both lines'
 * {@code InputConstants$Type.<clinit>}: {@code addKey(…, "key.keyboard.h", 11)} on 26.3, {@code 72} on 26.2),
 * which is also what {@code options.txt} stores. So the reference is the name, one list for both lines, and the
 * type is checked against the line's keyboard type.
 *
 * <p>A client class lane because the mappings are client classes; it takes no frame. {@link #check} is plain
 * Java over the mappings. <b>Updated only by hand</b>, in a commit that names the behaviour change (ADR-032).
 */
public class KeyMappingDefaultsClientGameTest implements FabricClientGameTest {

	/** The reference: {@code <mapping name> <default key name>}, in registration order. */
	public static final List<String> EXPECTED = List.of(
			"key.alaindustrial.toggle_energy_hud key.keyboard.h",
			"key.alaindustrial.toggle_drill_hud key.keyboard.j",
			"key.alaindustrial.open_profile key.keyboard.k",
			"key.alaindustrial.toggle_step_assist key.keyboard.g",
			"key.alaindustrial.toggle_drill_column key.keyboard.l");

	/** {@code null} when {@code mappings} match {@link #EXPECTED} in the line's keyboard type, else what differs. */
	public static String check(List<KeyMapping> mappings) {
		List<String> actual = new ArrayList<>();
		List<String> wrongType = new ArrayList<>();
		for (KeyMapping mapping : mappings) {
			actual.add(mapping.getName() + " " + mapping.getDefaultKey().getName());
			if (mapping.getDefaultKey().getType() != Keyboard.keyMappingType()) {
				wrongType.add(mapping.getName() + " is " + mapping.getDefaultKey().getType());
			}
		}
		if (actual.equals(EXPECTED) && wrongType.isEmpty()) {
			return null;
		}
		return "MOD-703: default keys differ — expected " + EXPECTED + ", got " + actual
				+ (wrongType.isEmpty() ? "" : "; not in the line's keyboard type " + Keyboard.keyMappingType()
						+ ": " + wrongType);
	}

	/**
	 * @implements MOD-703-KM01 — the five mod key mappings default to G, H, J, K and L of the line's keyboard
	 *     type, in registration order
	 */
	@Override
	public void runTest(ClientGameTestContext context) {
		String problem = context.computeOnClient(client -> check(ClientContentManifest.KEY_MAPPINGS));
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
