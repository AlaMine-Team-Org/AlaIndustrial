package dev.alaindustrial.client.tablet;

/**
 * Where the tablet's screen is drawn while it unfolds and once it is open (MOD-946) — plain arithmetic, shared by the
 * screen (drawing, input) and the hand pose (where the raised tablet sits in view).
 *
 * <p><b>Start: over the raised tablet.</b> The hands are drawn with a fixed 70° field of view whatever the player's
 * FOV setting, so the raised tablet always covers the same share of the view height: {@link #RAISED_WIDTH_OF_VIEW}.
 * The unfolding screen starts at that size, centred where the tablet is, and grows from there.
 *
 * <p><b>End: almost the whole screen, and crisp.</b> The open scale fills {@link #FILL} of the screen and is snapped
 * so that one panel pixel is a whole number of window pixels ({@code floor(fit · guiScale) / guiScale}); a fractional
 * scale would smear the panel's pixel font. The drawing is a uniform scale and a shift, so the inverse
 * ({@link Frame#toPanelX}) maps the mouse back exactly.
 */
public final class ReactorTabletLayout {

	/** Distance of the raised tablet from the eye, in the hand renderer's units (the two-handed map's own). */
	public static final float RAISED_DISTANCE = 0.72f;
	/** How far above the eye line the raised tablet's centre is, same units (the two-handed map's own lift). */
	public static final float RAISED_LIFT = 0.04f;
	/** The tablet model's scale in the hand pose: one model pixel is {@code 2 · MODEL_SCALE / 16} units. */
	public static final float MODEL_SCALE = 0.42f;
	/** The tablet's width in model pixels, grip to grip (the item model, MOD-946 look 02 «with handles»). */
	public static final int MODEL_BODY_WIDTH_PX = 16;

	/** Half the view height at {@link #RAISED_DISTANCE} under the hand renderer's fixed 70° field of view. */
	private static final float VIEW_HALF_HEIGHT = (float) Math.tan(Math.toRadians(35.0)) * RAISED_DISTANCE;
	/** The raised tablet body's width as a share of the view height. */
	public static final float RAISED_WIDTH_OF_VIEW = MODEL_BODY_WIDTH_PX * 2.0f * MODEL_SCALE / 16.0f
			/ (2.0f * VIEW_HALF_HEIGHT);
	/** How far above the screen centre the raised tablet sits, as a share of the view height. */
	private static final float RAISED_CENTRE_ABOVE = RAISED_LIFT / (2.0f * VIEW_HALF_HEIGHT);

	/** The share of the screen the open tablet may fill. */
	public static final float FILL = 0.94f;

	/**
	 * Set only while the tablet's screen draws the panel under its scale: tooltips recorded then are taken through
	 * the pose into screen space (the tooltip mixin of {@code GuiGraphicsExtractor}). Render thread only.
	 */
	public static boolean tooltipsInPose;

	private ReactorTabletLayout() {
	}

	/**
	 * A uniform scale that puts the tablet body's top-left corner, at {@code (pivotX, pivotY)} in the screen's own
	 * layout (as vanilla centres it), at {@code (originX, originY)} on screen.
	 */
	public record Frame(float originX, float originY, float scale, int pivotX, int pivotY) {

		/** Where a point of the panel's layout is drawn, horizontally. */
		public float toScreenX(double x) {
			return (float) (originX + (x - pivotX) * scale);
		}

		/** Where a point of the panel's layout is drawn, vertically. */
		public float toScreenY(double y) {
			return (float) (originY + (y - pivotY) * scale);
		}

		/** The panel-layout point drawn at screen {@code x} — the mouse, mapped back. */
		public double toPanelX(double x) {
			return (x - originX) / scale + pivotX;
		}

		/** The panel-layout point drawn at screen {@code y}. */
		public double toPanelY(double y) {
			return (y - originY) / scale + pivotY;
		}
	}

	/**
	 * The scale of the open tablet: its body ({@code bodyW × bodyH} panel pixels) fills {@link #FILL} of the screen,
	 * snapped to whole window pixels. Never below one window pixel per panel pixel.
	 */
	public static float openScale(int screenW, int screenH, int guiScale, int bodyW, int bodyH) {
		float fit = Math.min(screenW * FILL / bodyW, screenH * FILL / bodyH);
		int windowPixels = Math.max(1, (int) Math.floor(fit * Math.max(1, guiScale)));
		return windowPixels / (float) Math.max(1, guiScale);
	}

	/** The scale at which the body exactly covers the raised tablet in the hands. */
	public static float raisedScale(int screenH, int bodyW) {
		return RAISED_WIDTH_OF_VIEW * screenH / bodyW;
	}

	/**
	 * The frame at {@code unfold} (0 = over the raised tablet, 1 = open) for a body of {@code bodyW × bodyH} panel
	 * pixels whose top-left corner is at {@code (bodyX, bodyY)} in the screen's own layout. The scale grows
	 * geometrically, so the growth looks even rather than racing at the start; the centre moves with it. Open, the
	 * body's corner lands on a whole window pixel, so the panel stays crisp.
	 */
	public static Frame at(float unfold, int screenW, int screenH, int guiScale, int bodyX, int bodyY, int bodyW,
			int bodyH) {
		float u = Math.clamp(unfold, 0.0f, 1.0f);
		float from = raisedScale(screenH, bodyW);
		float to = openScale(screenW, screenH, guiScale, bodyW, bodyH);
		if (u >= 1.0f) {
			int gs = Math.max(1, guiScale);
			float originX = Math.round((screenW - bodyW * to) / 2.0f * gs) / (float) gs;
			float originY = Math.round((screenH - bodyH * to) / 2.0f * gs) / (float) gs;
			return new Frame(originX, originY, to, bodyX, bodyY);
		}
		float scale = (float) (from * Math.pow(to / from, u));
		float raisedY = screenH / 2.0f - RAISED_CENTRE_ABOVE * screenH;
		float centreY = raisedY + (screenH / 2.0f - raisedY) * u;
		return new Frame(screenW / 2.0f - bodyW * scale / 2.0f, centreY - bodyH * scale / 2.0f, scale, bodyX, bodyY);
	}
}
