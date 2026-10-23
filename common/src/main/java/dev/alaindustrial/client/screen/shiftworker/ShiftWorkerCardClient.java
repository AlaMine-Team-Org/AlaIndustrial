package dev.alaindustrial.client.screen.shiftworker;

import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.ShiftWorkerActionPayload;
import dev.alaindustrial.network.ShiftWorkerCardPayload;
import dev.alaindustrial.network.ShiftWorkerRequestPayload;
import dev.alaindustrial.network.ShiftWorkerRosterPayload;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfile;
import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Client end of the shift worker's card and the town roster (MOD-854): keeps the latest card and roster the server
 * sent, opens the card screen when a card arrives with {@code open = true}, and sends the screen's requests and
 * actions. Client thread only.
 *
 * <p><b>The screen plugs in here.</b> An opening card goes to {@link ShiftWorkerCardScreen#show} unless another
 * hook is installed with {@link #onOpenCard}; the open screen reads {@link #latestCard()} every tick for refreshes.
 * The core screen reads {@link #latestRoster()}; {@link #onRoster} is there for whoever needs the arrival itself.
 */
public final class ShiftWorkerCardClient {

	private static @Nullable ShiftWorkerCardPayload latestCard;
	private static @Nullable ShiftWorkerRosterPayload latestRoster;
	private static Consumer<ShiftWorkerCardPayload> openCard = ShiftWorkerCardScreen::show;
	private static Consumer<ShiftWorkerRosterPayload> rosterArrived = roster -> { };

	private ShiftWorkerCardClient() {
	}

	/** The landing point of a card: kept as the latest; an opening one is handed to the installed screen hook. */
	public static void receiveCard(ShiftWorkerCardPayload card) {
		latestCard = card;
		if (card.open()) {
			openCard.accept(card);
		}
	}

	/** The landing point of a roster: kept as the latest and handed to the installed hook. */
	public static void receiveRoster(ShiftWorkerRosterPayload roster) {
		latestRoster = roster;
		rosterArrived.accept(roster);
	}

	/** The card screen's hook: called with every card the server sends opening. */
	public static void onOpenCard(Consumer<ShiftWorkerCardPayload> hook) {
		openCard = Objects.requireNonNull(hook, "hook");
	}

	/** The core screen's hook: called with every roster the server sends. */
	public static void onRoster(Consumer<ShiftWorkerRosterPayload> hook) {
		rosterArrived = Objects.requireNonNull(hook, "hook");
	}

	/** The latest card, or null before the first one. */
	public static @Nullable ShiftWorkerCardPayload latestCard() {
		return latestCard;
	}

	/** The latest roster, or null before the first one. */
	public static @Nullable ShiftWorkerRosterPayload latestRoster() {
		return latestRoster;
	}

	/**
	 * Forgets the roster only: a core screen that opens calls this, so the roster it shows is one the server sent
	 * while it was open, never the previous core's.
	 */
	public static void forgetRoster() {
		latestRoster = null;
	}

	/** Forgets both (a disconnect, or a screen that wants a clean start). */
	public static void clear() {
		latestCard = null;
		latestRoster = null;
	}

	/** Asks the server for the card of {@code number} (a roster row clicked); it arrives opening. */
	public static void requestCard(String number) {
		NetworkDispatcher.get().sendToServer(ShiftWorkerRequestPayload.card(number));
	}

	/**
	 * Asks the server for the roster of the town whose core screen is open. The request names no core: the server
	 * answers for the menu the player has open, and only while it is a settlement core's.
	 */
	public static void requestRoster() {
		NetworkDispatcher.get().sendToServer(ShiftWorkerRequestPayload.roster());
	}

	/** «Find» on a roster row (R4-13): the worker is outlined for a few seconds. */
	public static void locate(String number) {
		NetworkDispatcher.get().sendToServer(ShiftWorkerActionPayload.locate(number));
	}

	/** «+» on a stat. */
	public static void invest(String number, ShiftWorkerProfile.Stat stat) {
		NetworkDispatcher.get().sendToServer(ShiftWorkerActionPayload.invest(number, stat));
	}

	/** Take every point back at a capsule. */
	public static void respec(String number) {
		NetworkDispatcher.get().sendToServer(ShiftWorkerActionPayload.respec(number));
	}

	/** A new name. */
	public static void rename(String number, String name) {
		NetworkDispatcher.get().sendToServer(ShiftWorkerActionPayload.rename(number, name));
	}

	/** Into free house {@code house} of the worker's town (MOD-862). */
	public static void relocate(String number, int house) {
		NetworkDispatcher.get().sendToServer(ShiftWorkerActionPayload.relocate(number, house));
	}
}
