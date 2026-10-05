package dev.alaindustrial.gametest.compat;

import net.minecraft.world.InteractionResult;

/**
 * Version facade (ADR-036) for reading what an item's {@code useOn} answered, so a gametest asks "did the arm
 * swing" and "was the click eaten silently" the same way on both lines. The signatures are the same on both
 * lines; the bodies are this line's.
 *
 * <p><b>This twin: Minecraft 26.2</b>, whose swing sources are {@code NONE}, {@code CLIENT} and {@code SERVER}
 * (26.3: {@code NONE}, {@code PREDICTED}, {@code SERVER_ONLY}); {@code InteractionResult.SUCCESS} is a
 * {@code Success} with {@code CLIENT}, {@code CONSUME} one with {@code NONE} (javap of both lines'
 * {@code minecraft-merged.jar}). 26.2's {@code ItemStack#useOn} does not attach the held stack to the result and
 * hands back the {@code SUCCESS}/{@code CONSUME} constants themselves, so this twin answers by identity with
 * them — the check the line's scenarios made before they called this facade, exactly as strict.
 */
public final class UseResults {

	private UseResults() {}

	/**
	 * Whether {@code result} is the SUCCESS an item's {@code useOn} returned: "handled, and the arm swings".
	 * 26.2: the {@code InteractionResult.SUCCESS} constant itself (a {@code Success} whose swing source is
	 * {@code CLIENT}).
	 */
	public static boolean isSwingSuccess(InteractionResult result) {
		return result == InteractionResult.SUCCESS;
	}

	/**
	 * Whether {@code result} is the CONSUME an item's {@code useOn} returned: "the click is eaten, no arm swing,
	 * no off-hand fallback". 26.2: the {@code InteractionResult.CONSUME} constant itself (a {@code Success}
	 * whose swing source is {@code NONE}).
	 */
	public static boolean isNoSwingConsume(InteractionResult result) {
		return result == InteractionResult.CONSUME;
	}
}
