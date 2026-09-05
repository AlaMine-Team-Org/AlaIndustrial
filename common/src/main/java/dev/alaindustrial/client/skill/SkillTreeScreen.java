package dev.alaindustrial.client.skill;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alaindustrial.Config;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.SkillActionPayload;
import dev.alaindustrial.skill.SkillBranch;
import dev.alaindustrial.skill.SkillBuild;
import dev.alaindustrial.skill.SkillClientCache;
import dev.alaindustrial.skill.SkillEffects;
import dev.alaindustrial.skill.SkillPoints;
import dev.alaindustrial.skill.SkillSlot;
import dev.alaindustrial.stats.LevelMath;
import dev.alaindustrial.stats.PlayerModStats;
import dev.alaindustrial.stats.PlayerStatsClientCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

/**
 * The Workstation's upgrade tree (MOD-483) — a player profile on the left, a pannable and zoomable
 * board of skills on the right.
 *
 * <p><b>The look comes from a texture atlas, the layout from code.</b> Frame, header, profile card,
 * stat recesses, board and the node sprites live in {@code textures/gui/skill_tree.png}
 * ({@code tools/gen_skill_tree_gui.py}); {@link SkillTexture} stretches them nine-slice, so the panel
 * fits the window instead of running off it. The split is deliberate: a texture holds what never
 * grows, data holds what does — bake twenty-eight node positions into a picture and adding one skill
 * means redrawing the picture and recomputing every coordinate, while here a fifth branch costs one
 * method and one row of icons.
 *
 * <p><b>The board pans and zooms</b> (owner request): drag with the left button off a node, scroll to
 * zoom around the cursor. Both go through {@link SkillWheelLayout.View}, the single mapping between
 * board space and pixels — drawing and clicking share it, so the cursor keeps landing on the node the
 * player sees however far they have dragged.
 *
 * <p><b>There is no way to reset a build</b> (owner, 2026-09-05 and 2026-09-06). A paid wipe existed
 * while the tree was being built and was removed with its handler: it was scaffolding for testing the
 * fork rule, and a hard fork the player can buy their way out of is not a hard fork.
 *
 * <p>Client-only, like the dashboard it borrows its look from. State arrives through the synced
 * {@code player_skills} attachment and a purchase leaves as one {@link SkillActionPayload}; the screen
 * never waits on a reply. Nothing it draws is authoritative — every rule is asked of
 * {@link SkillBuild}, the same class the server asks.
 */
public final class SkillTreeScreen extends Screen {

	/** Largest the panel ever gets — beyond this the wheel would just float in grey. */
	private static final int MAX_PANEL_W = 420;
	private static final int MAX_PANEL_H = 320;

	/**
	 * Guaranteed gap between panel and window edge, top and bottom alike. At a large GUI scale the
	 * virtual screen can be under 280 pixels tall, and a fixed 320 ran clean off both edges — the panel
	 * shrinks instead, and the board shrinks with it.
	 */
	private static final int SCREEN_MARGIN = 20;

	private static final int HEADER = 22;
	private static final int PAD = 8;
	private static final int PROFILE_W = 116;
	private static final int AVATAR_INSET = 6;
	private static final int XP_H = 7;
	private static final int STAT_H = 14;
	private static final int STAT_GAP = 2;
	private static final int FOOTER_H = 18;

	private static final int TITLE_COLOR = 0xFFF4F4F4;
	private static final int ACCENT = 0xFFB87333;
	private static final int LINK = 0xFF8E8E8E;
	private static final int LINK_ON = 0xFFFFC15A;
	private static final int HOVER_RING = 0xFFFFC15A;
	private static final int XP_FILL = 0xFF57C7FF;
	private static final int XP_TRACK = 0xFF23262B;
	private static final int COST_BG = 0xC8141414;
	private static final int STAT_LABEL = 0xFFA9B0B8;
	private static final int STAT_VALUE = 0xFFF4F4F4;

	/** Node sprite columns in the atlas, in the order the screen decides between them. */
	private static final int STATE_TAKEN = 0;
	private static final int STATE_CAN = 1;
	private static final int STATE_LOCKED = 2;
	private static final int STATE_SHUT = 3;

	private static final double MIN_ZOOM = 0.6;
	private static final double MAX_ZOOM = 2.2;
	private static final double ZOOM_STEP = 1.15;

	/** Pixels between the dots a spoke link is drawn with, at 1x zoom. */
	private static final int LINK_STEP = 2;

	private static final int TOOLTIP_WIDTH = 170;

	/** Side of the small branch icon that sits before each corner caption. */
	private static final int LABEL_ICON = 10;

	/** The station this screen was opened from; every packet names it so the server can re-check reach. */
	private final BlockPos station;

	private final SkillWheelLayout layout = new SkillWheelLayout();

	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int avatarY1;
	private int xpY;
	private int statY;
	private int boardX0;
	private int boardY0;
	private int boardX1;
	private int boardY1;

	private double panX;
	private double panY;
	private double zoom = 1.0;
	private boolean dragging;

	public SkillTreeScreen(BlockPos station) {
		super(Component.translatable("gui.alaindustrial.skill_tree.title"));
		this.station = station;
	}

	@Override
	protected void init() {
		panelW = Math.min(this.width - SCREEN_MARGIN * 2, MAX_PANEL_W);
		panelH = Math.min(this.height - SCREEN_MARGIN * 2, MAX_PANEL_H);
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;

		boardX0 = PROFILE_W + PAD * 2;
		boardY0 = HEADER + PAD;
		boardX1 = panelW - PAD;
		boardY1 = panelH - PAD - FOOTER_H;

		// The profile column pins its numbers to the bottom and gives the portrait whatever is left, so
		// a short window loses picture rather than losing the figures the screen exists to show.
		statY = panelH - PAD - STAT_H * 3 - STAT_GAP * 2 - 4;
		xpY = statY - XP_H - 6;
		avatarY1 = xpY - 8;

		Component closeLabel = Component.translatable("gui.alaindustrial.skill_tree.close");
		int closeW = this.font.width(closeLabel) + 12;
		addRenderableWidget(Button.builder(closeLabel, b -> this.onClose())
				.bounds(panelX + panelW - closeW - 5, panelY + 3, closeW, 16).build());

	}

	/** Where the board is drawn from — the hub sits mid-recess when nothing is panned. */
	private SkillWheelLayout.View view() {
		return new SkillWheelLayout.View(
				panelX + (boardX0 + boardX1) / 2.0,
				panelY + (boardY0 + boardY1) / 2.0,
				panX, panY, zoom);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		SkillBuild build = SkillClientCache.current();
		PlayerModStats stats = PlayerStatsClientCache.current();
		int points = SkillPoints.earned(stats);

		SkillTexture.nineSlice(g, SkillTexture.PANEL_U, 0, SkillTexture.PIECE,
				SkillTexture.PANEL_BORDER, panelX, panelY, panelW, panelH);
		SkillTexture.header(g, panelX + 1, panelY + 1, panelW - 2);
		g.centeredText(this.font, this.title, panelX + panelW / 2, panelY + 7, TITLE_COLOR);

		drawProfile(g, stats, points, build, mouseX, mouseY);
		drawBoard(g, build, points, mouseX, mouseY);

	}

	/** The left column: the player's own model, their level, and what they have to spend. */
	private void drawProfile(GuiGraphicsExtractor g, PlayerModStats stats, int points,
			SkillBuild build, int mouseX, int mouseY) {
		int x0 = panelX + PAD;
		int y0 = panelY + HEADER + PAD;
		SkillTexture.nineSlice(g, SkillTexture.CARD_U, 0, SkillTexture.PIECE,
				SkillTexture.PIECE_BORDER, x0, y0, PROFILE_W, panelH - HEADER - PAD * 2);

		int ax0 = x0 + AVATAR_INSET;
		int ax1 = x0 + PROFILE_W - AVATAR_INSET;
		int ay0 = y0 + AVATAR_INSET;
		int ay1 = panelY + avatarY1;
		SkillTexture.nineSlice(g, SkillTexture.AVATAR_U, 0, SkillTexture.PIECE,
				SkillTexture.PIECE_BORDER, ax0, ay0, ax1 - ax0, ay1 - ay0);

		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && ay1 - ay0 > 24) {
			// The same call the dashboard uses, so the model follows the mouse exactly as it does there.
			int scale = Math.max(12, (ay1 - ay0) / 3);
			InventoryScreen.extractEntityInInventoryFollowsMouse(g,
					ax0 + 2, ay0 + 4, ax1 - 2, ay1 - 4, scale, 0.0625F, mouseX, mouseY, player);
			g.text(this.font, Component.literal(player.getName().getString()),
					panelX + PAD + 4, panelY + 7, HOVER_RING);
		}

		int level = SkillPoints.level(stats);
		double progress = LevelMath.progressToNext(stats.xp(Config.euPerXp, Config.euPerXpGenerated),
				level, Config.xpLevelOneCost, Config.levelXpMultiplier);
		int barX = ax0;
		int barW = ax1 - ax0;
		int barY = panelY + xpY;
		g.fill(barX, barY, barX + barW, barY + XP_H, XP_TRACK);
		int filled = (int) Math.round((barW - 2) * Math.clamp(progress, 0.0, 1.0));
		if (filled > 0) {
			g.fill(barX + 1, barY + 1, barX + 1 + filled, barY + XP_H - 1, XP_FILL);
		}

		String rank = Component.translatable("alaindustrial.rank." + LevelMath.rankKey(level)).getString()
				+ " " + LevelMath.roman(LevelMath.subLevel(level));
		// Level alone: the ceiling is the same 40 for everyone and said nothing about this player.
		statRow(g, 0, ax0, barW, Component.translatable("gui.alaindustrial.skill_tree.stat_level"),
				Component.literal(String.valueOf(level)));
		// No spaces around the slash — the column is narrow and "4 / 4" read as two loose numbers.
		statRow(g, 1, ax0, barW, Component.translatable("gui.alaindustrial.skill_tree.stat_points"),
				Component.literal(build.free(points) + "/" + points));
		statRow(g, 2, ax0, barW, Component.translatable("gui.alaindustrial.skill_tree.stat_rank"),
				Component.literal(rank));
	}

	/** One line in a stat recess: label on the left, value on the right. */
	private void statRow(GuiGraphicsExtractor g, int index, int x, int w,
			Component label, Component value) {
		int y = panelY + statY + index * (STAT_H + STAT_GAP);
		SkillTexture.nineSliceH(g, SkillTexture.STAT_U, 0, SkillTexture.PIECE, SkillTexture.STAT_H,
				SkillTexture.STAT_BORDER, x, y, w, STAT_H);
		// Light on dark: the recess is now dark precisely so these two read at a glance. Grey-on-grey
		// was the first thing the owner caught in game.
		g.text(this.font, label, x + 4, y + 4, STAT_LABEL);
		g.text(this.font, value, x + w - 4 - this.font.width(value), y + 4, STAT_VALUE);
	}

	/**
	 * The board: links, nodes, branch captions — all clipped to the recess.
	 *
	 * <p>The scissor is what makes panning honest. Without it a dragged tree would spill over the
	 * profile column and the frame; with it the recess behaves like a window onto something larger,
	 * which is exactly what it is.
	 */
	private void drawBoard(GuiGraphicsExtractor g, SkillBuild build, int points, int mouseX, int mouseY) {
		int bx = panelX + boardX0;
		int by = panelY + boardY0;
		int bw = boardX1 - boardX0;
		int bh = boardY1 - boardY0;
		SkillTexture.nineSlice(g, SkillTexture.BOARD_U, 0, SkillTexture.PIECE,
				SkillTexture.PIECE_BORDER, bx, by, bw, bh);

		SkillWheelLayout.View view = view();
		g.enableScissor(bx + 2, by + 2, bx + bw - 2, by + bh - 2);

		for (SkillWheelLayout.Edge edge : layout.edges()) {
			boolean lit = build.has(edge.from().branch(), edge.from().slot())
					&& build.has(edge.to().branch(), edge.to().slot());
			drawLink(g, view, edge.from(), edge.to(), lit);
		}
		drawHub(g, view, build, points);

		SkillWheelLayout.Placed hovered = hoveredNode(mouseX, mouseY);
		for (SkillWheelLayout.Placed node : layout.nodes()) {
			drawNode(g, view, node, build, points, node == hovered);
		}
		drawBranchLabels(g, bx, by, bw, bh);
		g.disableScissor();

		// Tooltips go up after the scissor is off: one opening near the right edge has to be allowed to
		// spill over the frame, or it would be clipped to unreadability.
		if (hovered != null) {
			g.setTooltipForNextFrame(this.font, tooltip(hovered, build, points), mouseX, mouseY);
		}
	}

	/**
	 * Branch captions, pinned to the four corners of the board rather than drawn out on the spokes.
	 *
	 * <p>On the spokes they collided with the outer nodes at every zoom level and slid off the board
	 * the moment it was dragged. Pinned, each caption sits in the corner its branch actually points
	 * at — the wheel puts one branch on each diagonal — so it stays a legend that is always readable
	 * and always true, whatever the player does to the view.
	 *
	 * <p>Each carries its branch's entry icon, so a player learns the symbol before ever hovering a
	 * node.
	 */
	private void drawBranchLabels(GuiGraphicsExtractor g, int bx, int by, int bw, int bh) {
		for (SkillBranch branch : SkillBranch.values()) {
			Component name = Component.translatable(branchKey(branch));
			int textW = this.font.width(name);
			boolean left = branch.ordinal() == 0 || branch.ordinal() == 2;
			boolean top = branch.ordinal() < 2;
			int x = left ? bx + 5 : bx + bw - 5 - textW - LABEL_ICON - 2;
			int y = top ? by + 5 : by + bh - 5 - LABEL_ICON;
			// A plate under the caption, the same one the cost badges wear. The board scrolls and zooms
			// underneath these four, so sooner or later a node ends up behind one — and copper-on-node
			// is unreadable exactly when the player is looking for the branch they are in. Drawn first,
			// so the icon and the text sit on it rather than under it.
			g.fill(x - 2, y - 2, x + LABEL_ICON + 2 + textW + 2, y + LABEL_ICON + 2, COST_BG);
			SkillTexture.icon(g, branch.ordinal(), SkillSlot.IN.ordinal(),
					x + LABEL_ICON / 2, y + LABEL_ICON / 2, LABEL_ICON);
			g.text(this.font, name, x + LABEL_ICON + 2, y + 2, ACCENT);
		}
	}

	/** The station plate at the centre of the board — what the four branches run out of. */
	private void drawHub(GuiGraphicsExtractor g, SkillWheelLayout.View view, SkillBuild build, int points) {
		int cx = (int) Math.round(view.toScreenX(0));
		int cy = (int) Math.round(view.toScreenY(0));
		// The hub has art of its own — an octagonal plate with a copper rim. Borrowing another element's
		// look failed twice: the profile card went grey on grey, and a plain dark square fought the board
		// it sits in. A bare number then read as debris rather than a centre. The plate is a little
		// larger than a node and shaped differently, so it belongs to the wheel without pretending to be
		// one of its nodes.
		int size = Math.max(16, (int) Math.round(SkillTexture.HUB * zoom));
		SkillTexture.hub(g, cx, cy, size);
		// Gold on the recessed dark window — a reading on a display, which is what the plate now is.
		// Grey on grey was the complaint every earlier version earned.
		g.centeredText(this.font, Component.literal(String.valueOf(build.free(points))),
				cx, cy - 4, HOVER_RING);
	}

	/**
	 * A spoke link, drawn as a dotted run between two node centres.
	 *
	 * <p>{@code fill} only draws axis-aligned rectangles and every link here is diagonal, so the line is
	 * stepped along in short squares. The step scales with the zoom: a fixed one would thin into dashes
	 * zoomed in and clot into a solid bar zoomed out.
	 */
	private void drawLink(GuiGraphicsExtractor g, SkillWheelLayout.View view,
			SkillWheelLayout.Placed from, SkillWheelLayout.Placed to, boolean lit) {
		int colour = lit ? LINK_ON : LINK;
		double x1 = view.toScreenX(from.x());
		double y1 = view.toScreenY(from.y());
		double x2 = view.toScreenX(to.x());
		double y2 = view.toScreenY(to.y());
		double dx = x2 - x1;
		double dy = y2 - y1;
		double length = Math.sqrt(dx * dx + dy * dy);
		if (length <= 0) {
			return;
		}
		double clear = SkillWheelLayout.NODE_RADIUS * zoom;
		double step = Math.max(1.0, LINK_STEP * zoom);
		int thickness = Math.max(1, (int) Math.round(zoom));
		for (double at = clear; at <= length - clear; at += step) {
			int px = (int) Math.round(x1 + dx * at / length);
			int py = (int) Math.round(y1 + dy * at / length);
			g.fill(px, py, px + thickness, py + thickness, colour);
		}
	}

	/** One node: its sprite, its skill icon, its cost, and a halo when hovered. */
	private void drawNode(GuiGraphicsExtractor g, SkillWheelLayout.View view,
			SkillWheelLayout.Placed node, SkillBuild build, int points, boolean hovered) {
		SkillBranch branch = node.branch();
		SkillSlot slot = node.slot();
		int state;
		if (build.has(branch, slot)) {
			state = STATE_TAKEN;
		} else if (build.blocked(branch, slot)) {
			state = STATE_SHUT;
		} else if (build.canBuy(branch, slot, points)) {
			state = STATE_CAN;
		} else {
			state = STATE_LOCKED;
		}
		int cx = (int) Math.round(view.toScreenX(node.x()));
		int cy = (int) Math.round(view.toScreenY(node.y()));
		int size = Math.max(8, (int) Math.round(SkillTexture.NODE * zoom));

		if (hovered) {
			// A larger copy of the gold sprite behind the node reads as a halo with no extra art.
			SkillTexture.node(g, STATE_TAKEN, cx, cy, size + 4);
		}
		SkillTexture.node(g, state, cx, cy, size);
		SkillTexture.icon(g, branch.ordinal(), slot.ordinal(), cx, cy, Math.max(6, (int) (size * 0.6)));

		// Cost badge on the lower-right edge: a dark plate under a light number, readable on any state.
		String cost = String.valueOf(slot.cost());
		int badgeW = this.font.width(cost) + 3;
		int bx = cx + size / 2 - badgeW;
		int by = cy + size / 2 - 8;
		g.fill(bx, by, bx + badgeW, by + 9, COST_BG);
		g.text(this.font, Component.literal(cost), bx + 2, by + 1, HOVER_RING);
	}

	/**
	 * What a node says on hover: name, price, effect, and — the part the hard fork makes essential —
	 * whether taking it closes the other side forever.
	 */
	private List<FormattedCharSequence> tooltip(SkillWheelLayout.Placed node, SkillBuild build, int points) {
		SkillBranch branch = node.branch();
		SkillSlot slot = node.slot();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(nodeKey(branch, slot) + ".name").withStyle(ChatFormatting.GOLD));
		lines.add(Component.translatable("gui.alaindustrial.skill_tree.cost", slot.cost())
				.withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable(nodeKey(branch, slot) + ".desc"));
		if (!SkillEffects.wired(branch, slot)) {
			// Honest while the tree is being built: the node is bought and stored, but nothing reads it
			// yet. Without this line a player spends a point and believes the description.
			lines.add(Component.translatable("gui.alaindustrial.skill_tree.not_wired")
					.withStyle(ChatFormatting.DARK_RED));
		}
		switch (build.refuse(branch, slot, points)) {
			case ALREADY_TAKEN -> lines.add(Component.translatable("gui.alaindustrial.skill_tree.owned")
					.withStyle(ChatFormatting.GREEN));
			case FORK_CLOSED -> lines.add(Component.translatable("gui.alaindustrial.skill_tree.fork_closed",
					Component.translatable(nodeKey(branch, slot.sibling()) + ".name"))
					.withStyle(ChatFormatting.RED));
			case LOCKED -> lines.add(Component.translatable("gui.alaindustrial.skill_tree.locked")
					.withStyle(ChatFormatting.DARK_GRAY));
			case NOT_ENOUGH_POINTS -> lines.add(Component.translatable(
					"gui.alaindustrial.skill_tree.need_points", slot.cost() - build.free(points))
					.withStyle(ChatFormatting.RED));
			case NONE -> {
				lines.add(Component.translatable("gui.alaindustrial.skill_tree.buy")
						.withStyle(ChatFormatting.GREEN));
				if (slot.sibling() != null) {
					lines.add(Component.translatable("gui.alaindustrial.skill_tree.will_close",
							Component.translatable(nodeKey(branch, slot.sibling()) + ".name"))
							.withStyle(ChatFormatting.RED));
				}
			}
			default -> {
				// Refusal is exhaustive above; nothing to add.
			}
		}
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (Component line : lines) {
			wrapped.addAll(this.font.split(line, TOOLTIP_WIDTH));
		}
		return wrapped;
	}

	private static String branchKey(SkillBranch branch) {
		return "gui.alaindustrial.skill." + branch.key();
	}

	private static String nodeKey(SkillBranch branch, SkillSlot slot) {
		return branchKey(branch) + "." + slot.name().toLowerCase(Locale.ROOT);
	}

	/** Whether the cursor is inside the board recess — pan and zoom only apply there. */
	private boolean overBoard(double mouseX, double mouseY) {
		return mouseX >= panelX + boardX0 && mouseX <= panelX + boardX1
				&& mouseY >= panelY + boardY0 && mouseY <= panelY + boardY1;
	}

	/** The node under the cursor, or {@code null} — also null while the cursor is off the board. */
	private SkillWheelLayout.Placed hoveredNode(double mouseX, double mouseY) {
		return overBoard(mouseX, mouseY) ? layout.nodeAt(mouseX, mouseY, view()) : null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overBoard(event.x(), event.y())) {
			SkillWheelLayout.Placed node = layout.nodeAt(event.x(), event.y(), view());
			if (node != null) {
				buy(node);
				return true;
			}
			// Empty board: the press starts a drag rather than doing nothing.
			dragging = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			panX += dragX;
			panY += dragY;
			clampPan();
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			dragging = false;
		}
		return super.mouseReleased(event);
	}

	/**
	 * Zoom around the cursor, not around the centre.
	 *
	 * <p>The board point under the pointer is read before the zoom changes and put back under it
	 * afterwards, which is what makes the wheel feel anchored to the mouse instead of sliding away from
	 * whatever the player was looking at.
	 */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY == 0 || !overBoard(mouseX, mouseY)) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		SkillWheelLayout.View before = view();
		double anchorX = before.toBoardX(mouseX);
		double anchorY = before.toBoardY(mouseY);
		double next = Math.clamp(zoom * (scrollY > 0 ? ZOOM_STEP : 1 / ZOOM_STEP), MIN_ZOOM, MAX_ZOOM);
		if (next == zoom) {
			return true;
		}
		zoom = next;
		panX = mouseX - before.originX() - anchorX * zoom;
		panY = mouseY - before.originY() - anchorY * zoom;
		clampPan();
		return true;
	}

	/**
	 * Keep the tree from being dragged out of sight: far enough that any branch can reach the middle of
	 * the window, never so far that the board is empty and there is nothing to drag back.
	 */
	private void clampPan() {
		// The hub itself must stay on the board. The previous limit added the tree's own extent, which
		// let the player drag every node past the edge and stare at an empty grey field with no way to
		// tell which direction the wheel had gone.
		double limitX = (boardX1 - boardX0) / 2.0 - SkillWheelLayout.HUB_RADIUS * zoom;
		double limitY = (boardY1 - boardY0) / 2.0 - SkillWheelLayout.HUB_RADIUS * zoom;
		panX = Math.clamp(panX, -Math.max(0, limitX), Math.max(0, limitX));
		panY = Math.clamp(panY, -Math.max(0, limitY), Math.max(0, limitY));
	}

	/**
	 * Ask for a node. Sent only when this client believes it is legal — not as security (the server
	 * re-checks everything) but so a misclick on a locked node stays silent instead of playing a
	 * purchase sound for a purchase that will not happen.
	 */
	private void buy(SkillWheelLayout.Placed node) {
		SkillBuild build = SkillClientCache.current();
		int points = SkillPoints.earned(PlayerStatsClientCache.current());
		if (!build.canBuy(node.branch(), node.slot(), points)) {
			// A refused click used to be silent, which reads as a broken screen rather than a refusal.
			playClick(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value());
			return;
		}
		NetworkDispatcher.get().sendToServer(SkillActionPayload.buy(station, node.branch(), node.slot()));
		playClick(SoundEvents.EXPERIENCE_ORB_PICKUP);
	}

	private void playClick(SoundEvent sound) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
	}
}
