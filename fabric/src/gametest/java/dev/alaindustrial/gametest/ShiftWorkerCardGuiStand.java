package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.VisualStandSupport.takeCleanScreenshot;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.client.screen.shiftworker.ShiftWorkerCardScreen;
import dev.alaindustrial.network.ShiftWorkerCardPayload;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfile.ServiceMark;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfile.ServiceMark.Kind;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The shift worker's card, photographed (MOD-854): its three tabs for a living worker the viewer owns, then the
 * card of a dead worker under its stamp, closed for good and waiting to be brought back.
 *
 * <p>A plain {@code Screen}, not a menu, so it is outside {@code ScreensClientGameTest} and its parity check, like
 * the skill wheel ({@link SkillTreeGuiStand}). The cards are built here as data and pushed straight in: the frame is
 * about the screen's layout — every row inside its module, the «+» on its stat's row, nothing clipped — and a card
 * the server would send carries exactly these fields. The number belongs to no record, so the screen's own refresh
 * requests go unanswered and the frame keeps the card it was given.
 */
public final class ShiftWorkerCardGuiStand {

	private ShiftWorkerCardGuiStand() {
	}

	private static final Logger LOG = LoggerFactory.getLogger("alaindustrial-gametest");
	private static final String NUMBER = "Q-901";
	private static final Identifier LOOK = Industrialization.id("open_f3");
	private static final long DAY = 24000L;

	public static void shootCards(ClientGameTestContext context) {
		ShiftWorkerCardPayload alive = card(ShiftWorkerRecord.Status.ALIVE, Optional.empty(), 0L);
		open(context, alive, ShiftWorkerCardScreen.Tab.CARD, "gui_shift_worker_card_tab_card");
		open(context, alive, ShiftWorkerCardScreen.Tab.FILE, "gui_shift_worker_card_tab_file");
		open(context, alive, ShiftWorkerCardScreen.Tab.NUMBER, "gui_shift_worker_card_tab_number");
		Optional<ShiftWorkerRecord.Death> death = Optional.of(new ShiftWorkerRecord.Death(
				Identifier.withDefaultNamespace("mob_attack"), Optional.of(Identifier.withDefaultNamespace("zombie")),
				41 * DAY + 900));
		open(context, card(ShiftWorkerRecord.Status.DEAD, death, 0L), ShiftWorkerCardScreen.Tab.CARD,
				"gui_shift_worker_card_closed");
		open(context, card(ShiftWorkerRecord.Status.AWAITING_RETURN, death, 30000L), ShiftWorkerCardScreen.Tab.CARD,
				"gui_shift_worker_card_awaiting");
		context.runOnClient(mc -> mc.setScreenAndShow(null));
		context.waitTicks(3);
	}

	private static void open(ClientGameTestContext context, ShiftWorkerCardPayload card, ShiftWorkerCardScreen.Tab tab,
			String frame) {
		context.runOnClient(mc -> {
			ShiftWorkerCardScreen screen = new ShiftWorkerCardScreen(card);
			mc.setScreenAndShow(screen);
			screen.selectTab(tab);
		});
		context.waitFor(mc -> mc.gui.screen() instanceof ShiftWorkerCardScreen open && open.tab() == tab);
		context.waitTicks(2);
		LOG.info("[GUITEST][R-GUI-03] {} -> {}", frame, takeCleanScreenshot(context, frame).toAbsolutePath());
	}

	/** A worker of level 6 with three free points, two in «Strength», a history of marks and one former number. */
	private static ShiftWorkerCardPayload card(ShiftWorkerRecord.Status status,
			Optional<ShiftWorkerRecord.Death> death, long returnPrice) {
		boolean alive = status == ShiftWorkerRecord.Status.ALIVE;
		List<ServiceMark> marks = List.of(
				new ServiceMark(Kind.ISSUED, 12 * DAY, List.of()),
				new ServiceMark(Kind.NAMED, 12 * DAY + 400, List.of("Anya")),
				new ServiceMark(Kind.LEVEL, 15 * DAY, List.of("2")),
				new ServiceMark(Kind.WOUNDED, 18 * DAY, List.of("minecraft:zombie", "4", "24")),
				new ServiceMark(Kind.RENUMBERED, 22 * DAY, List.of("Q-090", NUMBER)),
				new ServiceMark(Kind.LEVEL, 27 * DAY, List.of("6")),
				new ServiceMark(Kind.RANK, 27 * DAY, List.of("2")));
		List<Component> biography = List.of(
				Component.literal("Taken in from a settlement by a salt lake. Earlier place of record unknown."),
				Component.literal("Kept the first-line pumps running. Full clearance for oil."),
				Component.literal("Checks the charge twice before going out."),
				Component.literal("Remembers a blue door. Does not remember where it was."));
		return new ShiftWorkerCardPayload(true, -1, NUMBER, "Anya", true, LOOK, status, 3,
				Component.literal("Plains works town"), alive, alive ? 17400L : 0L, 30000L,
				alive ? 24.0F : 0.0F, 24.0F, 112400L, 6, 20L, 30L, 1, 3, 10, new int[] {2, 0, 0, 0},
				new boolean[] {true, true, false, false}, biography, marks, 12 * DAY,
				List.of(new ShiftWorkerCardPayload.FormerNumber("Q-090", 22 * DAY)), death, returnPrice, alive,
				alive, 50000L);
	}
}
