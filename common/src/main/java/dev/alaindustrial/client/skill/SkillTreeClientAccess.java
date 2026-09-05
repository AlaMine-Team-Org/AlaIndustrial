package dev.alaindustrial.client.skill;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Client-only entry point for opening the skill wheel, called from
 * {@link dev.alaindustrial.block.WorkstationBlock#useWithoutItem} inside a {@code level.isClientSide()}
 * guard — the same indirection {@code GuideBookClientAccess} uses, for the same two reasons.
 *
 * <p>It keeps the block class free of any {@code Minecraft}/{@code Screen} reference at its top level,
 * so a dedicated server never classloads the screen; and it lives under {@code client.skill} because
 * the ArchUnit rule {@code clientTypesStayInsideClientPackages} forbids {@code net.minecraft.client}
 * outside {@code dev.alaindustrial.client..}.
 *
 * <p>The station's position is carried into the screen because every purchase packet names it: the
 * server re-reads the block from the world and re-checks the player is still near it.
 */
public final class SkillTreeClientAccess {

	private SkillTreeClientAccess() {
	}

	/** Open the wheel for the station at {@code station}. */
	public static void open(BlockPos station) {
		Minecraft.getInstance().setScreenAndShow(new SkillTreeScreen(station.immutable()));
	}
}
