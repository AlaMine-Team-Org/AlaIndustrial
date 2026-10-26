package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.works.ConstructionRow;
import dev.alaindustrial.settlement.works.NeedIcon;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: what one town needs and its chronicle (MOD-886) — sent to ONE player, as the reply to a
 * {@link TownPulseRequestPayload}. Words only: the client knows no building, job or worker.
 *
 * @param settlement the town's number
 * @param pulsed     whether the town's pulse has a reading — false for a dark core or one that has not looked yet
 * @param needs      what the town needs, most pressing first; the first is the core tab's one line
 * @param chronicle  the town's chronicle, newest line first
 * @param icons      the picture of each need, in the order of {@code needs} ({@link NeedIcon}; MOD-890), an empty
 *                   string for none
 */
public record TownPulsePayload(int settlement, boolean pulsed, List<ConstructionRow.Text> needs,
		List<Line> chronicle, List<String> icons) implements CustomPacketPayload {

	public static final Type<TownPulsePayload> TYPE = new Type<>(Industrialization.id("town_pulse"));

	/** Most needs one reply carries. */
	public static final int MAX_NEEDS = 16;
	/** Most chronicle lines one reply carries: a whole chronicle. */
	public static final int MAX_LINES = 256;

	public TownPulsePayload {
		needs = List.copyOf(needs.subList(0, Math.min(MAX_NEEDS, needs.size())));
		chronicle = List.copyOf(chronicle.subList(0, Math.min(MAX_LINES, chronicle.size())));
		List<String> pictures = new ArrayList<>(needs.size());
		for (int i = 0; i < needs.size(); i++) {
			pictures.add(i < icons.size() ? icons.get(i) : "");
		}
		icons = List.copyOf(pictures);
	}

	/** The picture of need {@code index}, or an empty string. */
	public String icon(int index) {
		return index >= 0 && index < icons.size() ? icons.get(index) : "";
	}

	/**
	 * One line of the chronicle, as the core's tab shows it.
	 *
	 * @param day     the game day of the line, 1 on the world's first day
	 * @param worker  the worker it is about — «K-482 Alia» — or empty
	 * @param text    the sentence
	 * @param repeats how many times it happened in a row, at least 1
	 */
	public record Line(long day, String worker, ConstructionRow.Text text, int repeats) {
	}

	private static final StreamCodec<ByteBuf, Line> LINE = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Line::day,
			ByteBufCodecs.stringUtf8(TownWorksPayload.TEXT_LIMIT), Line::worker,
			TownWorksPayload.TEXT, Line::text,
			ByteBufCodecs.VAR_INT, Line::repeats,
			Line::new);

	public static final StreamCodec<RegistryFriendlyByteBuf, TownPulsePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TownPulsePayload::settlement,
			ByteBufCodecs.BOOL, TownPulsePayload::pulsed,
			TownWorksPayload.TEXT.apply(ByteBufCodecs.list(MAX_NEEDS)), TownPulsePayload::needs,
			LINE.apply(ByteBufCodecs.list(MAX_LINES)), TownPulsePayload::chronicle,
			ByteBufCodecs.stringUtf8(TownWorksPayload.TEXT_LIMIT).apply(ByteBufCodecs.list(MAX_NEEDS)),
			TownPulsePayload::icons,
			TownPulsePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
