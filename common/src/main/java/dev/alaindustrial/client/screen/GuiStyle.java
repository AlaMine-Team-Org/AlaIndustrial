package dev.alaindustrial.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared visual language for Industrialization machine screens, built on the render-state GUI
 * model ({@link GuiGraphicsExtractor#fill}). The look is a light control-panel face with a bevel
 * and a single copper accent strip under the header (the signature element), recessed slots, and
 * an amber energy bar. Text stays dark for legibility on the light panel (matching vanilla labels).
 */
public final class GuiStyle {
	private GuiStyle() {
	}

	public static final int PANEL = 0xFFC6C6C6;
	public static final int PANEL_HI = 0xFFFDFDFD;
	public static final int PANEL_LO = 0xFF555555;
	public static final int SLOT = 0xFF8B8B8B;
	public static final int SLOT_EDGE = 0xFF373737;
	public static final int TRACK = 0xFF373737;
	public static final int ENERGY = 0xFFFF8A1E;
	public static final int ENERGY_HI = 0xFFFFC15A;
	public static final int COPPER = 0xFFB87333;
	public static final int TEXT = 0xFF3F3F3F;
	public static final int TEXT_DIM = 0xFF6B6B6B;
	/**
	 * The vanilla container-label grey — one step lighter than {@link #TEXT}. Kept apart on purpose: the
	 * screens that print in it today would shift a shade if it were folded into {@code TEXT}.
	 */
	public static final int LABEL = 0xFF404040;
	/** A blocking status caption — dark red, the colour of {@code ChatFormatting.DARK_RED}. */
	public static final int STATUS_BLOCKING = 0xFFAA0000;
	/**
	 * A finished, good status caption (MOD-857) — «intact», a done step, a covered material. A calm ink green that
	 * reads on the light-grey panel and card (owner's round 4 of stage 6: the bright
	 * {@code ChatFormatting.DARK_GREEN} it was hurt the eyes); the same ink as the reactor's and the teleporter's good
	 * lines.
	 */
	public static final int STATUS_GOOD = 0xFF2F6B33;
	/**
	 * The tint that turns the vanilla green tick sprite ({@code container/beacon/confirm}, pure green, brightest 184)
	 * into {@link #STATUS_GOOD}'s green: 0x6B / 184 of full. Only the green channel matters, the sprite has no other.
	 */
	public static final int STATUS_GOOD_TICK = 0xFF2F9433;
	/** Face of an unselected mode button (the sawmill's mode row). */
	public static final int BUTTON = 0xFF2B2B2B;
	/** Face of the selected mode button. */
	public static final int BUTTON_ACTIVE = 0xFF5A4A21;
	/** The 1 px frame around the selected mode button. */
	public static final int BUTTON_ACTIVE_EDGE = 0xFFFFC94A;
	/** Translucent white laid over a button under the cursor, before its icon. */
	public static final int HOVER_WASH = 0x40FFFFFF;

	// Profile cards (MOD-854, the shift worker's card): the player profile's palette, shared by name.
	/** A recessed module card on the panel. */
	public static final int CARD = 0xFFB2B2B2;
	/** The dark header bar of a profile card and the text on it. */
	public static final int HEADER_BAR = 0xFF141414;
	public static final int HEADER_TEXT = 0xFFF4F4F4;
	/** The backdrop behind a model: a cool dark top fading darker to the bottom. */
	public static final int MODEL_BACK_TOP = 0xFF1B1D24;
	public static final int MODEL_BACK_BOTTOM = 0xFF0C0D12;
	/** A level bar: its fill, its bright leading edge and its track. */
	public static final int LEVEL_FILL = 0xFF57C7FF;
	public static final int LEVEL_FILL_HI = 0xFFBFEBFF;
	public static final int LEVEL_TRACK = 0xFF23262B;
	/** Laid over the model of a closed record, so it reads as a photograph in a file. */
	public static final int CLOSED_WASH = 0xB0707070;

	/** Panel face with bevel border and a copper accent strip under the header. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, PANEL);
		g.fill(x, y, x + w, y + 1, PANEL_HI);
		g.fill(x, y, x + 1, y + h, PANEL_HI);
		g.fill(x, y + h - 1, x + w, y + h, PANEL_LO);
		g.fill(x + w - 1, y, x + w, y + h, PANEL_LO);
		g.fill(x + 5, y + 14, x + w - 5, y + 15, COPPER);
	}

	/** Recessed 18×18 slot background. Pass the slot's top-left (slot.x-1, slot.y-1). */
	public static void slot(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y, x + 18, y + 18, SLOT_EDGE);
		g.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
	}

	/** Horizontal progress arrow track + fill (progress/maxProgress). */
	public static void progress(GuiGraphicsExtractor g, int x, int y, int w, int progress, int maxProgress) {
		g.fill(x, y, x + w, y + 6, TRACK);
		int filled = maxProgress > 0 ? progress * w / maxProgress : 0;
		g.fill(x, y, x + filled, y + 6, PANEL_HI);
	}
}
