package dev.alaindustrial.teleporter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * What the server last knew about every teleporter station in the save (MOD-628).
 *
 * <p><b>Why a registry at all.</b> The remote's screen shows, for every bound station, whether a jump there
 * would work. Reading the station itself means loading its chunk synchronously ({@link TeleportEngine#stationAt}),
 * and a screen that did that for sixteen rows on every open would stall the server for a player who only
 * wanted to rename one. So the screen reads this instead, and never loads a chunk.
 *
 * <p><b>A record of an unloaded station is not stale.</b> An unloaded station does not charge, and its privacy,
 * chip and capsule only change while its chunk is loaded — each of those changes writes here. What an entry
 * cannot know is only what it was never told: a station with no entry (a world from before this registry, a
 * station placed by a command or a structure) is unknown until its chunk next loads.
 *
 * <p><b>Who writes.</b> Only the station, about itself ({@link TeleporterBlockEntity#recordInRegistry}), plus the
 * block's removal hook. No write reads the world, so none of them can re-enter a chunk operation.
 *
 * <p><b>26.2 API</b> — the same shape as {@code WelcomeMessageState}: Codec-based {@link SavedDataType}, one
 * server-global instance from {@code server.getDataStorage().computeIfAbsent(TYPE)}. Server-global rather than
 * per-dimension, because the remote lists stations of every dimension in one screen; the key carries the
 * dimension.
 */
public class TeleporterRegistry extends SavedData {

	/** Where a station stands. */
	public record Key(ResourceKey<Level> dimension, BlockPos pos) {
	}

	/**
	 * One station as last seen.
	 *
	 * @param updatedGameTime the game time of the write, for the screen's "updated N ago"
	 */
	public record Entry(Optional<UUID> owner, boolean isPrivate, boolean hasChip, boolean formed, long energy,
			long updatedGameTime) {

		/** What {@code station} is right now, stamped with {@code gameTime}. */
		public static Entry of(TeleporterBlockEntity station, long gameTime) {
			return new Entry(Optional.ofNullable(station.getOwner()), station.isPrivate(), station.hasRtpModule(),
					TeleporterBlock.isFormed(station.getBlockState()), station.getEnergyStorage().getAmount(), gameTime);
		}

		/** The same rule as {@link TeleporterBlockEntity#allowsAccess}: its owner, or anyone while it is public. */
		public boolean allowsAccess(UUID player) {
			return !isPrivate || owner.isEmpty() || owner.get().equals(player);
		}
	}

	/** One saved row. The map key is not a string, so the save is a list of rows rather than a map. */
	private record Row(ResourceKey<Level> dimension, BlockPos pos, Entry entry) {
		static final Codec<Row> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Row::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(Row::pos),
				UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(row -> row.entry().owner()),
				Codec.BOOL.optionalFieldOf("private", true).forGetter(row -> row.entry().isPrivate()),
				Codec.BOOL.optionalFieldOf("chip", false).forGetter(row -> row.entry().hasChip()),
				Codec.BOOL.optionalFieldOf("formed", false).forGetter(row -> row.entry().formed()),
				Codec.LONG.optionalFieldOf("energy", 0L).forGetter(row -> row.entry().energy()),
				Codec.LONG.optionalFieldOf("updated", 0L).forGetter(row -> row.entry().updatedGameTime())
		).apply(instance, (dimension, pos, owner, isPrivate, chip, formed, energy, updated) ->
				new Row(dimension, pos, new Entry(owner, isPrivate, chip, formed, energy, updated))));
	}

	public static final Codec<TeleporterRegistry> CODEC = Row.CODEC.listOf().optionalFieldOf("stations", List.of())
			.xmap(TeleporterRegistry::new, TeleporterRegistry::rows).codec();

	// DataFixTypes.LEVEL for the same reason WelcomeMessageState gives: the tag has no legacy schema, and the
	// fixer never runs on our own writes.
	public static final SavedDataType<TeleporterRegistry> TYPE = new SavedDataType<>(
			Industrialization.id("teleporter_stations"), TeleporterRegistry::new, CODEC, DataFixTypes.LEVEL);

	private final Map<Key, Entry> stations = new HashMap<>();

	public TeleporterRegistry() {
	}

	private TeleporterRegistry(List<Row> rows) {
		for (Row row : rows) {
			stations.put(new Key(row.dimension(), row.pos()), row.entry());
		}
	}

	private List<Row> rows() {
		List<Row> rows = new ArrayList<>(stations.size());
		stations.forEach((key, entry) -> rows.add(new Row(key.dimension(), key.pos(), entry)));
		return rows;
	}

	public static TeleporterRegistry get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Writes what {@code station} is right now. The station calls this about itself; see the class comment. */
	public static void record(ServerLevel level, TeleporterBlockEntity station) {
		TeleporterRegistry registry = get(level.getServer());
		registry.stations.put(new Key(level.dimension(), station.getBlockPos().immutable()),
				Entry.of(station, level.getGameTime()));
		registry.setDirty();
	}

	/** Drops the station that stood at {@code pos}, if there was one. */
	public static void forget(ServerLevel level, BlockPos pos) {
		TeleporterRegistry registry = get(level.getServer());
		if (registry.stations.remove(new Key(level.dimension(), pos)) != null) {
			registry.setDirty();
		}
	}

	/** The last record of the station at {@code pos} in {@code dimension}, or empty if the server never saw it. */
	public Optional<Entry> find(ResourceKey<Level> dimension, BlockPos pos) {
		return Optional.ofNullable(stations.get(new Key(dimension, pos)));
	}
}
