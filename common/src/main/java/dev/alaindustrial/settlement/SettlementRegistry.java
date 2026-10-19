package dev.alaindustrial.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.shift.ShiftWorkerBook;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Every ALA town of the save (MOD-831): the source of truth for a town — its number, where its core stands, who
 * owns it, its tier and style, and whether its core is lit. The core in the world stores only the town's number
 * ({@code SettlementCoreBlockEntity}); everything else lives here, so a screen, a shift worker or a command reads a
 * town without loading the chunk its core stands in. The shift workers' records and the town halls' log are its
 * {@link ShiftWorkerBook} (MOD-832), saved in the optional field {@code shift_workers}: a save written before the
 * workers reads as one without any.
 *
 * <p><b>Who writes.</b> The core, about its own town: it takes its place when a player places it ({@link #adopt}) and
 * on every tick ({@link #place}), reports when it lights or goes dark ({@link #setActive}) and passes a claim on
 * ({@link #claim}); the core block's removal hook reports the core taken away ({@link #coreRemoved}). The town
 * structure (MOD-830) sets the style ({@link #setStyle}) and the footprint of its pieces ({@link #setFootprint},
 * MOD-848), which the town's territory covers. No write reads the world, so none of them can re-enter a chunk
 * operation.
 *
 * <p><b>A core does not remember its town</b> (epic decisions R36, R43). Only a town's owner breaks its core; it drops
 * as a plain core, and the town hibernates, data, workers and owner intact. Any core a player places inside the
 * territory of a town without its core continues that town from its new spot; a second core may not be placed in a
 * town that has one ({@link #coreAlreadyStands}); outside every territory it founds no town (R53).
 *
 * <p><b>A copy is not the town's core.</b> A core copied with its data while the original still stands — a
 * structure template saved from a placed core, a {@code /clone} — is a dark second core inside that town's territory
 * and a town of its own outside it, never a share of one.
 *
 * <p><b>26.3 API</b> — the shape of {@code TeleporterRegistry}: a Codec-based {@link SavedDataType}, one
 * server-global instance from {@code server.getDataStorage().computeIfAbsent(TYPE)}, the dimension carried in each
 * record. Saved in id order, so the same towns always write the same file.
 */
public final class SettlementRegistry extends SavedData {

	/** What a claim came to. */
	public enum ClaimResult {
		/** The player now owns the town. */
		CLAIMED,
		/** The player already owned it; nothing changed. */
		ALREADY_YOURS,
		/** Somebody else owns it; nothing changed (epic decision R16: hand-over comes with the clan roles). */
		OWNED_BY_ANOTHER,
		/** The core is dark: a town goes to whoever brings it power (R37), so a dead one cannot be claimed. */
		NOT_POWERED,
		/** No town under that number. */
		UNKNOWN
	}

	/**
	 * Where a core stands as its town sees it.
	 *
	 * @param settlementId the core's town
	 * @param home         true when the core is its town's core: it may light. False for a second core of a town whose
	 *                     core stands elsewhere (R43) — dark, founding no town, and its town's again the moment that
	 *                     town is without its core
	 */
	public record Placement(int settlementId, boolean home) {
		/** No town: a player's core placed outside every territory (epic decision R53) — it founds none. */
		public static final Placement NONE = new Placement(0, false);
	}

	private static final Codec<SettlementTier> TIER_CODEC =
			Codec.STRING.xmap(SettlementTier::byName, SettlementTier::serializedName);

	/**
	 * A town's footprint (MOD-848) by its four named bounds. A footprint whose bounds are crossed is an error the
	 * town's field reads leniently ({@link #SETTLEMENT_CODEC}): such a town keeps its tier's square instead of failing
	 * the file.
	 */
	private static final Codec<SettlementTerritory.Area> AREA_CODEC = RecordCodecBuilder.<List<Integer>>create(
			instance -> instance.group(
					Codec.INT.fieldOf("min_x").forGetter(bounds -> bounds.get(0)),
					Codec.INT.fieldOf("min_z").forGetter(bounds -> bounds.get(1)),
					Codec.INT.fieldOf("max_x").forGetter(bounds -> bounds.get(2)),
					Codec.INT.fieldOf("max_z").forGetter(bounds -> bounds.get(3))
			).apply(instance, List::of))
			.comapFlatMap(SettlementRegistry::area,
					area -> List.of(area.minX(), area.minZ(), area.maxX(), area.maxZ()));

	private static final Codec<Settlement> SETTLEMENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("id").forGetter(Settlement::id),
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Settlement::dimension),
			BlockPos.CODEC.fieldOf("core").forGetter(Settlement::corePos),
			Codec.BOOL.optionalFieldOf("core_placed", true).forGetter(Settlement::corePlaced),
			UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Settlement::owner),
			Codec.STRING.optionalFieldOf("owner_name", "").forGetter(Settlement::ownerName),
			TIER_CODEC.optionalFieldOf("tier", SettlementTier.FIRST).forGetter(Settlement::tier),
			Codec.STRING.optionalFieldOf("style", "").forGetter(Settlement::style),
			Codec.BOOL.optionalFieldOf("active", false).forGetter(Settlement::active),
			// MOD-848: absent from a save written before towns knew their footprint, which reads as a square town.
			AREA_CODEC.lenientOptionalFieldOf("footprint").forGetter(Settlement::footprint)
	).apply(instance, Settlement::new));

	public static final Codec<SettlementRegistry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			SETTLEMENT_CODEC.listOf().optionalFieldOf("settlements", List.of()).forGetter(SettlementRegistry::rows),
			Codec.INT.optionalFieldOf("next_id", 1).forGetter(registry -> registry.nextId),
			ShiftWorkerBook.CODEC.optionalFieldOf("shift_workers").forGetter(registry -> registry.workers.isEmpty()
					? Optional.empty() : Optional.of(registry.workers))
	).apply(instance, SettlementRegistry::new));

	// DataFixTypes.LEVEL for the reason TeleporterRegistry gives: the tag has no legacy schema, and the fixer never
	// runs on our own writes.
	public static final SavedDataType<SettlementRegistry> TYPE = new SavedDataType<>(
			Industrialization.id("settlements"), SettlementRegistry::new, CODEC, DataFixTypes.LEVEL);

	/** Towns by number; a tree so the save and every listing come out in the same order. */
	private final Map<Integer, Settlement> byId = new TreeMap<>();
	/** The number the next new town gets; numbers are never reused while the save lives. */
	private int nextId = 1;
	/** The shift workers of every town and the halls' log (MOD-832). */
	private final ShiftWorkerBook workers;

	public SettlementRegistry() {
		this(List.of(), 1, Optional.empty());
	}

	private SettlementRegistry(List<Settlement> rows, int nextId, Optional<ShiftWorkerBook> workers) {
		this.workers = workers.orElseGet(ShiftWorkerBook::new);
		this.workers.onChange(this::setDirty);
		for (Settlement row : rows) {
			if (row.id() > 0) {
				byId.putIfAbsent(row.id(), row);
			}
		}
		int highest = byId.isEmpty() ? 0 : Collections.max(byId.keySet());
		this.nextId = Math.max(Math.max(1, nextId), highest + 1);
	}

	private List<Settlement> rows() {
		return new ArrayList<>(byId.values());
	}

	/** The registry of {@code server}'s save. */
	public static SettlementRegistry get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/**
	 * The town of a core standing at {@code corePos}, registering one if needed — called by the core on every tick
	 * with the town number its own data carries ({@code knownId}, 0 when it has none). A core a player has just placed
	 * goes through {@link #adopt} first, so by its first tick it already carries its town.
	 *
	 * <ul>
	 *   <li>Town {@code knownId}'s core is recorded right here: its home.</li>
	 *   <li>The core is a copy of town {@code knownId}'s core (a {@code /clone}, a structure template saved from a
	 *       placed core) standing inside that town's territory: while the town has its core elsewhere this one is a
	 *       second core — not home, dark, and it founds no town (R43); once the town is without its core, this one
	 *       continues it from here.</li>
	 *   <li>A copy outside that territory gets the town recorded here or a new one.</li>
	 *   <li>The registry has no town {@code knownId} (a save whose registry lost the record): the town comes back
	 *       under the same number, unless another town's core is already recorded here, which then wins.</li>
	 * </ul>
	 *
	 * <p>Territory alone never binds a core on this path: a core that appears without a player — a structure, a
	 * command, the demo stand — founds its own town wherever it stands. Only a player's core reads the territory
	 * it is placed in ({@link #adopt}); so a town is born with its town hall or by an operator, and a player's core
	 * revives a sleeping one.
	 */
	public Placement place(ResourceKey<Level> dimension, BlockPos corePos, int knownId) {
		Settlement known = knownId > 0 ? byId.get(knownId) : null;
		if (known != null && known.dimension().equals(dimension) && known.corePos().equals(corePos)) {
			// The answer on every tick but the first: one map lookup, a write only when the core comes back.
			if (!known.corePlaced()) {
				put(known.withCore(corePos, true));
			}
			return new Placement(knownId, true);
		}
		if (known != null && known.territoryContains(dimension, corePos)) {
			if (known.corePlaced()) {
				return new Placement(knownId, false);
			}
			put(known.withCore(corePos, true));
			return new Placement(knownId, true);
		}
		Optional<Settlement> here = findAt(dimension, corePos);
		if (knownId > 0 && known == null && here.isEmpty()) {
			put(Settlement.unclaimed(knownId, dimension, corePos));
			nextId = Math.max(nextId, knownId + 1);
			return new Placement(knownId, true);
		}
		if (here.isPresent()) {
			return new Placement(here.get().id(), true);
		}
		int id = nextId++;
		put(Settlement.unclaimed(id, dimension, corePos));
		return new Placement(id, true);
	}

	/**
	 * The town of a core a player has just placed at {@code corePos} (epic decision R43: a core does not remember its
	 * town). The town whose territory holds the spot decides — the one whose core stands, or last stood, nearest:
	 *
	 * <ul>
	 *   <li>a town without its core: this core continues it from here — its number, owner, workers and data;</li>
	 *   <li>a town whose core is recorded on this very spot: this core is it;</li>
	 *   <li>a town whose core stands elsewhere: this would be a second core — {@link #coreAlreadyStands} lets the
	 *       block refuse the placement, so this branch answers only a race: not home, dark;</li>
	 *   <li>no territory here: {@link Placement#NONE} — a player's core founds no town (epic decision R53: a town of
	 *       one's own from scratch is a later stage of the epic, R18); nothing is written.</li>
	 * </ul>
	 */
	public Placement adopt(ResourceKey<Level> dimension, BlockPos corePos) {
		Optional<Settlement> nearest = nearestTerritoryAt(dimension, corePos);
		if (nearest.isEmpty()) {
			return Placement.NONE;
		}
		Settlement town = nearest.get();
		if (!town.corePlaced()) {
			put(town.withCore(corePos, true));
			return new Placement(town.id(), true);
		}
		return new Placement(town.id(), town.corePos().equals(corePos));
	}

	/**
	 * The town that already has its core elsewhere and whose territory, nearest first, holds {@code pos} — the place
	 * a player may not put a second core (R43). Empty when a core placed there would continue a town without its core,
	 * stand on its own town's recorded spot, or found a town.
	 */
	public Optional<Settlement> coreAlreadyStands(ResourceKey<Level> dimension, BlockPos pos) {
		return nearestTerritoryAt(dimension, pos).filter(town -> town.corePlaced() && !town.corePos().equals(pos));
	}

	/**
	 * Of the towns whose territory holds {@code pos} — lit or not, with or without their core — the one whose core
	 * stands, or last stood, nearest on the ground; the newest of two at the same distance. Territories of real towns
	 * rarely overlap (their structures stand apart); where they do, a player standing in a town hall means that hall.
	 */
	private Optional<Settlement> nearestTerritoryAt(ResourceKey<Level> dimension, BlockPos pos) {
		Settlement nearest = null;
		long nearestDistance = Long.MAX_VALUE;
		for (Settlement town : byId.values()) {
			if (!town.territoryContains(dimension, pos)) {
				continue;
			}
			long dx = (long) town.corePos().getX() - pos.getX();
			long dz = (long) town.corePos().getZ() - pos.getZ();
			long distance = dx * dx + dz * dz;
			// Towns come in number order, so "not farther" lets the newer of two equals win.
			if (distance <= nearestDistance) {
				nearest = town;
				nearestDistance = distance;
			}
		}
		return Optional.ofNullable(nearest);
	}

	/**
	 * The core standing at {@code corePos} of {@code dimension} has left the world — its owner broke it, or it was
	 * removed some other way. Its town stays, dark, until the core is placed back ({@link #place}).
	 */
	public void coreRemoved(ResourceKey<Level> dimension, BlockPos corePos) {
		findAt(dimension, corePos).ifPresent(settlement -> {
			put(settlement.withCore(corePos, false));
			// INFO on purpose (MOD-833): the playtest protocol of the first slice reads these lines from the log.
			Industrialization.LOGGER.info("[ala-towns] settlement {} core taken away from {}: hibernation on",
					settlement.id(), corePos);
		});
	}

	/** The shift workers of every town and the town halls' log (MOD-832). */
	public ShiftWorkerBook workers() {
		return workers;
	}

	/** The town numbered {@code id}. */
	public Optional<Settlement> find(int id) {
		return Optional.ofNullable(byId.get(id));
	}

	/** The town whose core stands at {@code corePos} of {@code dimension} right now. */
	public Optional<Settlement> findAt(ResourceKey<Level> dimension, BlockPos corePos) {
		for (Settlement settlement : byId.values()) {
			if (settlement.corePlaced() && settlement.dimension().equals(dimension)
					&& settlement.corePos().equals(corePos)) {
				return Optional.of(settlement);
			}
		}
		return Optional.empty();
	}

	/**
	 * The town whose territory holds {@code pos} right now ({@link Settlement#holds}), the lowest number first where
	 * two territories overlap. Reads no chunk.
	 */
	public Optional<Settlement> territoryAt(ResourceKey<Level> dimension, BlockPos pos) {
		for (Settlement settlement : byId.values()) {
			if (settlement.holds(dimension, pos)) {
				return Optional.of(settlement);
			}
		}
		return Optional.empty();
	}

	/** Every town, in number order. */
	public Collection<Settlement> all() {
		return Collections.unmodifiableCollection(byId.values());
	}

	/**
	 * {@code player} claims town {@code id}: they become its owner if it has none and its core is lit (R37). A
	 * claimed town stays its owner's — a second player cannot take it over (R16; roles and hand-over come later).
	 */
	public ClaimResult claim(int id, UUID player, String playerName) {
		Settlement settlement = byId.get(id);
		if (settlement == null) {
			return ClaimResult.UNKNOWN;
		}
		if (settlement.owner().isPresent()) {
			return settlement.isOwnedBy(player) ? ClaimResult.ALREADY_YOURS : ClaimResult.OWNED_BY_ANOTHER;
		}
		if (!settlement.active()) {
			return ClaimResult.NOT_POWERED;
		}
		put(settlement.withOwner(player, playerName));
		return ClaimResult.CLAIMED;
	}

	/** The core of town {@code id} lit or went dark. Nothing happens for a number the registry does not know. */
	public void setActive(int id, boolean active) {
		Settlement settlement = byId.get(id);
		if (settlement != null && settlement.active() != active) {
			put(settlement.withActive(active));
		}
	}

	/** Town {@code id} is of style {@code style} (a {@code settlement_style} id), as its town structure says. */
	public void setStyle(int id, String style) {
		Settlement settlement = byId.get(id);
		if (settlement != null && !settlement.style().equals(style)) {
			put(settlement.withStyle(style));
		}
	}

	/**
	 * Town {@code id} was built by a structure whose pieces cover {@code footprint} (MOD-848, epic decision R59): its
	 * territory holds them all from now on ({@link Settlement#territory()}). Written by the town structure next to the
	 * style, and by the core of a town from an older save once it finds its structure again.
	 */
	public void setFootprint(int id, SettlementTerritory.Area footprint) {
		Settlement settlement = byId.get(id);
		if (settlement != null && !settlement.footprint().equals(Optional.of(footprint))) {
			put(settlement.withFootprint(footprint));
		}
	}

	private static DataResult<SettlementTerritory.Area> area(List<Integer> bounds) {
		if (bounds.get(0) > bounds.get(2) || bounds.get(1) > bounds.get(3)) {
			return DataResult.error(() -> "a footprint with crossed bounds: " + bounds);
		}
		return DataResult.success(new SettlementTerritory.Area(bounds.get(0), bounds.get(1), bounds.get(2),
				bounds.get(3)));
	}

	private void put(Settlement settlement) {
		byId.put(settlement.id(), settlement);
		setDirty();
	}
}
