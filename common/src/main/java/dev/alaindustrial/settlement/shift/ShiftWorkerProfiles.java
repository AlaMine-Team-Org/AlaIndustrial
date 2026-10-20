package dev.alaindustrial.settlement.shift;

import dev.alaindustrial.block.entity.ReceptionCapsuleBlockEntity;
import dev.alaindustrial.block.entity.SettlementCoreBlockEntity;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.menu.SettlementCoreMenu;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.ShiftWorkerActionPayload;
import dev.alaindustrial.network.ShiftWorkerCardPayload;
import dev.alaindustrial.network.ShiftWorkerRosterPayload;
import dev.alaindustrial.registry.ModDataRegistries;
import dev.alaindustrial.settlement.ReceptionCapsuleRules;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.stats.LevelMath;
import dev.alaindustrial.worldgen.town.TownBiographyFacts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The server side of a shift worker's card (MOD-854): builds the card and the roster for a viewer, and applies the
 * owner's changes from the card — one point into a stat, all points back at a capsule, a name. Server thread only;
 * the payload handlers ({@code ShiftWorkerRequestPayload}, {@code ShiftWorkerActionPayload}) and the worker's right
 * click ({@link ShiftWorker}) are its callers.
 *
 * <p><b>Who may do what (R74).</b> Anyone standing close enough — to the loaded worker, or to its town's core — may
 * VIEW any card, read-only ({@link #mayView}). Only the town's owner may CHANGE one, and only a living record
 * ({@link #mayChange}); a worker of no town, or of a town nobody has claimed, cannot be changed from its card. A
 * request that fails a check gets no reply — the client asked for something it has no screen for.
 *
 * <p><b>The biography</b> ({@link #biography}) is four fragment ids kept in the profile, chosen at issue and rendered
 * with the town's facts, which are computed once per town ({@link #townFacts}).
 */
public final class ShiftWorkerProfiles {

	/** How far from the loaded worker, or from its town's core, a player may stand to view or change its card. */
	public static final double VIEW_DISTANCE = 10.0;
	/** How far from the worker a reception capsule of its town may stand for the worker to count as "at" it. */
	public static final int CAPSULE_REACH = 3;
	/** The biography facts of each town by number; runtime only, cleared when the server stops. */
	private static final Map<Integer, TownFacts> TOWN_FACTS = new HashMap<>();

	private ShiftWorkerProfiles() {
	}

	// ── requests ────────────────────────────────────────────────────────────────────────────────

	/** {@code viewer} right-clicked a loaded {@code worker}: open its card, if it has a record. */
	public static void openFromEntity(ServerPlayer viewer, ShiftWorker worker) {
		ShiftWorkers.book(viewer.level().getServer()).byEntity(worker.getUUID())
				.ifPresent(record -> sendCard(viewer, record, true));
	}

	/** A roster row asked for the card of {@code number}: sent opening, if {@code viewer} may see it. */
	public static void requestCard(ServerPlayer viewer, RecordNumber number) {
		ShiftWorkers.book(viewer.level().getServer()).byNumber(number)
				.filter(record -> mayView(viewer, record))
				.ifPresent(record -> sendCard(viewer, record, true));
	}

	/**
	 * The roster of the town whose core {@code viewer} has open: sent if the open menu is a settlement core's, that
	 * core still stands in the viewer's level and belongs to a town, and the viewer is within {@link #VIEW_DISTANCE}
	 * of it. The client names no core (MOD-854): the open menu is the only core it can be asking about.
	 */
	public static void requestRoster(ServerPlayer viewer) {
		if (!(viewer.containerMenu instanceof SettlementCoreMenu menu) || menu.core() == null) {
			return;
		}
		SettlementCoreBlockEntity coreBe = menu.core();
		BlockPos core = coreBe.getBlockPos();
		ServerLevel level = viewer.level();
		if (coreBe.isRemoved() || coreBe.getLevel() != level || !near(viewer, Vec3.atCenterOf(core))
				|| !level.isLoaded(core) || level.getBlockEntity(core) != coreBe) {
			return;
		}
		coreBe.settlement().ifPresent(town -> send(viewer, roster(level.getServer(), town, core)));
	}

	/** Sends {@code viewer} the card of {@code record}; {@code open} opens the screen, false only refreshes it. */
	public static void sendCard(ServerPlayer viewer, ShiftWorkerRecord record, boolean open) {
		send(viewer, card(viewer, record, open));
	}

	// ── actions ─────────────────────────────────────────────────────────────────────────────────

	/**
	 * One free point into {@code stat}: owner only, a living record, a free point, the stat under
	 * {@link SettlementConfig#shiftWorkerStatCap} and a stat that works in this build ({@link
	 * ShiftWorkerProfile.Stat#active()}). The new maximum health is applied to the loaded entity at once
	 * ({@link #applyStats}); the battery's capacity follows from the profile. Replies with a refreshed card.
	 */
	public static void invest(ServerPlayer player, RecordNumber number, ShiftWorkerProfile.Stat stat) {
		Optional<ShiftWorkerRecord> found = changeable(player, number);
		if (found.isEmpty()) {
			return;
		}
		ShiftWorkerRecord record = found.get();
		ShiftWorkerProfile profile = record.profile();
		if (stat.active() && profile.freePoints() > 0 && profile.count(stat) < SettlementConfig.shiftWorkerStatCap) {
			record = store(player.level(), record.withProfile(profile.invested(stat)));
			applyStats(player.level(), record);
		}
		sendCard(player, record, false);
	}

	/**
	 * Every invested point back (R69): owner only, a living record with at least one point, standing at a reception
	 * capsule of its town ({@link #findCapsule}) whose buffer holds {@link SettlementConfig#shiftWorkerRespecCost} —
	 * the price is drained from it. Replies with a refreshed card.
	 */
	public static void respec(ServerPlayer player, RecordNumber number) {
		Optional<ShiftWorkerRecord> found = changeable(player, number);
		if (found.isEmpty()) {
			return;
		}
		ShiftWorkerRecord record = found.get();
		if (!record.profile().investments().isEmpty()) {
			Optional<ReceptionCapsuleBlockEntity> capsule = findCapsule(player.level(), record)
					.filter(ShiftWorkerProfiles::holdsRespecPrice);
			if (capsule.isPresent()) {
				capsule.get().getEnergyStorage().drainInternal(SettlementConfig.shiftWorkerRespecCost);
				capsule.get().setChanged();
				record = store(player.level(), record.withProfile(record.profile().respecced()));
				applyStats(player.level(), record);
			}
		}
		sendCard(player, record, false);
	}

	/**
	 * A new name (R3v): owner only, a living record; the text loses formatting codes and control characters, is
	 * trimmed and cut to {@link ShiftWorkerActionPayload#NAME_LIMIT}; an empty result clears the name. A changed
	 * non-empty name writes a {@code NAMED} service mark, and the loaded entity shows it ({@link
	 * ShiftWorker#showRecord}). Replies with a refreshed card.
	 */
	public static void rename(ServerPlayer player, RecordNumber number, String requested) {
		Optional<ShiftWorkerRecord> found = changeable(player, number);
		if (found.isEmpty()) {
			return;
		}
		ShiftWorkerRecord record = found.get();
		String name = sanitizeName(requested);
		if (!name.equals(record.name())) {
			ShiftWorkerRecord renamed = record.withName(name);
			if (!name.isEmpty()) {
				renamed = renamed.withProfile(renamed.profile().marked(new ShiftWorkerProfile.ServiceMark(
						ShiftWorkerProfile.ServiceMark.Kind.NAMED, player.level().getGameTime(), List.of(name))));
			}
			record = store(player.level(), renamed);
			if (player.level().getEntity(record.entity()) instanceof ShiftWorker worker) {
				worker.showRecord(record);
			}
		}
		sendCard(player, record, false);
	}

	/** A requested name as it is stored: no formatting codes or control characters, trimmed, at most 16 chars. */
	public static String sanitizeName(String requested) {
		String clean = StringUtil.filterText(requested == null ? "" : requested).strip();
		if (clean.length() > ShiftWorkerActionPayload.NAME_LIMIT) {
			clean = clean.substring(0, ShiftWorkerActionPayload.NAME_LIMIT).strip();
		}
		return clean;
	}

	// ── checks ──────────────────────────────────────────────────────────────────────────────────

	/**
	 * Whether {@code viewer} may see the card of {@code record} (R74 — everyone, read-only): within
	 * {@link #VIEW_DISTANCE} of the loaded worker in the viewer's level, or of its town's core in the viewer's
	 * dimension (the roster at the core shows the dead too).
	 */
	public static boolean mayView(ServerPlayer viewer, ShiftWorkerRecord record) {
		ServerLevel level = viewer.level();
		Entity entity = level.getEntity(record.entity());
		if (entity != null && !entity.isRemoved() && near(viewer, entity.position())) {
			return true;
		}
		return town(level.getServer(), record).filter(town -> town.dimension().equals(level.dimension()))
				.map(town -> near(viewer, Vec3.atCenterOf(town.corePos()))).orElse(false);
	}

	/** Whether {@code viewer} may change {@code record}: may view it, owns its town, and the record is alive. */
	public static boolean mayChange(ServerPlayer viewer, ShiftWorkerRecord record) {
		return record.status() == ShiftWorkerRecord.Status.ALIVE && isOwner(viewer, record) && mayView(viewer, record);
	}

	private static boolean isOwner(ServerPlayer viewer, ShiftWorkerRecord record) {
		return town(viewer.level().getServer(), record).map(town -> town.isOwnedBy(viewer.getUUID())).orElse(false);
	}

	private static Optional<ShiftWorkerRecord> changeable(ServerPlayer player, RecordNumber number) {
		return ShiftWorkers.book(player.level().getServer()).byNumber(number)
				.filter(record -> mayChange(player, record));
	}

	private static boolean near(ServerPlayer viewer, Vec3 point) {
		return viewer.position().distanceToSqr(point) <= VIEW_DISTANCE * VIEW_DISTANCE;
	}

	private static boolean holdsRespecPrice(ReceptionCapsuleBlockEntity capsule) {
		return capsule.getEnergyStorage().getAmount() >= SettlementConfig.shiftWorkerRespecCost;
	}

	/**
	 * A reception capsule of the worker's town within {@link #CAPSULE_REACH} blocks of its loaded entity — "the worker
	 * stands at the capsule" for the respec (R69). Empty for a worker that is not loaded or of no town. A small box
	 * scan, run only on a request, never per tick.
	 */
	public static Optional<ReceptionCapsuleBlockEntity> findCapsule(ServerLevel level, ShiftWorkerRecord record) {
		Entity entity = level.getEntity(record.entity());
		if (entity == null || entity.isRemoved() || record.settlement() <= 0) {
			return Optional.empty();
		}
		BlockPos at = entity.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-CAPSULE_REACH, -CAPSULE_REACH, -CAPSULE_REACH),
				at.offset(CAPSULE_REACH, CAPSULE_REACH, CAPSULE_REACH))) {
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ReceptionCapsuleBlockEntity capsule
					&& capsule.settlementId() == record.settlement()) {
				return Optional.of(capsule);
			}
		}
		return Optional.empty();
	}

	// ── building the payloads ───────────────────────────────────────────────────────────────────

	/** The card of {@code record} as {@code viewer} sees it. */
	public static ShiftWorkerCardPayload card(ServerPlayer viewer, ShiftWorkerRecord record, boolean open) {
		ServerLevel level = viewer.level();
		MinecraftServer server = level.getServer();
		ShiftWorkerProfile profile = record.profile();
		Optional<Settlement> town = town(server, record);
		boolean alive = record.status() == ShiftWorkerRecord.Status.ALIVE;
		Entity entity = alive ? level.getEntity(record.entity()) : null;
		ShiftWorker worker = entity instanceof ShiftWorker loaded && !loaded.isRemoved() ? loaded : null;
		int workerLevel = profile.level();
		long xp = ShiftWorkerLevels.xp(profile.euDrained());
		long base = ShiftWorkerLevels.xpForLevel(workerLevel);
		long next = workerLevel >= LevelMath.MAX_LEVEL ? base : ShiftWorkerLevels.xpForLevel(workerLevel + 1);
		ShiftWorkerProfile.Stat[] stats = ShiftWorkerProfile.Stat.values();
		boolean[] active = new boolean[stats.length];
		for (ShiftWorkerProfile.Stat stat : stats) {
			active[stat.ordinal()] = stat.active();
		}
		float maxHealth = worker != null ? worker.getMaxHealth() : profile.maxHealth();
		float health = worker != null ? worker.getHealth() : alive ? maxHealth : 0.0F;
		boolean owner = isOwner(viewer, record);
		Optional<ReceptionCapsuleBlockEntity> capsule = alive ? findCapsule(level, record) : Optional.empty();
		boolean canRespec = owner && alive && !profile.investments().isEmpty()
				&& capsule.filter(ShiftWorkerProfiles::holdsRespecPrice).isPresent();
		long returnPrice = record.status() == ShiftWorkerRecord.Status.AWAITING_RETURN
				? ReceptionCapsuleRules.euPrice(true) : 0L;
		return new ShiftWorkerCardPayload(open, worker != null ? worker.getId() : -1, record.number().toString(),
				record.name(), record.slim(), record.look(), record.status(), record.settlement(),
				townName(server, town), owner,
				profile.chargeNow(), profile.capacity(), health, maxHealth, profile.euDrained(),
				workerLevel, Math.max(0L, xp - base), Math.max(0L, next - base),
				ShiftWorkerLevels.rankIndex(workerLevel), profile.freePoints(), SettlementConfig.shiftWorkerStatCap,
				profile.counts(), active, biography(level, record), profile.marks(), profile.issuedGameTime(),
				formerNumbers(server, record), record.death(), returnPrice,
				capsule.isPresent(), canRespec, SettlementConfig.shiftWorkerRespecCost);
	}

	/** The roster of {@code town}: living and waiting workers, then the closed records, each in number order. */
	public static ShiftWorkerRosterPayload roster(MinecraftServer server, Settlement town, BlockPos core) {
		List<ShiftWorkerRecord> records = new ArrayList<>();
		for (ShiftWorkerRecord record : ShiftWorkers.book(server).all()) {
			if (record.settlement() == town.id()) {
				records.add(record);
			}
		}
		records.sort((a, b) -> a.number().compareTo(b.number()));
		List<ShiftWorkerRosterPayload.Row> active = new ArrayList<>();
		List<ShiftWorkerRosterPayload.Row> closed = new ArrayList<>();
		for (ShiftWorkerRecord record : records) {
			ShiftWorkerRosterPayload.Row row = new ShiftWorkerRosterPayload.Row(record.number().toString(),
					record.name(), record.profile().level(), record.status(),
					record.status() == ShiftWorkerRecord.Status.ALIVE);
			boolean dead = record.status() == ShiftWorkerRecord.Status.DEAD;
			List<ShiftWorkerRosterPayload.Row> into = dead ? closed : active;
			if (into.size() < ShiftWorkerRosterPayload.MAX_ROWS) {
				into.add(row);
			}
		}
		return new ShiftWorkerRosterPayload(core, town.id(), active, closed);
	}

	/**
	 * The four biography lines of {@code record}, resolved on the server so they read the same for every player
	 * (R26a, R72): the fragment ids kept in its profile, rendered with its town's facts. A record with no ids yet — an
	 * older save, or one issued before the registry existed — gets them chosen and stored now; a kept id that left the
	 * datapack is chosen again for its slot alone ({@link ShiftWorkerBiography#render}). No registry — no lines.
	 */
	public static List<Component> biography(ServerLevel level, ShiftWorkerRecord record) {
		MinecraftServer server = level.getServer();
		Optional<Registry<ShiftWorkerBiographyFragment>> fragments = fragments(server);
		if (fragments.isEmpty()) {
			return List.of();
		}
		List<Identifier> ids = record.profile().biography();
		if (ids.isEmpty()) {
			ids = chooseBiography(server, record.number(), record.settlement(), record.entity());
			if (!ids.isEmpty()) {
				ShiftWorkers.book(server).put(record.withProfile(record.profile().withBiography(ids)));
			}
		}
		return ShiftWorkerBiography.render(fragments.get(), ids, server.overworld().getSeed(),
				record.number(), record.slim(), townFacts(server, record.settlement()));
	}

	/**
	 * The biography fragment ids for the worker with {@code number} in town {@code settlement}: a memory another worker
	 * of the same town already carries is avoided while another fits (ADR-050 point 7). {@code self} — the worker's own
	 * entity, left out of the town's memories. Empty without the registry.
	 */
	public static List<Identifier> chooseBiography(MinecraftServer server, RecordNumber number, int settlement,
			UUID self) {
		Optional<Registry<ShiftWorkerBiographyFragment>> fragments = fragments(server);
		if (fragments.isEmpty()) {
			return List.of();
		}
		Set<Identifier> memories = new HashSet<>();
		if (settlement > 0) {
			for (ShiftWorkerRecord other : ShiftWorkers.book(server).all()) {
				if (other.settlement() != settlement || other.entity().equals(self)) {
					continue;
				}
				for (Identifier id : other.profile().biography()) {
					ShiftWorkerBiographyFragment fragment = fragments.get().getValue(id);
					if (fragment != null && fragment.slot() == ShiftWorkerBiographyFragment.Slot.MEMORY) {
						memories.add(id);
					}
				}
			}
		}
		return ShiftWorkerBiography.choose(fragments.get(), server.overworld().getSeed(), number,
				townFacts(server, settlement), memories);
	}

	/**
	 * What town {@code settlement} lets a biography quote, computed once per town and kept until the server stops
	 * ({@link #clearTownFacts}): reading it walks every building template of the town. No town — nothing.
	 */
	public static TownFacts townFacts(MinecraftServer server, int settlement) {
		if (settlement <= 0) {
			return TownFacts.UNKNOWN;
		}
		TownFacts cached = TOWN_FACTS.get(settlement);
		if (cached != null) {
			return cached;
		}
		Optional<Settlement> town = SettlementRegistry.get(server).find(settlement);
		ServerLevel level = town.map(found -> server.getLevel(found.dimension())).orElse(null);
		if (level == null) {
			return TownFacts.UNKNOWN;
		}
		TownFacts facts = TownBiographyFacts.at(level, town.get().corePos());
		TOWN_FACTS.put(settlement, facts);
		return facts;
	}

	/** Forgets every town's facts — the server stopped, the next world has its own towns. */
	public static void clearTownFacts() {
		TOWN_FACTS.clear();
	}

	private static Optional<Registry<ShiftWorkerBiographyFragment>> fragments(MinecraftServer server) {
		return server.registryAccess().lookup(ModDataRegistries.SHIFT_WORKER_BIOGRAPHY);
	}

	/**
	 * Puts the profile's maximum health on the loaded entity of {@code record}; current health is capped at it.
	 * Nothing for a worker that is not loaded — {@link ShiftWorkers#bind} applies it when it joins.
	 */
	public static void applyStats(ServerLevel level, ShiftWorkerRecord record) {
		if (level.getEntity(record.entity()) instanceof ShiftWorker worker && !worker.isRemoved()) {
			applyStats(worker, record.profile(), false);
		}
	}

	/**
	 * Puts {@code profile}'s maximum health on {@code worker}: healed to it ({@code heal}, a new or returned body) or
	 * with its current health capped at it.
	 */
	public static void applyStats(ShiftWorker worker, ShiftWorkerProfile profile, boolean heal) {
		AttributeInstance maxHealth = worker.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null) {
			return;
		}
		maxHealth.setBaseValue(profile.maxHealth());
		worker.setHealth(heal ? worker.getMaxHealth() : Math.min(worker.getHealth(), worker.getMaxHealth()));
	}

	private static ShiftWorkerRecord store(ServerLevel level, ShiftWorkerRecord record) {
		ShiftWorkers.book(level.getServer()).put(record);
		return record;
	}

	private static Component townName(MinecraftServer server, Optional<Settlement> town) {
		return town.filter(settlement -> !settlement.style().isEmpty())
				.flatMap(settlement -> Optional.ofNullable(Identifier.tryParse(settlement.style())))
				.flatMap(id -> server.registryAccess().lookup(ModDataRegistries.SETTLEMENT_STYLE)
						.flatMap(registry -> registry.getOptional(id)))
				.map(style -> (Component) Component.translatable(style.name()))
				.orElse(Component.empty());
	}

	private static List<ShiftWorkerCardPayload.FormerNumber> formerNumbers(MinecraftServer server,
			ShiftWorkerRecord record) {
		List<ShiftWorkerCardPayload.FormerNumber> former = new ArrayList<>();
		List<ShiftWorkerBook.LogLine> log = ShiftWorkers.book(server).log();
		for (RecordNumber old : record.formerNumbers()) {
			long when = -1L;
			for (ShiftWorkerBook.LogLine line : log) {
				if (line.number().equals(old) && line.renumbering().isPresent()) {
					when = line.renumbering().get().gameTime();
				}
			}
			former.add(new ShiftWorkerCardPayload.FormerNumber(old.toString(), when));
		}
		return former;
	}

	private static Optional<Settlement> town(MinecraftServer server, ShiftWorkerRecord record) {
		return record.settlement() > 0 ? SettlementRegistry.get(server).find(record.settlement()) : Optional.empty();
	}

	private static void send(ServerPlayer player, ShiftWorkerCardPayload payload) {
		NetworkDispatcher dispatcher = NetworkDispatcher.get();
		if (dispatcher.canSendToPlayer(player, ShiftWorkerCardPayload.TYPE)) {
			dispatcher.sendToPlayer(player, payload);
		}
	}

	private static void send(ServerPlayer player, ShiftWorkerRosterPayload payload) {
		NetworkDispatcher dispatcher = NetworkDispatcher.get();
		if (dispatcher.canSendToPlayer(player, ShiftWorkerRosterPayload.TYPE)) {
			dispatcher.sendToPlayer(player, payload);
		}
	}
}
