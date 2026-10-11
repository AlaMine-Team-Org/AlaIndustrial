package dev.alaindustrial.client.blueprint;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.client.blueprint.BlueprintDef.Placement;
import dev.alaindustrial.client.blueprint.BlueprintDef.Step;
import dev.alaindustrial.mixin.client.GuiGraphicsExtractorAccessor;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The blueprint sheet (MOD-797): a blue engineering sheet with a grid and a double frame, the scene in
 * the middle, a numbered callout on the block the step is about, a title block in the corner and a step
 * strip at the bottom. Steps play on their own; the player can pause, step, turn the scene by dragging
 * and zoom with the wheel. Closing returns to the screen the sheet was opened from.
 */
public final class BlueprintScreen extends Screen {

	/** How long one step plays, in milliseconds. */
	private static final long STEP_MS = 5500;
	private static final float MIN_ZOOM = 0.5f;
	private static final float MAX_ZOOM = 2.5f;
	private static final int STRIP_H = 40;
	private static final int BUTTON = 16;
	private static final int CALLOUT_W = 150;
	/** How long one item icon takes to go in or come out, in milliseconds. */
	private static final long ITEM_MS = 1400;
	private static final long SWAY_AFTER_MS = 3000;
	private static final double SWAY_PERIOD_MS = 9000;
	private static final double SWAY_DEGREES = 12;

	private final BlueprintDef def;
	private final @Nullable Screen parent;
	private final BlueprintBounds bounds;
	private int step;
	private long stepStarted = System.currentTimeMillis();
	private long pausedAt = -1;
	private BlueprintCamera camera = BlueprintCamera.DEFAULT;
	private float zoom = 1f;
	/** When the player last turned the scene; the idle sway waits a while after that. */
	private long lastTurned = Long.MIN_VALUE / 2;

	public BlueprintScreen(BlueprintDef def, @Nullable Screen parent) {
		super(Component.translatable(def.titleKey()));
		this.def = def;
		this.parent = parent;
		this.bounds = BlueprintBounds.of(def);
		BlueprintProgress.markViewed(def.id());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}

	// ─── timing ─────────────────────────────────────────────────────────────────────────────────

	private long now() {
		return pausedAt >= 0 ? pausedAt : System.currentTimeMillis();
	}

	private float reveal() {
		return Math.min(1f, (now() - stepStarted) / (float) STEP_MS);
	}

	@Override
	public void tick() {
		if (pausedAt < 0 && System.currentTimeMillis() - stepStarted >= STEP_MS) {
			goTo(step + 1);
		}
	}

	private void goTo(int target) {
		int count = def.steps().size();
		step = Math.floorMod(target, count);
		stepStarted = System.currentTimeMillis();
		if (pausedAt >= 0) {
			pausedAt = stepStarted;
		}
	}

	private void togglePause() {
		if (pausedAt >= 0) {
			stepStarted += System.currentTimeMillis() - pausedAt;
			pausedAt = -1;
		} else {
			pausedAt = System.currentTimeMillis();
		}
	}

	/**
	 * The camera the scene is drawn with: the player's angle, plus a slow sway while the sheet plays and the
	 * player has not turned it for a few seconds, so the scene reads as a solid.
	 */
	private BlueprintCamera viewCamera() {
		long now = System.currentTimeMillis();
		if (pausedAt >= 0 || now - lastTurned < SWAY_AFTER_MS) {
			return camera;
		}
		return camera.turned((float) (SWAY_DEGREES * Math.sin(now / SWAY_PERIOD_MS * 2 * Math.PI)));
	}

	// ─── layout ─────────────────────────────────────────────────────────────────────────────────

	private int sceneLeft() {
		return 20;
	}

	private int sceneTop() {
		return 30;
	}

	private int sceneRight() {
		return width - 20;
	}

	private int sceneBottom() {
		return height - STRIP_H - 34;
	}

	/**
	 * The 3D picture covers the whole sheet inside the frame, not just the middle band, so a zoomed-in
	 * scene is cut only by the frame; the callout, the title block and the strip are drawn over it.
	 */
	private static final int PICTURE_INSET = 10;

	private int pictureCenterX() {
		return width / 2;
	}

	private int pictureCenterY() {
		return height / 2;
	}

	private float pixelsPerBlock() {
		int w = sceneRight() - sceneLeft();
		int h = sceneBottom() - sceneTop();
		return (float) (Math.min(w, h) / (2.4 * bounds.radius())) * zoom;
	}

	/** Screen position of scene point ({@code x}, {@code y}, {@code z}), given in block coordinates. */
	private int[] toScreen(double x, double y, double z) {
		double[] p = viewCamera().project(x - bounds.centerX(), y - bounds.centerY(), z - bounds.centerZ());
		float s = pixelsPerBlock();
		return new int[] {pictureCenterX() + (int) Math.round(p[0] * s), pictureCenterY() + (int) Math.round(p[1] * s)};
	}

	// ─── drawing ────────────────────────────────────────────────────────────────────────────────

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		BlueprintPaper.sheet(g, width, height);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		((GuiGraphicsExtractorAccessor) g).alaindustrial$guiRenderState().addPicturesInPictureState(
				BlueprintSceneState.of(def, step, reveal(), viewCamera(), pixelsPerBlock(),
						PICTURE_INSET, PICTURE_INSET, width - PICTURE_INSET, height - PICTURE_INSET));
		g.nextStratum();
		Step current = def.steps().get(step);
		drawFlow(g, current);
		drawItems(g, current);
		drawCallout(g, current);
		g.text(font, Component.translatable("blueprint.alaindustrial.ui.sheet_label",
				this.title.getString().toUpperCase(java.util.Locale.ROOT)), 16, 14, BlueprintPaper.INK, false);
		BlueprintPaper.titleBlock(g, font, width, height - STRIP_H - 2,
				Component.translatable("blueprint.alaindustrial.ui.sheet_of", step + 1, def.steps().size()));
		drawStrip(g, mouseX, mouseY);
	}

	/**
	 * Whatever the step moves between blocks: energy as orange dots, lava and water as drops of their colour,
	 * an item as its own icon riding the line.
	 */
	private void drawFlow(GuiGraphicsExtractor g, Step current) {
		if (current.flows().isEmpty() || reveal() < 0.3f) {
			return;
		}
		for (BlueprintDef.Flow flow : current.flows()) {
			Placement from = current.blocks().get(flow.from());
			Placement to = current.blocks().get(flow.to());
			int[] a = toScreen(from.x() + 0.5, from.y() + 0.5, from.z() + 0.5);
			int[] b = toScreen(to.x() + 0.5, to.y() + 0.5, to.z() + 0.5);
			boolean item = flow.kind() == BlueprintDef.FlowKind.ITEM;
			long period = item ? 1800 : 900;
			float phase = (System.currentTimeMillis() % period) / (float) period;
			int count = item ? 2 : 4;
			for (int i = 0; i < count; i++) {
				float t = (phase + i / (float) count) % 1f;
				int x = Math.round(a[0] + (b[0] - a[0]) * t);
				int y = Math.round(a[1] + (b[1] - a[1]) * t);
				if (item) {
					g.item(BlueprintItems.stack(flow.itemId()), x - 8, y - 8);
				} else {
					int[] colours = flowColours(flow.kind());
					g.fill(x - 2, y - 2, x + 2, y + 2, colours[0]);
					g.fill(x - 1, y - 1, x + 1, y + 1, colours[1]);
				}
			}
		}
	}

	/** Outer and inner colour of a flow's dots. */
	private static int[] flowColours(BlueprintDef.FlowKind kind) {
		return switch (kind) {
			case LAVA -> new int[] {0xFFD2410A, 0xFFFFB347};
			case WATER -> new int[] {0xFF2F6BFF, 0xFFBFD8FF};
			case STEAM -> new int[] {0xFF9AA8B4, 0xFFFFFFFF};
			default -> new int[] {BlueprintPaper.ENERGY, BlueprintPaper.INK};
		};
	}

	/**
	 * Inputs and outputs as item icons: an input drops onto the block from above and shrinks into it, an
	 * output grows out of the block's front. The step's motions take turns in a loop.
	 */
	private void drawItems(GuiGraphicsExtractor g, Step current) {
		if (current.items().isEmpty() || reveal() < 0.3f) {
			return;
		}
		long elapsed = now() - stepStarted;
		int count = current.items().size();
		long slot = (elapsed / ITEM_MS) % count;
		float t = (elapsed % ITEM_MS) / (float) ITEM_MS;
		BlueprintDef.ItemMotion motion = current.items().get((int) slot);
		Placement p = current.blocks().get(motion.block());
		int[] centre = toScreen(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
		int[] outside = motion.into()
				? toScreen(p.x() + 0.5, p.y() + 2.0, p.z() + 0.5)
				: toScreen(p.x() + 0.5, p.y() + 0.5, p.z() + 2.0);
		float eased = t * t * (3 - 2 * t);
		if (motion.into() && t > 0.85f) {
			return;
		}
		int[] from = motion.into() ? outside : centre;
		int[] to = motion.into() ? centre : outside;
		float x = from[0] + (to[0] - from[0]) * eased;
		float y = from[1] + (to[1] - from[1]) * eased;
		float size = (motion.into() ? 1.8f - 1.1f * eased : 0.7f + 1.1f * eased) * Math.max(1f, zoom);
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(size, size);
		g.item(BlueprintItems.stack(motion.itemId()), -8, -8);
		g.pose().popMatrix();
	}

	/** The numbered callout: balloon on the block, dashed leader, a box with the step's title and text. */
	private void drawCallout(GuiGraphicsExtractor g, Step current) {
		if (current.highlight() < 0 || reveal() < 0.15f) {
			return;
		}
		Placement p = current.blocks().get(current.highlight());
		int[] anchor = toScreen(p.x() + 0.5, p.y() + 1.15, p.z() + 0.5);
		String mark = switch (current.verdict()) {
			case GOOD -> "✔ ";
			case BAD -> "✘ ";
			case NONE -> "";
		};
		List<FormattedCharSequence> titleLines = font.split(
				Component.literal(mark).append(Component.translatable(def.stepTitleKey(step))), CALLOUT_W - 12);
		List<FormattedCharSequence> body = font.split(Component.translatable(def.stepTextKey(step)), CALLOUT_W - 12);
		int titleH = titleLines.size() * 10;
		int boxH = 6 + titleH + 4 + body.size() * 10 + 6;
		boolean right = anchor[0] < width / 2;
		int boxX = right ? anchor[0] + 40 : anchor[0] - 40 - CALLOUT_W;
		boxX = Math.max(14, Math.min(width - CALLOUT_W - 14, boxX));
		int boxY = Math.max(28, anchor[1] - boxH - 34);
		int edgeX = right ? boxX : boxX + CALLOUT_W;
		int midY = boxY + boxH / 2;
		BlueprintPaper.dashedV(g, anchor[0], Math.min(anchor[1], midY), Math.max(anchor[1], midY));
		BlueprintPaper.dashedH(g, Math.min(anchor[0], edgeX), Math.max(anchor[0], edgeX), midY);
		BlueprintPaper.balloon(g, font, anchor[0], midY, current.position());
		int ink = current.verdict() == BlueprintDef.Verdict.BAD ? BlueprintPaper.RED : BlueprintPaper.INK;
		g.fill(boxX, boxY, boxX + CALLOUT_W, boxY + boxH, BlueprintPaper.PAPER_DARK);
		g.outline(boxX, boxY, CALLOUT_W, boxH, ink);
		int y = boxY + 6;
		for (FormattedCharSequence line : titleLines) {
			g.text(font, line, boxX + 6, y, ink, false);
			y += 10;
		}
		g.fill(boxX + 6, y, boxX + CALLOUT_W - 6, y + 1, BlueprintPaper.INK_FAINT);
		y += 4;
		for (FormattedCharSequence line : body) {
			g.text(font, line, boxX + 6, y, BlueprintPaper.INK, false);
			y += 10;
		}
	}

	/** Prev / pause / next squares and the step strip with a mark per step. */
	private void drawStrip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int y = height - STRIP_H + 4;
		String[] labels = {"◀", pausedAt >= 0 ? "▶" : "II", "▶"};
		for (int i = 0; i < 3; i++) {
			int x = 16 + i * (BUTTON + 4);
			boolean hover = mouseX >= x && mouseX < x + BUTTON && mouseY >= y && mouseY < y + BUTTON;
			if (hover) {
				g.fill(x, y, x + BUTTON, y + BUTTON, BlueprintPaper.INK_FAINT);
			}
			g.outline(x, y, BUTTON, BUTTON, BlueprintPaper.INK);
			g.centeredText(font, labels[i], x + BUTTON / 2, y + 4, BlueprintPaper.INK);
		}
		int left = 16 + 3 * (BUTTON + 4) + 6;
		int right = width - 16;
		int barY = y + BUTTON / 2 - 1;
		int count = def.steps().size();
		g.fill(left, barY, right, barY + 2, BlueprintPaper.INK_FAINT);
		float done = (step + reveal()) / count;
		g.fill(left, barY, left + Math.round((right - left) * done), barY + 2, BlueprintPaper.INK);
		for (int i = 1; i < count; i++) {
			int x = left + (right - left) * i / count;
			g.fill(x, barY - 4, x + 1, barY + 6, BlueprintPaper.INK);
		}
	}

	// ─── input ──────────────────────────────────────────────────────────────────────────────────

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int y = height - STRIP_H + 4;
			for (int i = 0; i < 3; i++) {
				int x = 16 + i * (BUTTON + 4);
				if (event.x() >= x && event.x() < x + BUTTON && event.y() >= y && event.y() < y + BUTTON) {
					switch (i) {
						case 0 -> goTo(step - 1);
						case 1 -> togglePause();
						default -> goTo(step + 1);
					}
					return true;
				}
			}
			int left = 16 + 3 * (BUTTON + 4) + 6;
			int right = width - 16;
			if (event.y() >= y && event.y() < y + BUTTON && event.x() >= left && event.x() < right) {
				goTo((int) ((event.x() - left) / (right - left) * def.steps().size()));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.y() < sceneBottom()) {
			camera = viewCamera().turned((float) dragX * 0.8f).withPitch(camera.pitchDegrees() + (float) dragY * 0.6f);
			lastTurned = System.currentTimeMillis();
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * (scrollY > 0 ? 1.1f : 1 / 1.1f)));
		return true;
	}
}
