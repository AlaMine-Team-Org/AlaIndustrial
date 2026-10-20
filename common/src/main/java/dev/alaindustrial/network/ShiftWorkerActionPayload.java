package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfile;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfiles;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S: a change the owner makes from a shift worker's card (MOD-854): one point into a stat, all points back at a
 * capsule, or a name. Nothing here is trusted — {@link ShiftWorkerProfiles} checks owner (R74: others only view),
 * a living record, the viewing distance and the action's own conditions, and replies with a refreshed card.
 *
 * @param number the record number
 * @param action what to do
 * @param stat   the stat, for {@link Action#INVEST}; empty otherwise
 * @param name   the new name, for {@link Action#RENAME}; empty otherwise (the server sanitises and cuts it)
 */
public record ShiftWorkerActionPayload(String number, Action action, Optional<ShiftWorkerProfile.Stat> stat,
		String name) implements CustomPacketPayload {

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
		RENAME
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
			ShiftWorkerActionPayload::new);

	/** One point into {@code stat}. */
	public static ShiftWorkerActionPayload invest(String number, ShiftWorkerProfile.Stat stat) {
		return new ShiftWorkerActionPayload(number, Action.INVEST, Optional.of(stat), "");
	}

	/** Every point back at a capsule. */
	public static ShiftWorkerActionPayload respec(String number) {
		return new ShiftWorkerActionPayload(number, Action.RESPEC, Optional.empty(), "");
	}

	/** A new name. */
	public static ShiftWorkerActionPayload rename(String number, String name) {
		return new ShiftWorkerActionPayload(number, Action.RENAME, Optional.empty(), name);
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
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
