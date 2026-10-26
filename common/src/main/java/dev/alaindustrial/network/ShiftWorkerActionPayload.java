package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.shift.ShiftWorkerLocator;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfile;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfiles;
import dev.alaindustrial.settlement.works.TownWorkplaces;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S: a change the owner makes from a shift worker's card (MOD-854): one point into a stat, all points back at a
 * capsule, a name, another house (MOD-862), an outline to find it by ({@link ShiftWorkerLocator}, R4-13), or off work
 * and back (MOD-888).
 * Nothing here is trusted — {@link ShiftWorkerProfiles} checks owner
 * (R74: others only view), a living record, the viewing distance and the action's own conditions, and replies with
 * a refreshed card.
 *
 * @param number the record number
 * @param action what to do
 * @param stat   the stat, for {@link Action#INVEST}; empty otherwise
 * @param name   the new name, for {@link Action#RENAME}; empty otherwise (the server sanitises and cuts it)
 * @param house  the house to move into, for {@link Action#RELOCATE} (its index in the town, MOD-862); -1 otherwise
 */
public record ShiftWorkerActionPayload(String number, Action action, Optional<ShiftWorkerProfile.Stat> stat,
		String name, int house) implements CustomPacketPayload {

	public static final Type<ShiftWorkerActionPayload> TYPE = new Type<>(Industrialization.id("shift_worker_action"));

	/** The most characters a name keeps; a longer one is cut by the server. */
	public static final int NAME_LIMIT = 16;

	/** What the owner does. */
	public enum Action {
		/** One free point into {@link #stat}. */
		INVEST,
		/** Every invested point back, paid at a capsule of the worker's town (R69). */
		RESPEC,
		/** A new name; an empty one clears it. */
		RENAME,
		/** Into another free house of its town (MOD-862, R102). */
		RELOCATE,
		/** Outline it for a few seconds, seen through walls, so it can be found (R4-13). */
		LOCATE,
		/** Off work, or back to work once taken off (MOD-888). */
		TOGGLE_WORK
	}

	private static final Action[] ACTIONS = Action.values();
	private static final ShiftWorkerProfile.Stat[] STATS = ShiftWorkerProfile.Stat.values();
	private static final StreamCodec<ByteBuf, Action> ACTION = ByteBufCodecs.idMapper(
			ordinal -> ordinal >= 0 && ordinal < ACTIONS.length ? ACTIONS[ordinal] : Action.RENAME, Action::ordinal);
	// An unknown stat ordinal reads as STRENGTH; the server re-checks every condition anyway.
	private static final StreamCodec<ByteBuf, ShiftWorkerProfile.Stat> STAT = ByteBufCodecs.idMapper(
			ordinal -> ordinal >= 0 && ordinal < STATS.length ? STATS[ordinal] : STATS[0],
			ShiftWorkerProfile.Stat::ordinal);

	public static final StreamCodec<RegistryFriendlyByteBuf, ShiftWorkerActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(ShiftWorkerCardPayload.TEXT_LIMIT), ShiftWorkerActionPayload::number,
			ACTION, ShiftWorkerActionPayload::action,
			ByteBufCodecs.optional(STAT), ShiftWorkerActionPayload::stat,
			ByteBufCodecs.stringUtf8(ShiftWorkerCardPayload.TEXT_LIMIT), ShiftWorkerActionPayload::name,
			ByteBufCodecs.VAR_INT, ShiftWorkerActionPayload::house,
			ShiftWorkerActionPayload::new);

	/** One point into {@code stat}. */
	public static ShiftWorkerActionPayload invest(String number, ShiftWorkerProfile.Stat stat) {
		return new ShiftWorkerActionPayload(number, Action.INVEST, Optional.of(stat), "", -1);
	}

	/** Every point back at a capsule. */
	public static ShiftWorkerActionPayload respec(String number) {
		return new ShiftWorkerActionPayload(number, Action.RESPEC, Optional.empty(), "", -1);
	}

	/** A new name. */
	public static ShiftWorkerActionPayload rename(String number, String name) {
		return new ShiftWorkerActionPayload(number, Action.RENAME, Optional.empty(), name, -1);
	}

	/** Into free house {@code house} of the worker's town. */
	public static ShiftWorkerActionPayload relocate(String number, int house) {
		return new ShiftWorkerActionPayload(number, Action.RELOCATE, Optional.empty(), "", house);
	}

	/** Outline the worker for a few seconds. */
	public static ShiftWorkerActionPayload locate(String number) {
		return new ShiftWorkerActionPayload(number, Action.LOCATE, Optional.empty(), "", -1);
	}

	/** Off work, or back to work (MOD-888). */
	public static ShiftWorkerActionPayload toggleWork(String number) {
		return new ShiftWorkerActionPayload(number, Action.TOGGLE_WORK, Optional.empty(), "", -1);
	}

	/** Server side: hands the action to the profile service, which validates, applies and refreshes the card. */
	public static void handle(ShiftWorkerActionPayload payload, ServerPlayer player) {
		Optional<RecordNumber> number = RecordNumber.parse(payload.number());
		if (number.isEmpty()) {
			return;
		}
		switch (payload.action()) {
			case INVEST -> payload.stat().ifPresent(stat -> ShiftWorkerProfiles.invest(player, number.get(), stat));
			case RESPEC -> ShiftWorkerProfiles.respec(player, number.get());
			case RENAME -> ShiftWorkerProfiles.rename(player, number.get(), payload.name());
			case RELOCATE -> ShiftWorkerProfiles.relocate(player, number.get(), payload.house());
			case LOCATE -> ShiftWorkerLocator.locate(player, number.get());
			case TOGGLE_WORK -> TownWorkplaces.toggleWork(player, number.get());
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
