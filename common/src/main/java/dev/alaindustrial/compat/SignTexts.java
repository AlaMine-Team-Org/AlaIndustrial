package dev.alaindustrial.compat;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;

/**
 * Writing the text of a sign, whose API differs between the Minecraft lines (MOD-799, ADR-036). Every line
 * has a twin of this class with the same signature; only the body differs. The energy test stand labels
 * its bays through it instead of naming its line's sign API.
 *
 * <p><b>This twin: Minecraft 26.3</b>, where {@code SignText} is built through {@code SignText.Mutable} —
 * {@code SignText.EMPTY.asMutable().setLine(..).asImmutable()} — and {@code SignBlockEntity.setText} names the
 * side with {@code SignTextSlot.FRONT} (javap of the 26.3 merged jar). (26.2 builds an immutable
 * {@code SignText} with {@code setMessage(int, Component)} and takes the side as {@code isFrontText}; neither
 * {@code SignText.Mutable} nor {@code SignTextSlot} exists there.)
 */
public final class SignTexts {

	private SignTexts() {
	}

	/**
	 * Replaces the front text of {@code sign} with {@code lines}, top to bottom; lines past the sign's four are
	 * dropped, missing ones stay empty. Colour, glow and the back side are the defaults of a fresh sign.
	 */
	public static void setFront(SignBlockEntity sign, List<Component> lines) {
		SignText.Mutable text = SignText.EMPTY.asMutable();
		for (int i = 0; i < Math.min(SignText.LINES, lines.size()); i++) {
			text.setLine(i, lines.get(i));
		}
		sign.setText(text.asImmutable(), SignTextSlot.FRONT);
	}
}
