package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.client.skill.SkillTreeScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The skill wheel, photographed (MOD-483).
 *
 * <p>The screen is a plain {@code Screen} rather than a menu — the tree has no container and no slots
 * — so it is outside {@code ScreensClientGameTest}, which walks {@code ContentManifest.MENUS}, and
 * outside {@code menu_shot_parity_check.py} with it. Without a stand of its own the mod's largest and
 * most hand-tuned screen would be the only one with no frame at all, which is exactly the coverage gap
 * the whole L3 lane exists to close.
 *
 * <p>Two frames, because the board is the part that can break: the first is the screen as it opens,
 * the second after it has been zoomed in. R-GUI-03 is asked of both — nothing clipped, no caption over
 * another element — and the second one asks it of a state the layout only reaches at runtime, where
 * the branch captions stay pinned in their corners while the wheel under them grows.
 *
 * <p>The screen is pushed straight in, the way {@code AdvancementScreenStand} pushes its own: opening
 * it through the block would need an assembled, powered station in the frame, and the frame is about
 * the screen.
 */
public final class SkillTreeGuiStand {

	private SkillTreeGuiStand() {
	}

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");

	/** Any position: the screen only carries it to name the station in the purchase packet. */
	private static final BlockPos STATION = new BlockPos(0, 100, 0);

	/** Scroll notches for the zoomed frame — enough to be unmistakably a different scale. */
	private static final int ZOOM_NOTCHES = 4;

	public static void shootSkillTree(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.setScreenAndShow(new SkillTreeScreen(STATION)));
		awaitWheel(context);
		LOG.info("[GUITEST][R-GUI-03] gui_skill_tree_wheel -> {}",
				takeCleanScreenshot(context, "gui_skill_tree_wheel").toAbsolutePath());

		// Zoom through the screen's own handler rather than by writing its field: what is being
		// photographed is the state the player's scroll wheel produces, clamps included.
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof SkillTreeScreen wheel) {
				for (int i = 0; i < ZOOM_NOTCHES; i++) {
					wheel.mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2.0,
							mc.getWindow().getGuiScaledHeight() / 2.0, 0.0, 1.0);
				}
			}
		});
		context.waitTicks(1);
		LOG.info("[GUITEST][R-GUI-03] gui_skill_tree_zoomed -> {}",
				takeCleanScreenshot(context, "gui_skill_tree_zoomed").toAbsolutePath());

		context.runOnClient(mc -> mc.setScreenAndShow(null));
		context.waitTicks(3);
	}

	/** Wait for the screen to exist AND to have laid itself out — the frame is meaningless before that. */
	private static void awaitWheel(ClientGameTestContext context) {
		context.waitFor(mc -> mc.gui.screen() instanceof SkillTreeScreen);
		context.waitTicks(1);
	}
}
