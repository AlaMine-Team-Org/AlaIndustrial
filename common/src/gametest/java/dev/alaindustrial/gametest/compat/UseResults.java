package dev.alaindustrial.gametest.compat;

import net.minecraft.world.InteractionResult;

/**
 * Version facade (ADR-036) for reading what an item's {@code useOn} answered, so a gametest asks "did the arm
 * swing" and "was the click eaten silently" the same way on both lines. The signatures are the same on both
 * lines; the bodies are this line's.
 *
 * <p><b>This twin: Minecraft 26.3.</b> {@code ItemStack#useOn} (and the {@code gameMode.useItemOn} chain that
 * calls it) attaches the post-interaction held stack to every {@code InteractionResult.Success}
 * ({@code heldItemTransformedTo}), so record equality with the {@code SUCCESS}/{@code CONSUME} constants no
 * longer holds on that path — the swing source is the part that carries the meaning. 26.3's swing sources are
 * {@code NONE}, {@code PREDICTED} and {@code SERVER_ONLY}; 26.2's are {@code NONE}, {@code CLIENT} and
 * {@code SERVER} (javap of both lines' {@code minecraft-merged.jar}).
 */
public final class UseResults {

	private UseResults() {}

	/**
	 * Whether {@code result} is the SUCCESS an item's {@code useOn} returned: "handled, and the arm swings".
	 * 26.3: a {@code Success} whose swing source is {@code PREDICTED}.
	 */
	public static boolean isSwingSuccess(InteractionResult result) {
		return result instanceof InteractionResult.Success success
				&& success.swingSource() == InteractionResult.SwingSource.PREDICTED;
	}

	/**
	 * Whether {@code result} is the CONSUME an item's {@code useOn} returned: "the click is eaten, no arm swing,
	 * no off-hand fallback". 26.3: a {@code Success} whose swing source is {@code NONE}.
	 */
	public static boolean isNoSwingConsume(InteractionResult result) {
		return result instanceof InteractionResult.Success success
				&& success.swingSource() == InteractionResult.SwingSource.NONE;
	}
}
