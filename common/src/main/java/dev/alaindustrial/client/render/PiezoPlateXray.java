package dev.alaindustrial.client.render;

import dev.alaindustrial.block.entity.PiezoPlateBlockEntity;
import dev.alaindustrial.block.entity.PiezoPlateGroup;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The wrench's x-ray for piezo plates (MOD-764). Camouflaged plates are nearly invisible on purpose, so while
 * the player holds the wrench the plates around them light up through walls, coloured by what their group is
 * doing: green — an outlet and room to store; amber — no outlet, presses are kept but go nowhere; red — the
 * group is full and new presses are lost. A line points from each outlet to its receiver, straight down
 * through the floor block when the receiver is hidden under it. Looking at a plate prints its group in a line
 * beside the hotbar.
 *
 * <p>Drawn as frame gizmos with always-on-top ({@link FrameGizmos}) — the path the network overlay uses on both
 * lines — so it needs no mixin, no depth target of its own and no version seam. All it reads is the plates'
 * synced group view ({@link PiezoPlateBlockEntity#viewStatus()} and friends).
 */
public final class PiezoPlateXray {
	private static final int RADIUS = 8;
	private static final int HEIGHT = 4;
	private static final int MAX_PLATES = 160;
	private static final int SCAN_EVERY_TICKS = 5;
	private static final int HOTBAR_HALF = 91;
	private static final int OFFHAND_SLOT = 29;
	private static final float EDGE_WIDTH = 2.0f;

	private record Plate(BlockPos pos, PiezoPlateGroup.Status status, int outletMask) {
	}

	private static @Nullable ClientLevel seenLevel;
	private static long lastScan = Long.MIN_VALUE;
	private static List<Plate> plates = List.of();
	private static float opacity;
	private static @Nullable Component hint;

	private PiezoPlateXray() {
	}

	/** True while the player holds the wrench in either hand. */
	static boolean holdsWrench(Player player) {
		return player.getMainHandItem().is(ModContent.WRENCH.get())
				|| player.getOffhandItem().is(ModContent.WRENCH.get());
	}

	/** Per-frame entry: called by each loader's world-render hook next to the network overlay. */
	public static void submitFrame(SubmitNodeCollector collector, CameraRenderState camera) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level != seenLevel || level == null || mc.player == null) {
			seenLevel = level;
			plates = List.of();
			lastScan = Long.MIN_VALUE;
			opacity = 0;
			hint = null;
			return;
		}
		boolean active = holdsWrench(mc.player) && mc.gui.screen() == null;
		float step = Math.min(1, mc.getDeltaTracker().getRealtimeDeltaTicks() / 4.0f);
		opacity = active ? Math.min(1, opacity + step) : Math.max(0, opacity - step);
		long tick = level.getGameTime();
		if (active && (tick - lastScan >= SCAN_EVERY_TICKS || lastScan == Long.MIN_VALUE || tick < lastScan)) {
			lastScan = tick;
			plates = scan(level, mc.player.blockPosition());
		}
		hint = active ? focusedHint(mc, level) : null;
		if (opacity <= 0 || plates.isEmpty()) {
			return;
		}
		List<Plate> frame = plates;
		float fade = opacity;
		FrameGizmos.emit((gizmos, alphaMultiplier) -> {
			for (Plate plate : frame) {
				draw(gizmos, plate, fade);
			}
		}, true);
	}

	private static List<Plate> scan(ClientLevel level, BlockPos centre) {
		List<Plate> found = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-RADIUS, -HEIGHT, -RADIUS),
				centre.offset(RADIUS, HEIGHT, RADIUS))) {
			if (found.size() >= MAX_PLATES) {
				break;
			}
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PiezoPlateBlockEntity plate) {
				found.add(new Plate(pos.immutable(), plate.viewStatus(), plate.outletMask()));
			}
		}
		return List.copyOf(found);
	}

	private static void draw(GizmoPrimitives gizmos, Plate plate, float fade) {
		int fill;
		int edge;
		switch (plate.status()) {
			case OK -> {
				fill = 0x5532D25A;
				edge = 0xE632D25A;
			}
			case FULL -> {
				fill = 0x55E0413A;
				edge = 0xE6E0413A;
			}
			default -> {
				fill = 0x55FFB020;
				edge = 0xE6FFB020;
			}
		}
		BlockPos pos = plate.pos();
		AABB box = new AABB(pos.getX() + 1 / 16.0, pos.getY(), pos.getZ() + 1 / 16.0,
				pos.getX() + 15 / 16.0, pos.getY() + 0.12, pos.getZ() + 15 / 16.0);
		OverlayGeometry.addBoxOutline(gizmos, box, faded(fill, fade), faded(edge, fade), EDGE_WIDTH);
		Vec3 centre = new Vec3(pos.getX() + 0.5, pos.getY() + 0.06, pos.getZ() + 0.5);
		int line = faded(0xF0FFFFFF, fade);
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			if ((plate.outletMask() & 1 << dir.get2DDataValue()) != 0) {
				gizmos.addLine(centre, centre.add(dir.getStepX() * 0.9, 0, dir.getStepZ() * 0.9), line, EDGE_WIDTH);
			}
		}
		if ((plate.outletMask() & PiezoPlateGroup.OUTLET_FLOOR) != 0) {
			gizmos.addLine(centre, centre.add(0, -1.6, 0), line, EDGE_WIDTH);
		}
	}

	private static @Nullable Component focusedHint(Minecraft mc, ClientLevel level) {
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
				|| !(level.getBlockEntity(hit.getBlockPos()) instanceof PiezoPlateBlockEntity plate)) {
			return null;
		}
		String state = switch (plate.viewStatus()) {
			case OK -> "ok";
			case FULL -> "full";
			case NO_OUTLET -> "no_outlet";
		};
		return Component.translatable("hud.alaindustrial.piezo_plate." + state, plate.viewSize(),
				plate.viewStored(), plate.viewCapacity(), plate.viewOutlets());
	}

	/** HUD layer: the focused plate's group, beside the hotbar like the root inspection's line. */
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

	/** The same colour at {@code fade} of its alpha. */
	private static int faded(int argb, float fade) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.clamp(fade, 0.0F, 1.0F));
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}
}
