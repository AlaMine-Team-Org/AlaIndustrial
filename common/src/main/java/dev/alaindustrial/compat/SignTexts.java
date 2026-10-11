package dev.alaindustrial.compat;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

/**
 * Writing the text of a sign, whose API differs between the Minecraft lines (MOD-799, ADR-036). Every line
 * has a twin of this class with the same signature; only the body differs. The energy test stand labels
 * its bays through it instead of naming its line's sign API.
 *
 * <p><b>This twin: Minecraft 26.2</b>, where {@code SignText} is an immutable value built line by line with
 * {@code setMessage(int, Component)} and {@code SignBlockEntity.setText(SignText, boolean)} takes the side as
 * {@code isFrontText} (javap of the 26.2 merged jar). (26.3 builds it through {@code SignText.Mutable} —
 * {@code SignText.EMPTY.asMutable().setLine(..).asImmutable()} — and names the side with
 * {@code SignTextSlot.FRONT}; neither exists on 26.2.)
 */
public final class SignTexts {

	private SignTexts() {
	}

	/**
	 * Replaces the front text of {@code sign} with {@code lines}, top to bottom; lines past the sign's four are
	 * dropped, missing ones stay empty. Colour, glow and the back side are the defaults of a fresh sign.
	 */
	public static void setFront(SignBlockEntity sign, List<Component> lines) {
		SignText text = new SignText();
		for (int i = 0; i < Math.min(SignText.LINES, lines.size()); i++) {
			text = text.setMessage(i, lines.get(i));
		}
		sign.setText(text, true);
	}
}
