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
 * <p><b>This twin: Minecraft 26.3.</b> {@code ShovelItem} is gone (javap: no such class in
 * {@code minecraft-merged.jar}); {@code CampfireBlock.useItemOn} douses a lit campfire when the held stack is in
 * {@code #minecraft:douses_campfires} (javap: {@code ItemStack.is(ItemTags.DOUSES_CAMPFIRES)}, no
 * {@code Direction} read anywhere in the method), and that tag is {@code ["#minecraft:shovels"]}, which the mod's
 * shovels join. On NeoForge 26.3.0.7-beta the patched method asks
 * {@code canPerformAction(ItemAbilities.SHOVEL_DOUSE)} instead, whose default answers by the same tag — the same
 * outcome. So the face does not matter; what matters is whether the block is asked at all:
 * {@code ServerPlayerGameMode.useItemOn} skips {@code BlockState.useItemOn} while the player
 * {@code isSecondaryUseActive()} (sneaking) with something in hand, and the shovel's own conversion, the
 * {@code shovel} block transformer, does not know the campfire.
 */
public final class LineShovelFacts {

	private LineShovelFacts() {}

	/**
	 * Whether right-clicking a lit campfire from {@code face} with a shovel that douses — a vanilla diamond
	 * shovel, or the mod's base electric shovel (charged or flat) — leaves it unlit on this line, with the
	 * player {@code sneaking} or not. 26.3: the campfire douses from any face, but a sneaking click never
	 * reaches the campfire.
	 */
	public static boolean dousesLitCampfire(Direction face, boolean sneaking) {
		return !sneaking;
	}
}
