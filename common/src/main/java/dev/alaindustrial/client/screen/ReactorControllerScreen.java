package dev.alaindustrial.client.screen;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.ReactorRoomStatus;
import dev.alaindustrial.menu.ReactorControllerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Reactor Controller (MOD-468) — the room's build assistant while it is being put
 * together, its control desk once it runs.
 *
 * <p><b>The panel shows one of two layouts, never both.</b> The usable strip ends where the art's
 * boxes end (see {@link #CONTENT_BOTTOM}), and the first version tried to fit a
 * status line, four data rows, a heat gauge and five throttle buttons into it. They overlapped into
 * an unreadable mess. They also answer different questions at different times: while the shell is
 * open the player needs to know what is missing and where, and none of the reactor's numbers exist
 * yet; once it is sealed the fault line has nothing to say and the numbers are the whole point. So
 * the screen picks the layout that matches the state, and each one has room to breathe.
 *
 * <p><b>Every row is label-column plus value-column</b>, so nothing depends on how long a translated
 * label turns out to be — an earlier version put label and value in one string and Russian ran into
 * the pictogram. Values that do not fit are scaled down rather than clipped, the rule the machine
 * family's status line has always used.
 *
 * <p><b>The throttle rides the vanilla container-button channel</b> ({@code handleInventoryButtonClick}
 * → {@link ReactorControllerMenu#clickMenuButton}), like the repeller's dome toggle: the request
 * carries no data beyond "this container, this number", so no payload of our own is needed. The
 * button id IS the requested depth in percent.
 */
public class ReactorControllerScreen extends MachineScreen<ReactorControllerMenu> {

	private static final Identifier TEXTURE =
			Industrialization.id("textures/gui/container/reactor_controller.png");

	/*
	 * Layout metrics. Public for symmetry with the rest of the screen family; the reactor has no
	 * text-row stand of its own, so nothing outside this class reads these numbers today. Should one be
	 * added, it must measure the strip from here rather than from a copy that would drift the first time
	 * the layout moves.
	 *
	 * The hard bound is CONTENT_BOTTOM, and it comes from the art rather than from the inventory
	 * label: vanilla draws that label at {@code imageHeight - 94}, which on this 228-tall panel is
	 * y=134 — far below anything here. Every constant is checked against the frame lines instead.
	 */

	/**
	 * The art now draws TWO framed boxes, and the numbers below are read off it rather than chosen:
	 * the readout box runs to its border at y=87 and the throttle has a box of its own from y=96.
	 * Elements are placed inside those borders, not merely above the
	 * inventory — the previous version fitted the space and still looked wrong, because the coolant
	 * row was sitting on top of a frame line.
	 */
	public static final int CONTENT_BOTTOM = 86;

	/** {@link #LABEL_X} clears the room pictogram baked into the atlas at x=8. */
	public static final int LABEL_X = 24;
	public static final int VALUE_X = 98;
	public static final int VALUE_RIGHT = 168;

	/** The status headline, first thing on the panel in both layouts. */
	public static final int STATUS_Y = 21;
	/**
	 * The accident countdown bar, in the gap the headline leaves above the first data row.
	 *
	 * <p>Three pixels tall and full width: it has to be visible from the corner of the eye while the
	 * player is reading the rows below it, and it carries no text, so it needs no more height than that.
	 */
	public static final int COUNTDOWN_Y = 31;
	public static final int COUNTDOWN_H = 3;

	/** Where the data rows begin, below the headline. */
	public static final int ROW_TOP = 35;
	public static final int ROW_STEP = 12;

	/**
	 * The two gauges — heat and coolant — sit below the text rows on the same grid.
	 *
	 * <p>Both are bars rather than more text rows, and that is a space decision as much as a design
	 * one: five text rows, a full-width bar and the throttle do not fit in seventy pixels, and the two
	 * numbers that want watching continuously are exactly the two that read better as a filling bar
	 * than as a figure to parse.
	 */
	public static final int GAUGE_TOP = 61;
	public static final int GAUGE_STEP = 13;
	public static final int BAR_X = 98;
	public static final int BAR_W = 70;
	public static final int BAR_H = 6;

	/**
	 * Throttle strip along the bottom of the content area: five stops, 28 px each. Inside the throttle's
	 * own frame (y 96…113), centred in it rather than aligned to the text.
	 */
	public static final int THROTTLE_Y = 98;
	public static final int THROTTLE_X = 16;
	public static final int THROTTLE_W = 28;
	public static final int THROTTLE_H = 12;

	/**
	 * The charge gauge, in the strip the art leaves between the throttle's frame and the inventory
	 * label. It is the answer to "where does the energy go": the reactor banks what nothing is drawing,
	 * and until this row existed a player watching a working reactor had no sign of it anywhere.
	 */
	public static final int ENERGY_Y = 116;
	/** Tall enough to carry its own label — see {@code drawStoredEnergy}. */
	public static final int CHARGE_BAR_H = 11;
	private static final int[] THROTTLE_STOPS = {0, 25, 50, 75, 100};

	/** Stands in for a value the controller cannot report in the current state. */
	private static final String DASH = "—";

	/** Panel size. Taller than the family default: the reactor has more to say than a machine does. */
	public static final int IMAGE_WIDTH = 176;
	public static final int IMAGE_HEIGHT = 228;

	public ReactorControllerScreen(ReactorControllerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
	}

	@Override
	protected Identifier texture() {
		return TEXTURE;
	}

	@Override
	protected void drawMachineFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		blitStaticFrame(graphics);

		ReactorRoomStatus status = this.menu.getStatus();
		drawStatusHeadline(graphics, status);
		drawCountdown(graphics);
		if (status == ReactorRoomStatus.FORMED) {
			drawRunningLayout(graphics);
		} else {
			// The building diagnostics come FIRST and are never replaced (MOD-469). Bare mode fires on
			// exactly the same condition as an unfinished shell — "not FORMED" — so a layout that swapped
			// one for the other would take the size, the fault and the direction-to-the-hole away from the
			// player who is genuinely still building, in order to serve the player who never intended to.
			// The bare readout is added BELOW that, and only once racks have actually been found.
			int row = drawBuildingLayout(graphics, status);
			if (this.menu.isBare()) {
				drawBareLayout(graphics, row);
			}
			drawStoredEnergy(graphics);
		}
	}

	/**
	 * The verdict, centred at the top of the panel — the one thing shown in every layout.
	 *
	 * <p>A room melting its own contents says so HERE rather than in a row of its own, and overrides the
	 * geometric verdict while it does. A shell that is still perfectly sealed is telling the truth when
	 * it reports itself assembled, and it is the least useful true thing the panel could be saying at
	 * that moment.
	 */
	private void drawStatusHeadline(GuiGraphicsExtractor graphics, ReactorRoomStatus status) {
		if (this.menu.getBlastPercent() > 0) {
			// Above the meltdown line, and above everything else. A room that is melting its contents is
			// in trouble; a room counting down has minutes to live, and nothing else on the panel is worth
			// reading first.
			drawFittedStatus(graphics,
					Component.translatable("gui.alaindustrial.reactor_controller.status.blast")
							.withStyle(ChatFormatting.DARK_RED),
					STATUS_Y, STATUS_ROW_LEFT, STATUS_ROW_RIGHT, 0xFF8A1010);
			return;
		}
		if (this.menu.isMeltingDown()) {
			drawFittedStatus(graphics,
					Component.translatable("gui.alaindustrial.reactor_controller.status.meltdown")
							.withStyle(ChatFormatting.DARK_RED),
					STATUS_Y, STATUS_ROW_LEFT, STATUS_ROW_RIGHT, 0xFF7A2020);
			return;
		}
		if (this.menu.isBare()) {
			// Amber, not red, and it replaces the geometric verdict rather than sitting under it. A bare
			// reactor standing in open ground reports CONTROLLER_NOT_IN_WALL, which is perfectly true and
			// reads as a fault the player is expected to fix — when in fact they chose this. Saying BARE
			// MODE instead names the state as a state. It also buys the row the readout needs: the panel's
			// content band ends at y=86, and status headline + two building rows + three bare rows would
			// have run the last line past the frame.
			drawFittedStatus(graphics,
					Component.translatable("gui.alaindustrial.reactor_controller.mode.bare")
							.withStyle(ChatFormatting.GOLD),
					STATUS_Y, STATUS_ROW_LEFT, STATUS_ROW_RIGHT, 0xFF7A5A20);
			return;
		}
		Component headline = Component.translatable(status.translationKey())
				.withStyle(status.needsAttention() ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GREEN);
		drawFittedStatus(graphics, headline, STATUS_Y, STATUS_ROW_LEFT, STATUS_ROW_RIGHT,
				status.needsAttention() ? 0xFF7A2020 : 0xFF1F6B2A);
	}

	/**
	 * The bare reactor's readout: what it found and what it is making (MOD-469).
	 *
	 * <p>No heat gauge, no coolant gauge, no throttle — a bare core tracks no temperature, has nothing
	 * plumbed to it and ignores the control rods entirely. Drawing those as zeroes would be worse than
	 * leaving them out: a temperature bar sitting at nothing reads as a cold reactor rather than as a
	 * reactor with no thermometer, and it would invite a player to plumb a loop that can never engage.
	 *
	 * <p>Two rows, not three: the mode itself is announced by the headline, which both frees the row
	 * that would have pushed the last line out of the content band and puts the word where a player
	 * looks first.
	 *
	 * @param row the first free row under whatever the building diagnostics printed
	 */
	private void drawBareLayout(GuiGraphicsExtractor graphics, int row) {
		drawRow(graphics, row++,
				Component.translatable("gui.alaindustrial.reactor_controller.label.rods"),
				Component.literal(Integer.toString(this.menu.getRods())));
		drawRow(graphics, row++,
				Component.translatable("gui.alaindustrial.reactor_controller.label.output"),
				this.menu.getOutput() > 0
						? Component.translatable("gui.alaindustrial.reactor_controller.eu_per_tick",
								this.menu.getOutput())
						: Component.translatable(this.menu.getIdleReason().translationKey())
								.withStyle(ChatFormatting.DARK_RED));
		// MOD-471. MOD-469 deliberately gave the bare layout no gauges, because a bare core tracked no
		// temperature and a bar sitting at zero would have read as a cold reactor rather than as a
		// reactor with no thermometer. That reasoning inverts the moment a scale exists: a hidden scale
		// that kills you is exactly what MOD-469 refused to do with the throttle. So the pile's
		// instability is shown, and the four-rack cliff becomes something the player can see coming.
		int instability = Math.min(100, Math.max(0, this.menu.getInstabilityPercent()));
		drawRow(graphics, row,
				Component.translatable("gui.alaindustrial.reactor_controller.label.instability"),
				Component.translatable("gui.alaindustrial.reactor_controller.percent", instability)
						.withStyle(instability >= 100 ? ChatFormatting.DARK_RED
								: instability >= 80 ? ChatFormatting.GOLD : ChatFormatting.DARK_GREEN));
	}

	/**
	 * The accident countdown: a bar that empties, with no figure on it.
	 *
	 * <p><b>The missing number is the feature.</b> Every accident rolls its own length between two and
	 * three minutes, so that a player cannot learn "half a minute" once and stop reading the panel.
	 * Printing the seconds would hand that knowledge straight back and make the roll pointless. A
	 * draining bar says "you are running out of time" — which is the true and useful part — without
	 * saying how much is left.
	 *
	 * <p>Drawn in every layout, because the accident does not care which one is up: a breached room
	 * running bare can be counting down just as a sealed one can.
	 */
	private void drawCountdown(GuiGraphicsExtractor graphics) {
		int percent = this.menu.getBlastPercent();
		if (percent <= 0) {
			return;
		}
		int left = this.leftPos + LABEL_X;
		int top = this.topPos + COUNTDOWN_Y;
		int width = VALUE_RIGHT - LABEL_X;
		graphics.fill(left, top, left + width, top + COUNTDOWN_H, 0xFF2A2D33);
		int filled = width * Math.min(100, percent) / 100;
		if (filled > 0) {
			graphics.fill(left, top, left + filled, top + COUNTDOWN_H, 0xFFD63A2A);
		}
	}

	/**
	 * While the shell is unfinished: what was measured and where the problem is. The reactor's own
	 * numbers are deliberately absent — there is no reactor yet, and printing zeroes for it would read
	 * as measurement rather than as absence.
	 */
	private int drawBuildingLayout(GuiGraphicsExtractor graphics, ReactorRoomStatus status) {
		int row = 0;
		drawRow(graphics, row++,
				Component.translatable("gui.alaindustrial.reactor_controller.label.room"),
				status.hasSize() && this.menu.getSizeX() > 0
						? Component.translatable("gui.alaindustrial.reactor_controller.size",
								this.menu.getSizeX(), this.menu.getSizeY(), this.menu.getSizeZ())
						: Component.literal(DASH));
		if (status.hasLocation()) {
			drawRow(graphics, row++,
					Component.translatable("gui.alaindustrial.reactor_controller.label.where"),
					describeOffset());
		}
		return row;
	}

	/** Once it runs: what it is doing, and the throttle to change it. */
	private void drawRunningLayout(GuiGraphicsExtractor graphics) {
		int row = 0;
		drawRow(graphics, row++,
				Component.translatable("gui.alaindustrial.reactor_controller.label.rods"),
				Component.literal(Integer.toString(this.menu.getRods())));
		drawRow(graphics, row++,
				Component.translatable("gui.alaindustrial.reactor_controller.label.output"),
				this.menu.getOutput() > 0
						? Component.translatable("gui.alaindustrial.reactor_controller.eu_per_tick",
								this.menu.getOutput())
						// Not a dash: a sealed, fuelled, silent reactor is the state that most needs an
						// explanation, and every cause has a different fix.
						: Component.translatable(this.menu.getIdleReason().translationKey())
								.withStyle(ChatFormatting.DARK_RED));

		int heat = Math.min(100, Math.max(0, this.menu.getHeatPercent()));
		drawGauge(graphics, 0,
				Component.translatable("gui.alaindustrial.reactor_controller.label.heat", heat),
				heat, heatColour(heat));

		// The coolant gauge carries the boil rate in its label, not in a row of its own: the level says
		// whether the loop is holding, the rate says what an inlet has to keep up with, and a player
		// sizing their plumbing needs both at once.
		int water = Math.min(100, Math.max(0, this.menu.getWaterPercent()));
		drawGauge(graphics, 1,
				Component.translatable("gui.alaindustrial.reactor_controller.label.water",
						water, this.menu.getWaterRate()),
				water, coolantColour(water, this.menu.getSteamPercent()));

		drawThrottle(graphics);
		drawStoredEnergy(graphics);
	}

	/**
	 * Buffer charge: label with the figure on the left, a filling bar on the right.
	 *
	 * <p>Drawn in BOTH layouts, unlike everything else here — a reactor that has been scrammed, or whose
	 * room has just been breached, still holds whatever it banked, and that is exactly when a player
	 * wants to know how much is left.
	 */
	private void drawStoredEnergy(GuiGraphicsExtractor graphics) {
		int percent = Math.min(100, Math.max(0, this.menu.getStoredPercent()));
		// Full width, with the figure written ON the bar rather than beside it. Beside it was the first
		// try and it broke on its own success: at 200 000 EU the label outgrew its column and ran under
		// the gauge. A number that only misbehaves once the reactor is full is the worst kind.
		int left = this.leftPos + LABEL_X;
		int top = this.topPos + ENERGY_Y;
		int width = VALUE_RIGHT - LABEL_X;
		graphics.fill(left, top, left + width, top + CHARGE_BAR_H, 0xFF2A2D33);
		int filled = width * percent / 100;
		if (filled > 0) {
			graphics.fill(left, top, left + filled, top + CHARGE_BAR_H, 0xFFD9A33A);
		}
		Component label = Component.translatable("gui.alaindustrial.reactor_controller.label.stored",
				percent, this.menu.getStoredEu());
		int textWidth = this.font.width(label);
		// Drawn with a shadow: the strip is amber on the left and near-black on the right, and no flat
		// colour reads over both.
		graphics.text(this.font, label, left + (width - textWidth) / 2,
				top + (CHARGE_BAR_H - 8) / 2, 0xFFF2F4F5, true);
	}

	/** One gauge: its label on the left, a filling bar in the value column. */
	private void drawGauge(GuiGraphicsExtractor graphics, int index, Component label, int percent,
			int colour) {
		int y = GAUGE_TOP + index * GAUGE_STEP;
		drawFittedStatus(graphics, label, y, LABEL_X, BAR_X - 4, GuiStyle.TEXT_DIM);
		int left = this.leftPos + BAR_X;
		int top = this.topPos + y;
		graphics.fill(left, top, left + BAR_W, top + BAR_H, 0xFF2A2D33);
		int filled = BAR_W * percent / 100;
		if (filled > 0) {
			graphics.fill(left, top, left + filled, top + BAR_H, colour);
		}
	}

	/**
	 * Green while there is head room, amber as it climbs, red near the top — an incandescence ramp
	 * rather than a cool-to-warm one, for the reason the heater's thermometer documents: the straight
	 * line from blue to orange runs through grey and reads as dirty rather than as warm.
	 *
	 * <p>The amber step is {@code Config.reactorHeatWarnPercent} — the same number the block entity
	 * treats as "running hot". It was hardcoded at 60, which made the config key decorative and let the
	 * gauge disagree with the machine it displays.
	 */
	/**
	 * Teal while the loop turns over, amber once the steam has nowhere to go, red when the columns
	 * are dry. The amber case is the one worth the code: with the exhaust blocked the coolant gauge
	 * sits reassuringly full while the temperature climbs, and nothing else on the panel says why.
	 */
	private static int coolantColour(int water, int steam) {
		if (water == 0) {
			return 0xFFD63A2A;
		}
		return steam >= 90 ? 0xFFD9A33A : 0xFF3E8FA8;
	}

	private static int heatColour(int percent) {
		int warn = Config.reactorHeatWarnPercent;
		int critical = Math.min(100, warn + (100 - warn) / 2);
		return percent >= critical ? 0xFFD63A2A : percent >= warn ? 0xFFD9A33A : 0xFF4E9E52;
	}

	/** Five throttle stops; the active one is lit, so the current setting is obvious at a glance. */
	private void drawThrottle(GuiGraphicsExtractor graphics) {
		int depth = this.menu.getDepthPercent();
		for (int i = 0; i < THROTTLE_STOPS.length; i++) {
			int bx = this.leftPos + THROTTLE_X + i * (THROTTLE_W + 1);
			int by = this.topPos + THROTTLE_Y;
			boolean active = THROTTLE_STOPS[i] == depth;
			graphics.fill(bx, by, bx + THROTTLE_W, by + THROTTLE_H, active ? 0xFF2E6E7A : 0xFF3A3E45);
			Component label = Component.literal(THROTTLE_STOPS[i] + "%");
			int tw = this.font.width(label);
			graphics.text(this.font, label, bx + (THROTTLE_W - tw) / 2, by + 2,
					active ? 0xFFE8F6F8 : 0xFFB9C0C7, false);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Only while the reactor exists: the throttle is not drawn in the building layout, so a click
		// there must not silently move control rods the player cannot see.
		if (event.button() == 0 && this.menu.getStatus() == ReactorRoomStatus.FORMED
				&& this.minecraft != null && this.minecraft.gameMode != null) {
			for (int i = 0; i < THROTTLE_STOPS.length; i++) {
				int bx = this.leftPos + THROTTLE_X + i * (THROTTLE_W + 1);
				int by = this.topPos + THROTTLE_Y;
				if (event.x() >= bx && event.x() < bx + THROTTLE_W
						&& event.y() >= by && event.y() < by + THROTTLE_H) {
					this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
							THROTTLE_STOPS[i]);
					this.minecraft.getSoundManager().play(
							SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/**
	 * Turns the offset into words: "4 east, 2 up, 3 south". Axes with no offset are dropped, so a
	 * breach straight above reads "2 up" rather than "0 east, 2 up, 0 south".
	 */
	private Component describeOffset() {
		int dx = this.menu.getBreachDx();
		int dy = this.menu.getBreachDy();
		int dz = this.menu.getBreachDz();
		Component result = null;
		result = append(result, dx, "east", "west");
		result = append(result, dy, "up", "down");
		result = append(result, dz, "south", "north");
		return result == null
				? Component.translatable("gui.alaindustrial.reactor_controller.here")
				: result;
	}

	private Component append(Component soFar, int amount, String positiveKey, String negativeKey) {
		if (amount == 0) {
			return soFar;
		}
		Component piece = Component.translatable(
				"gui.alaindustrial.reactor_controller.dir." + (amount > 0 ? positiveKey : negativeKey),
				Math.abs(amount));
		return soFar == null ? piece : soFar.copy().append(", ").append(piece);
	}

	/** One table row: a dim label in its column, the value shrunk to fit in its own. */
	private void drawRow(GuiGraphicsExtractor graphics, int row, Component label, Component value) {
		int rowY = ROW_TOP + row * ROW_STEP;
		graphics.text(this.font, label, this.leftPos + LABEL_X, this.topPos + rowY, GuiStyle.TEXT_DIM, false);
		drawFittedStatus(graphics, value, rowY, VALUE_X, VALUE_RIGHT, GuiStyle.TEXT);
	}
}
