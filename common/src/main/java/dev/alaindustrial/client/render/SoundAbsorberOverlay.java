package dev.alaindustrial.client.render;

import dev.alaindustrial.block.SoundAbsorberBlock;
import dev.alaindustrial.client.sound.SoundAbsorberZones;
import dev.alaindustrial.core.acoustic.AbsorberTier;
import dev.alaindustrial.core.acoustic.AcousticZones.Zone;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * What the sound absorber shows the player (MOD-804): with the wrench in hand, the quiet zone of every group
 * around them as a translucent box with a bright edge; looking at an absorber, a line beside the hotbar —
 * "group of N blocks — radius R, −X %". Both read the client's own registry ({@link SoundAbsorberZones}), so
 * what is drawn is exactly what muffles the sounds.
 *
 * <p>Drawn as frame gizmos through {@link FrameGizmos}, the path {@link PiezoPlateXray} and the network
 * overlay use on both lines; the HUD line is a {@code ClientContentManifest.HUD_LAYERS} entry.
 */
public final class SoundAbsorberOverlay {
	private static final int HOTBAR_HALF = 91;
	private static final int OFFHAND_SLOT = 29;
	private static final float EDGE_WIDTH = 2.0f;
	/** Padding orange, faint for the volume and strong for the edge. */
	private static final int FILL = 0x22E07A2C;
	private static final int EDGE = 0xE6E07A2C;

	private static @Nullable Component hint;

	private SoundAbsorberOverlay() {
	}

	/** Per-frame entry: called by each loader's world-render hook next to the network overlay. */
	public static void submitFrame(SubmitNodeCollector collector, CameraRenderState camera) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || mc.gui.screen() != null) {
			hint = null;
			return;
		}
		hint = focusedHint(mc, level);
		if (!PiezoPlateXray.holdsWrench(mc.player)) {
			return;
		}
		List<Zone> zones = SoundAbsorberZones.zones();
		if (zones.isEmpty()) {
			return;
		}
		FrameGizmos.emit((gizmos, alphaMultiplier) -> {
			for (Zone zone : zones) {
				int r = zone.tier().radius();
				AABB box = new AABB(zone.minX() - r, zone.minY() - r, zone.minZ() - r,
						zone.maxX() + 1 + r, zone.maxY() + 1 + r, zone.maxZ() + 1 + r);
				OverlayGeometry.addBoxOutline(gizmos, box, FILL, EDGE, EDGE_WIDTH);
			}
		}, false);
	}

	private static @Nullable Component focusedHint(Minecraft mc, ClientLevel level) {
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
				|| !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof SoundAbsorberBlock)) {
			return null;
		}
		Zone zone = SoundAbsorberZones.groupAt(hit.getBlockPos());
		if (zone == null) {
			return null;
		}
		String size = zone.size() > AbsorberTier.GROUP_CAP ? AbsorberTier.GROUP_CAP + "+"
				: Integer.toString(zone.size());
		return Component.translatable("hud.alaindustrial.sound_absorber.group", size, zone.tier().radius(),
				zone.tier().dampeningPercent());
	}

	/** HUD layer: the focused absorber's group, beside the hotbar like the piezo plate's line. */
	public static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Component text = hint;
		if (text == null || mc.gui.screen() != null || mc.gui.hud.isHidden()) {
			return;
		}
		int width = mc.font.width(text);
		int left = graphics.guiWidth() / 2 + HOTBAR_HALF + 6;
		if (mc.options.mainHand().get() == HumanoidArm.LEFT) {
			left += OFFHAND_SLOT;
		}
		int y = graphics.guiHeight() - 16;
		if (left + width > graphics.guiWidth() - 4) {
			left = Math.max(2, graphics.guiWidth() - width - 4);
			y -= 24;
		}
		graphics.fill(left - 4, y - 3, left + width + 4, y + 12, 0x99000000);
		graphics.text(mc.font, text, left, y, 0xFFF0E7CC);
	}
}
