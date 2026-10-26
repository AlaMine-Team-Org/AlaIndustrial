package dev.alaindustrial.settlement.works;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.DispatcherBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * The storekeeper's trips (MOD-888; epic MOD-824, decision R155, plan 0.1.220 block B6): what to carry between the
 * town's storage ({@link TownStorage}) and the dispatcher's buffer, and the carrying itself. The drones still take
 * from the buffer only (R84); the storekeeper is what fills it. Without a storekeeper nothing here runs and the
 * restorer fills the buffer by hand, as in 0.1.219.
 *
 * <p><b>Two kinds of trip.</b> {@link Kind#FETCH}: from the storage container holding most of what the town's orders
 * still lack for their current stage, a batch of it to the buffer. {@link Kind#HAUL}: once the buffer is filling
 * ({@link StorekeeperRules#filling}), a batch of what no order wants — the clearing's spoil — from the buffer into a
 * storage container with room, the warehouse's shelves first. A fetch goes first while the buffer has room.
 *
 * <p><b>The batch is a promise until it is put down</b>, as a drone's load is ({@link MaterialSource}): nothing leaves
 * the source when the storekeeper picks it up; the items move from source to target in the one tick the storekeeper
 * reaches the target ({@link #deliver}), as far as the source still holds them and the target takes them. A body that
 * is unloaded on the way, or killed, neither loses nor doubles anything; what it shows in its hand is a picture.
 */
public final class Storekeeping {

	/** What a trip does. */
	public enum Kind {
		/** Storage to the buffer: what the orders lack. */
		FETCH,
		/** The buffer to storage: the spoil no order wants. */
		HAUL
	}

	/**
	 * One trip.
	 *
	 * @param kind  fetch or haul
	 * @param from  where the storekeeper picks the batch up: a storage container, or the dispatcher
	 * @param to    where it puts the batch down: the dispatcher, or a storage container
	 * @param batch the items, most pressing first
	 */
	public record Trip(Kind kind, BlockPos from, BlockPos to, List<BuildStageRules.Need> batch) {

		public Trip {
			batch = List.copyOf(batch);
		}

		/** The storage container of the trip: where a fetch starts, where a haul ends. */
		public BlockPos storage() {
			return kind == Kind.FETCH ? from : to;
		}
	}

	private Storekeeping() {
	}

	/**
	 * What town {@code town}'s buildings under construction still lack for their current stage, beyond what
	 * {@code buffer} holds — every order, and every repair the town raised by itself, summed by item, most lacking
	 * first.
	 */
	public static List<BuildStageRules.Need> wanted(ServerLevel level, int town, DispatcherBuffer buffer) {
		Optional<TownWorks> works = SettlementRegistry.get(level.getServer()).works().find(town);
		if (works.isEmpty()) {
			return List.of();
		}
		Map<String, Integer> lack = new LinkedHashMap<>();
		for (int index : building(works.get())) {
			BuildMaterials.forBuilding(level, town, index)
					.flatMap(staged -> staged.progress(level))
					.flatMap(BuildStageRules.Progress::current)
					.ifPresent(stage -> {
						for (BuildStageRules.Need need : BuildStageRules.shortage(stage, buffer)) {
							lack.merge(need.item(), need.count(), Integer::sum);
						}
					});
		}
		List<BuildStageRules.Need> out = new ArrayList<>();
		lack.forEach((item, count) -> out.add(new BuildStageRules.Need(item, count)));
		out.sort((a, b) -> Integer.compare(b.count(), a.count()));
		return out;
	}

	/**
	 * The buildings the drones build now: the restorer's orders in their order, then any other building with an open
	 * building job — a repair the town's maintenance raised by itself.
	 */
	private static Set<Integer> building(TownWorks works) {
		Set<Integer> out = new LinkedHashSet<>(works.orders());
		for (WorkJob job : works.queue().jobs()) {
			if (job.type() == WorkJobType.BUILD && job.status().open()) {
				out.add(job.building());
			}
		}
		return out;
	}

	/** The next trip of town {@code town}'s storekeeper, or empty when there is nothing to carry. */
	public static Optional<Trip> next(ServerLevel level, int town) {
		Optional<TownDispatcherBlockEntity> dispatcher = TownDispatcherBlockEntity.of(level, town);
		if (dispatcher.isEmpty()) {
			return Optional.empty();
		}
		DispatcherBuffer buffer = dispatcher.get().buffer();
		BlockPos at = dispatcher.get().getBlockPos();
		List<TownStorage.Box> boxes = TownStorage.boxes(level, town);
		if (boxes.isEmpty()) {
			return Optional.empty();
		}
		List<BuildStageRules.Need> wanted = wanted(level, town, buffer);
		int free = freeSlots(buffer);
		if (!wanted.isEmpty() && free > 0) {
			Optional<Trip> fetch = fetch(boxes, wanted, at);
			if (fetch.isPresent()) {
				return fetch;
			}
		}
		if (StorekeeperRules.filling(free, buffer.size())) {
			List<String> wantedItems = new ArrayList<>();
			for (int index : building(SettlementRegistry.get(level.getServer()).works().town(town))) {
				BuildMaterials.forBuilding(level, town, index).flatMap(staged -> staged.progress(level))
						.ifPresent(progress -> progress.needed().forEach(need -> wantedItems.add(need.item())));
			}
			List<BuildStageRules.Need> spoil = StorekeeperRules.haul(contents(buffer), wantedItems,
					TownStorage::maxStack);
			if (!spoil.isEmpty()) {
				for (TownStorage.Box box : boxes) {
					Optional<ItemStack> first = TownStorage.byId(spoil.getFirst().item())
							.map(item -> new ItemStack(item));
					if (first.isPresent() && TownStorage.room(box.container(), first.get()) > 0) {
						return Optional.of(new Trip(Kind.HAUL, at, box.pos(), spoil));
					}
				}
			}
		}
		return Optional.empty();
	}

	/** The fetch from the box that holds the biggest batch of {@code wanted}; empty when none holds any of it. */
	private static Optional<Trip> fetch(List<TownStorage.Box> boxes, List<BuildStageRules.Need> wanted,
			BlockPos dispatcher) {
		Trip best = null;
		int bestCount = 0;
		for (TownStorage.Box box : boxes) {
			List<BuildStageRules.Need> batch = StorekeeperRules.fetch(wanted, TownStorage.contents(box.container()),
					TownStorage::maxStack);
			int count = 0;
			for (BuildStageRules.Need need : batch) {
				count += need.count();
			}
			if (count > bestCount) {
				best = new Trip(Kind.FETCH, box.pos(), dispatcher, batch);
				bestCount = count;
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * Carries {@code trip} out at its target, in this tick: moves its batch from the source to the target, as far as
	 * the source still holds it and the target takes it. Returns how many items moved; 0 when the dispatcher or the
	 * container is gone, unloaded, or no longer part of the town's storage.
	 */
	public static int deliver(ServerLevel level, int town, Trip trip) {
		Optional<TownDispatcherBlockEntity> dispatcher = TownDispatcherBlockEntity.of(level, town);
		if (dispatcher.isEmpty()) {
			return 0;
		}
		Optional<TownStorage.Box> box = TownStorage.boxes(level, town).stream()
				.filter(candidate -> candidate.pos().equals(trip.storage())).findFirst();
		if (box.isEmpty()) {
			return 0;
		}
		DispatcherBuffer buffer = dispatcher.get().buffer();
		int moved = 0;
		for (BuildStageRules.Need need : trip.batch()) {
			moved += trip.kind() == Kind.FETCH ? toBuffer(box.get(), buffer, need) : toStorage(buffer, box.get(), need);
		}
		if (moved > 0) {
			// INFO on purpose (MOD-833): the playtest protocol of the towns reads these lines from the log.
			Industrialization.LOGGER.info("[ala-towns] settlement {}: the storekeeper carried {} items {} {}", town,
					moved, trip.kind() == Kind.FETCH ? "to the dispatcher from" : "from the dispatcher to",
					trip.storage().toShortString());
		}
		return moved;
	}

	private static int toBuffer(TownStorage.Box box, DispatcherBuffer buffer, BuildStageRules.Need need) {
		int moved = 0;
		for (ItemStack stack : TownStorage.take(box.container(), need.item(), need.count())) {
			ItemStack rest = buffer.insert(stack);
			moved += stack.getCount() - rest.getCount();
			if (!rest.isEmpty()) {
				TownStorage.insert(box.container(), rest); // it came out of there a moment ago: it fits back
			}
		}
		return moved;
	}

	private static int toStorage(DispatcherBuffer buffer, TownStorage.Box box, BuildStageRules.Need need) {
		Optional<ItemStack> kind = TownStorage.byId(need.item()).map(item -> new ItemStack(item));
		if (kind.isEmpty()) {
			return 0;
		}
		int count = Math.min(Math.min(need.count(), buffer.count(need.item())),
				TownStorage.room(box.container(), kind.get()));
		if (count <= 0 || !buffer.consume(need.item(), count)) {
			return 0;
		}
		ItemStack rest = TownStorage.insert(box.container(), kind.get().copyWithCount(count));
		if (!rest.isEmpty()) {
			buffer.insert(rest);
		}
		return count - rest.getCount();
	}

	/** How many empty slots the buffer has. */
	public static int freeSlots(DispatcherBuffer buffer) {
		int free = 0;
		for (int i = 0; i < buffer.size(); i++) {
			if (buffer.get(i).isEmpty()) {
				free++;
			}
		}
		return free;
	}

	/** What the buffer holds, by item id, in slot order. */
	private static Map<String, Integer> contents(DispatcherBuffer buffer) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (int i = 0; i < buffer.size(); i++) {
			ItemStack stack = buffer.get(i);
			if (!stack.isEmpty() && stack.getComponentsPatch().isEmpty()) {
				out.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
						stack.getCount(), Integer::sum);
			}
		}
		return out;
	}
}
