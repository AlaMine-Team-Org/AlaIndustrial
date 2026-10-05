package dev.alaindustrial.gametest.compat;

import net.minecraft.core.Direction;

/**
 * Version facade (ADR-036) for what a shovel does to a lit campfire on this Minecraft line, so the one face
 * matrix of {@code ElectricShovelScenarios} can pin that cell with a literal on both lines instead of trusting
 * the vanilla oracle alone. The signature is the same on both lines; the body is this line's.
 *
 * <p>The answer differs between the lines because the game moved dousing out of the shovel and into the
 * campfire, not because of the mod: the mod's shovels repeat a vanilla diamond shovel of their own line. The
 * owner decided on 2026-10-05 (MOD-744) to keep each line as its vanilla does and to pin the outcome here, so a
 * change of the game on either line turns a gametest red instead of passing silently.
 *
 * <p><b>This twin: Minecraft 26.2.</b> The shovel douses: {@code ShovelItem.useOn} answers {@code PASS} for a
 * click on {@code Direction.DOWN} as its first check (javap: {@code getClickedFace}, {@code if_acmpeq
 * Direction.DOWN} to the {@code PASS} return), and only then puts out a lit {@code CampfireBlock}; the mod's
 * shovels reach that method through {@code RightClickTransform.SHOVEL}, which hands the context to
 * {@code Items.DIAMOND_SHOVEL.useOn}. On NeoForge 26.2.0.67 the patched method keeps the same {@code DOWN} check
 * before it asks {@code getToolModifiedState(…, ItemAbilities.SHOVEL_DOUSE, …)}. {@code CampfireBlock.useItemOn}
 * knows no shovel on this line. Sneaking does not matter: a sneaking click skips only the block, whose
 * {@code useItemOn} would not douse anyway, and {@code ShovelItem.useOn} never reads the sneak (javap).
 */
public final class LineShovelFacts {

	private LineShovelFacts() {}

	/**
	 * Whether right-clicking a lit campfire from {@code face} with a shovel that douses — a vanilla diamond
	 * shovel, or the mod's base electric shovel (charged or flat) — leaves it unlit on this line, with the
	 * player {@code sneaking} or not. 26.2: from any face but {@code DOWN}, sneaking or not.
	 */
	public static boolean dousesLitCampfire(Direction face, boolean sneaking) {
		return face != Direction.DOWN;
	}
}
