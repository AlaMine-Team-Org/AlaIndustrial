package dev.alaindustrial.network;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.works.BuildingTile;
import dev.alaindustrial.settlement.works.ConstructionRow;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: the rows of the «Construction» tab of one town's core (MOD-857) — sent to ONE player, as the reply to a
 * {@link TownWorksRequestPayload}. The rows are words and controls only ({@link ConstructionRow}); the client knows no
 * building or job.
 *
 * @param core       the core the tab was asked at
 * @param settlement the town's number
 * @param manage     whether the viewer is the town's owner and may press the controls (R95)
 * @param hint       what to do next, one line at the top of the tab (owner's round 3 of 0.1.219, R3-6), or empty
 * @param tiles      what the buildings' cards draw besides their rows: section, picture, chip, resident, jobs (R3-3)
 * @param rows       the tab's rows, top to bottom
 */
public record TownWorksPayload(BlockPos core, int settlement, boolean manage, Optional<ConstructionRow.Text> hint,
		List<BuildingTile> tiles, List<ConstructionRow> rows) implements CustomPacketPayload {

	public static final Type<TownWorksPayload> TYPE = new Type<>(Industrialization.id("town_works"));

	/** Most rows one reply carries. */
	public static final int MAX_ROWS = 256;
	/** Longest translation key or argument. */
	public static final int TEXT_LIMIT = 256;
	/** Most arguments of one sentence. */
	public static final int MAX_ARGS = 16;

	/** Most jobs one card lists. */
	public static final int MAX_JOBS = 32;

	public TownWorksPayload {
		tiles = List.copyOf(tiles.subList(0, Math.min(MAX_ROWS, tiles.size())));
		rows = List.copyOf(rows.subList(0, Math.min(MAX_ROWS, rows.size())));
	}

	/** One sentence of a row, as the house laying window (MOD-877) carries its problems too. */
	public static final StreamCodec<ByteBuf, ConstructionRow.Text> TEXT = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(TEXT_LIMIT), ConstructionRow.Text::key,
			ByteBufCodecs.stringUtf8(TEXT_LIMIT).apply(ByteBufCodecs.list(MAX_ARGS)), ConstructionRow.Text::args,
			ByteBufCodecs.BOOL, ConstructionRow.Text::joined,
			ConstructionRow.Text::new);

	private static final ConstructionRow.Tone[] TONES = ConstructionRow.Tone.values();
	private static final StreamCodec<ByteBuf, ConstructionRow.Tone> TONE = ByteBufCodecs.idMapper(
			ordinal -> ordinal >= 0 && ordinal < TONES.length ? TONES[ordinal] : ConstructionRow.Tone.NORMAL,
			ConstructionRow.Tone::ordinal);

	/** One row, as the building card (round 2 of 0.1.219, Q2) carries its building's too. */
	public static final StreamCodec<ByteBuf, ConstructionRow> ROW = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(TEXT_LIMIT), ConstructionRow::section,
			TEXT, ConstructionRow::text,
			ByteBufCodecs.optional(TEXT), ConstructionRow::status,
			ByteBufCodecs.VAR_INT, ConstructionRow::percent,
			TONE, ConstructionRow::tone,
			ByteBufCodecs.optional(TEXT), ConstructionRow::detail,
			ByteBufCodecs.VAR_INT, ConstructionRow::target,
			ByteBufCodecs.VAR_INT, ConstructionRow::actions,
			ConstructionRow::new);

	private static final BuildingTile.Group[] GROUPS = BuildingTile.Group.values();
	private static final StreamCodec<ByteBuf, BuildingTile.Group> GROUP = ByteBufCodecs.idMapper(
			ordinal -> ordinal >= 0 && ordinal < GROUPS.length ? GROUPS[ordinal] : BuildingTile.Group.OTHER,
			BuildingTile.Group::ordinal);
	private static final StreamCodec<ByteBuf, BuildingTile.Chip> CHIP = ByteBufCodecs.idMapper(
			BuildingTile.Chip::byOrdinal, BuildingTile.Chip::ordinal);

	/** One building's card data (round 3 of 0.1.219, R3-3). */
	public static final StreamCodec<ByteBuf, BuildingTile> TILE = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BuildingTile::index,
			ByteBufCodecs.stringUtf8(TEXT_LIMIT), BuildingTile::icon,
			GROUP, BuildingTile::group,
			CHIP, BuildingTile::chip,
			ByteBufCodecs.optional(TEXT), BuildingTile::resident,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_JOBS)), BuildingTile::jobs,
			BuildingTile::new);

	public static final StreamCodec<RegistryFriendlyByteBuf, TownWorksPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, TownWorksPayload::core,
			ByteBufCodecs.VAR_INT, TownWorksPayload::settlement,
			ByteBufCodecs.BOOL, TownWorksPayload::manage,
			ByteBufCodecs.optional(TEXT), TownWorksPayload::hint,
			TILE.apply(ByteBufCodecs.list(MAX_ROWS)), TownWorksPayload::tiles,
			ROW.apply(ByteBufCodecs.list(MAX_ROWS)), TownWorksPayload::rows,
			TownWorksPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
