package dev.alaindustrial.client.dashboard;

import dev.alaindustrial.Config;
import dev.alaindustrial.client.GuiStyle;
import dev.alaindustrial.stats.LevelMath;
import dev.alaindustrial.stats.PlayerModStats;
import dev.alaindustrial.stats.PlayerStatsClientCache;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The player's "personal cabinet" (MOD-133) — an MMORPG-style profile screen. A left column holds the
 * live 3D player avatar (following the mouse, like the inventory) with the player's name beneath it;
 * the right column stacks career stats into titled modules: rank + XP bar, energy totals, and the
 * per-generator production breakdown. Client-only modal, vanilla-minimalist look (same primitives as
 * {@code GuideBookScreen}), opened by the {@code K} hotkey or the inventory corner button.
 *
 * <p>{@link #isPauseScreen()} is {@code false} so the world keeps ticking (in singleplayer) while it
 * is open — otherwise the "live" numbers would freeze. Data comes from {@link PlayerStatsClientCache}.
 */
public final class DashboardScreen extends Screen {
	private static final int HEADER = 22;
	private static final int HEADER_BG = 0xFF141414;
	private static final int TITLE_COLOR = 0xFFF4F4F4;

	// Palette tuned for a "profile card" look on top of the vanilla panel.
	private static final int CARD_FILL = 0xFFB2B2B2;   // recessed module card
	private static final int AVATAR_BG_TOP = 0xFF1B1D24; // cool dark avatar backdrop (top)
	private static final int AVATAR_BG_BOT = 0xFF0C0D12; // …fading darker at the bottom
	private static final int ACCENT = 0xFFB87333;      // copper — module titles
	private static final int XP_FILL = 0xFF57C7FF;
	private static final int XP_FILL_HI = 0xFFBFEBFF;
	private static final int XP_TRACK = 0xFF23262B;

	private int panelX, panelY, panelW, panelH;
	private int avatarX0, avatarY0, avatarX1, avatarY1;

	public DashboardScreen() {
		super(Component.translatable("gui.alaindustrial.dashboard.title"));
	}

	@Override
	protected void init() {
		panelW = Math.min(this.width - 24, 384);
		panelH = Math.min(this.height - 24, 248);
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;

		// Avatar frame (left column) — full-height character panel.
		avatarX0 = panelX + 12;
		avatarY0 = panelY + HEADER + 10;
		avatarX1 = avatarX0 + 104;
		avatarY1 = panelY + panelH - 12;

		Component closeLabel = Component.translatable("gui.alaindustrial.dashboard.close");
		int closeW = this.font.width(closeLabel) + 12;
		addRenderableWidget(Button.builder(closeLabel, b -> this.onClose())
				.bounds(panelX + panelW - closeW - 6, panelY + 3, closeW, 16).build());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		PlayerModStats stats = PlayerStatsClientCache.current();
		LocalPlayer player = Minecraft.getInstance().player;

		GuiStyle.panel(g, panelX, panelY, panelW, panelH);
		g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + HEADER, HEADER_BG);
		g.fill(panelX + 1, panelY + HEADER, panelX + panelW - 1, panelY + HEADER + 1, GuiStyle.PANEL_LO);
		// Header shows the player's name — this screen IS their profile.
		String headerName = player != null ? player.getName().getString() : this.title.getString();
		g.centeredText(this.font, Component.literal(headerName), panelX + panelW / 2, panelY + 7, TITLE_COLOR);

		drawAvatarCard(g, player, mouseX, mouseY);

		// Right column geometry.
		int rx = avatarX1 + 12;
		int rRight = panelX + panelW - 12;
		int ry = avatarY0;

		ry = drawRankModule(g, stats, rx, ry, rRight);
		ry += 6;
		ry = drawEnergyModule(g, stats, rx, ry, rRight);
		ry += 6;
		drawGeneratorModule(g, stats, rx, ry, rRight, panelY + panelH - 12);
	}

	// --- Left column: 3D avatar + name -------------------------------------------------------------

	private void drawAvatarCard(GuiGraphicsExtractor g, LocalPlayer player, int mouseX, int mouseY) {
		// Recessed frame with a soft vertical gradient backdrop.
		inset(g, avatarX0 - 1, avatarY0 - 1, avatarX1 + 1, avatarY1 + 1);
		verticalGradient(g, avatarX0, avatarY0, avatarX1, avatarY1, AVATAR_BG_TOP, AVATAR_BG_BOT);
		if (player != null) {
			// Name now lives in the header; the avatar fills the whole frame.
			int scale = (avatarY1 - avatarY0) / 3; // fills the frame at ~full-body height
			InventoryScreen.extractEntityInInventoryFollowsMouse(g,
					avatarX0 + 2, avatarY0 + 6, avatarX1 - 2, avatarY1 - 6,
					scale, 0.0625F, mouseX, mouseY, player);
		}
	}

	// --- Right column modules ----------------------------------------------------------------------

	private int drawRankModule(GuiGraphicsExtractor g, PlayerModStats stats, int x, int y, int right) {
		int h = 52;
		card(g, x, y, right - x, h);
		int inX = x + 8;
		int inRight = right - 8;
		// Same formula as `/ala profile show` — the rank floor OR the level the current points already
		// earn, whichever is higher. Using highestLevelReached alone would lag whenever points move
		// without a flush having run yet (e.g. right after a rate change retroactively raises the
		// total), showing a stale rank next to a bar clamped at 100%.
		int level = Math.max(Math.max(1, stats.highestLevelReached()),
				LevelMath.levelForXp(stats.xp(Config.euPerXp, Config.euPerXpGenerated),
						Config.xpLevelOneCost, Config.levelXpMultiplier));
		// Module title makes the progression its own named system ("Mastery"), distinct from vanilla XP.
		moduleTitle(g, Component.translatable("gui.alaindustrial.dashboard.mastery"), inX, y + 5);
		String rankName = Component.translatable("alaindustrial.rank." + LevelMath.rankKey(level)).getString();
		String rankLine = rankName + " " + LevelMath.roman(LevelMath.subLevel(level));
		g.text(this.font, Component.literal(rankLine), inX, y + 17, GuiStyle.TEXT, false);
		String levelTag = Component.translatable("gui.alaindustrial.dashboard.level", level).getString();
		g.text(this.font, Component.literal(levelTag), inRight - this.font.width(levelTag), y + 17, GuiStyle.TEXT_DIM, false);

		// Mastery bar (deliberately NOT called "XP" — see the mastery label below).
		long xp = stats.xp(Config.euPerXp, Config.euPerXpGenerated);
		double progress = LevelMath.progressToNext(xp, level, Config.xpLevelOneCost, Config.levelXpMultiplier);
		int barY = y + 30;
		int barW = inRight - inX;
		g.fill(inX, barY, inRight, barY + 8, XP_TRACK);
		int filled = (int) Math.round(progress * (barW - 2));
		if (filled > 0) {
			g.fill(inX + 1, barY + 1, inX + 1 + filled, barY + 7, XP_FILL);
			g.fill(inX + 1, barY + 1, inX + 1 + Math.min(filled, 2), barY + 7, XP_FILL_HI);
		}
		String xpLabel = level >= LevelMath.MAX_LEVEL
				? Component.translatable("gui.alaindustrial.dashboard.maxed").getString()
				: Component.translatable("gui.alaindustrial.dashboard.xp", compact(xp)).getString();
		g.text(this.font, Component.literal(xpLabel), inX, barY + 10, GuiStyle.TEXT_DIM, false);
		return y + h;
	}

	private int drawEnergyModule(GuiGraphicsExtractor g, PlayerModStats stats, int x, int y, int right) {
		int h = 52;
		card(g, x, y, right - x, h);
		int inX = x + 8;
		int inRight = right - 8;
		moduleTitle(g, Component.translatable("gui.alaindustrial.dashboard.energy"), inX, y + 5);
		int rowY = y + 18;
		rowY = statRow(g, Component.translatable("gui.alaindustrial.dashboard.produced_label"),
				compact(stats.euProducedTotal()) + " EU", inX, rowY, inRight);
		rowY = statRow(g, Component.translatable("gui.alaindustrial.dashboard.consumed_label"),
				compact(stats.euUsefulConsumedTotal()) + " EU", inX, rowY, inRight);
		statRow(g, Component.translatable("gui.alaindustrial.dashboard.active_label"),
				formatDuration(stats.activeTicks() / 1200), inX, rowY, inRight);
		return y + h;
	}

	private void drawGeneratorModule(GuiGraphicsExtractor g, PlayerModStats stats, int x, int y, int right, int bottom) {
		int h = Math.max(28, bottom - y);
		card(g, x, y, right - x, h);
		int inX = x + 8;
		int inRight = right - 8;
		moduleTitle(g, Component.translatable("gui.alaindustrial.dashboard.breakdown"), inX, y + 5);
		int rowY = y + 18;
		List<Map.Entry<Identifier, Long>> byGen = stats.producedByGenerator().entrySet().stream()
				.sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
				.toList();
		if (byGen.isEmpty()) {
			g.text(this.font, Component.translatable("gui.alaindustrial.dashboard.breakdown.empty"),
					inX, rowY, GuiStyle.TEXT_DIM, false);
			return;
		}
		for (Map.Entry<Identifier, Long> e : byGen) {
			if (rowY + 16 > y + h - 2) {
				break;
			}
			ItemStack icon = iconFor(e.getKey());
			if (!icon.isEmpty()) {
				g.item(icon, inX, rowY - 4);
			}
			String name = icon.isEmpty() ? e.getKey().toString() : icon.getHoverName().getString();
			String amount = compact(e.getValue()) + " EU";
			g.text(this.font, trimTo(name, inRight - this.font.width(amount) - 6 - (inX + 20)),
					inX + 20, rowY, GuiStyle.TEXT, false);
			g.text(this.font, Component.literal(amount), inRight - this.font.width(amount), rowY, ACCENT, false);
			rowY += 16;
		}
	}

	// --- Drawing helpers ---------------------------------------------------------------------------

	/** A recessed module card (light fill, dark top-left inset, light bottom-right edge). */
	private static void card(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, CARD_FILL);
		g.fill(x, y, x + w, y + 1, GuiStyle.PANEL_LO);
		g.fill(x, y, x + 1, y + h, GuiStyle.PANEL_LO);
		g.fill(x, y + h - 1, x + w, y + h, GuiStyle.PANEL_HI);
		g.fill(x + w - 1, y, x + w, y + h, GuiStyle.PANEL_HI);
	}

	/** A recessed dark frame outline (for the avatar backdrop). */
	private static void inset(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x1, y1, GuiStyle.SLOT_EDGE);
	}

	private static void verticalGradient(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int top, int bottom) {
		int rows = Math.max(1, y1 - y0);
		for (int i = 0; i < rows; i++) {
			g.fill(x0, y0 + i, x1, y0 + i + 1, lerpColor(top, bottom, (float) i / rows));
		}
	}

	private void moduleTitle(GuiGraphicsExtractor g, Component title, int x, int y) {
		g.text(this.font, title, x, y, ACCENT, false);
	}

	/** "label ............ value" row; label left in dim, value right in bright. Returns the next row Y. */
	private int statRow(GuiGraphicsExtractor g, Component label, String value, int x, int y, int right) {
		g.text(this.font, label, x, y, GuiStyle.TEXT_DIM, false);
		g.text(this.font, Component.literal(value), right - this.font.width(value), y, GuiStyle.TEXT, false);
		return y + 11;
	}

	private Component trimTo(String text, int maxWidth) {
		Font f = this.font;
		if (f.width(text) <= maxWidth) {
			return Component.literal(text);
		}
		String ell = "…";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			if (f.width(sb.toString() + text.charAt(i) + ell) > maxWidth) {
				break;
			}
			sb.append(text.charAt(i));
		}
		return Component.literal(sb + ell);
	}

	private static int lerpColor(int a, int b, float t) {
		int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
		int rr = (int) (ar + (br - ar) * t);
		int rg = (int) (ag + (bg - ag) * t);
		int rb = (int) (ab + (bb - ab) * t);
		int ra = (int) (aa + (ba - aa) * t);
		return (ra << 24) | (rr << 16) | (rg << 8) | rb;
	}

	private static ItemStack iconFor(Identifier blockId) {
		return BuiltInRegistries.BLOCK.getOptional(blockId)
				.map(block -> new ItemStack(block.asItem()))
				.orElse(ItemStack.EMPTY);
	}

	private static String compact(long value) {
		if (value < 1000) {
			return Long.toString(value);
		}
		if (value < 1_000_000) {
			return String.format("%.1fk", value / 1000.0);
		}
		if (value < 1_000_000_000) {
			return String.format("%.1fM", value / 1_000_000.0);
		}
		return String.format("%.1fB", value / 1_000_000_000.0);
	}

	private static String formatDuration(long minutes) {
		if (minutes < 60) {
			return minutes + "m";
		}
		return (minutes / 60) + "h " + (minutes % 60) + "m";
	}
}
