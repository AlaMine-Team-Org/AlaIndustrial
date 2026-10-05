package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.SaveFormatTestSupport.fresh;
import static dev.alaindustrial.gametest.SaveFormatTestSupport.load;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.core.energy.EnergyBuffer;
import dev.alaindustrial.registry.ContentManifest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * L2 save -> load -> save sweep over every block entity the mod registers (MOD-701, batch 1, BE-8
 * point 1a).
 *
 * <p>For each entry of {@link ContentManifest#BLOCK_ENTITIES} a block entity of its first valid
 * block is built, every inventory slot gets a different item, the energy buffer a non-zero charge,
 * and the tag it saves is loaded into a second, fresh block entity. That one must save the same tag
 * again, and every item must be back on the index it was put on. A slot that moved, a key that is
 * written but not read, or a key that is read into a field the save path forgets all show up here,
 * for all block entities at once — before this sweep only about fourteen of them had a round trip.
 *
 * <p>The block entities are built level-free, like the restored side of every other persistence
 * scenario; see {@link SaveFormatTestSupport} for why nothing is placed in the world.
 *
 * <p><b>Exemptions</b> go into {@link #EXEMPT} with a reason, in the manner of the lane-parity
 * gate's {@code LOADER_ONLY}: a block entity whose data does not survive the trip is a finding, filed
 * as its own task and listed here with that task's id — never fixed inside this sweep and never
 * skipped silently. An exemption for an id the manifest no longer has fails the test.
 */
public final class BlockEntityPersistenceSweepScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(BlockEntityPersistenceSweepScenarios::everyBlockEntityRoundTrips,
								"save_format_every_block_entity_round_trips")
						.fabricId("SaveFormatGameTest", "everyBlockEntityRoundTrips").ticks(20, 40));

		private Roster() {}
	}

	private BlockEntityPersistenceSweepScenarios() {}

	/** Block-entity ids left out of the sweep, each with the reason or the task that tracks it. */
	private static final Map<String, String> EXEMPT = Map.of();

	/** Charge put into every energy buffer before the save: small enough for the smallest buffer. */
	private static final long CHARGE = 7L;

	/**
	 * @implements R-PER-01 — every block entity of {@code ContentManifest.BLOCK_ENTITIES} saves the
	 *     same tag after a load of its own save, with every item on its own index.
	 * @covers R-PER-01
	 */
	public static void everyBlockEntityRoundTrips(GameTestHelper helper) {
		if (ContentManifest.BLOCK_ENTITIES.isEmpty()) {
			helper.fail("ContentManifest.BLOCK_ENTITIES is empty — the sweep would check nothing");
			return;
		}
		RegistryAccess registries = helper.getLevel().registryAccess();
		List<Item> distinct = distinctItems();
		List<String> failures = new ArrayList<>();
		List<String> ids = new ArrayList<>();
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			ids.add(def.id());
			if (EXEMPT.containsKey(def.id())) {
				continue;
			}
			String problem;
			try {
				problem = sweep(def, registries, distinct);
			} catch (RuntimeException e) {
				problem = "threw " + e;
			}
			if (problem != null) {
				failures.add(def.id() + ": " + problem);
			}
		}
		for (String id : EXEMPT.keySet()) {
			if (!ids.contains(id)) {
				failures.add(id + ": is exempt but is not in ContentManifest.BLOCK_ENTITIES any more");
			}
		}
		if (!failures.isEmpty()) {
			helper.fail(failures.size() + " of " + ids.size() + " block entities do not survive a save:\n  "
					+ String.join("\n  ", failures));
			return;
		}
		helper.succeed();
	}

	/** The first thing wrong with one entry's round trip, or null. */
	private static @Nullable String sweep(ContentManifest.BlockEntityDef<?> def, RegistryAccess registries,
			List<Item> distinct) {
		Block block = BuiltInRegistries.BLOCK.getValue(Industrialization.id(def.blocks().getFirst()));
		BlockEntity source = fresh(def.factory(), block);
		if (source instanceof EnergyBlockEntity powered) {
			EnergyBuffer buffer = powered.getEnergyStorage();
			buffer.setAmountUntracked(Math.min(CHARGE, buffer.getCapacity()));
		}
		if (source instanceof Container container) {
			if (container.getContainerSize() > distinct.size()) {
				return "has " + container.getContainerSize() + " slots, more than the " + distinct.size()
						+ " distinct items the sweep can tell apart";
			}
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				container.setItem(slot, new ItemStack(distinct.get(slot)));
			}
		}
		CompoundTag first = source.saveWithoutMetadata(registries);
		BlockEntity restored = load(fresh(def.factory(), block), registries, first);
		CompoundTag second = restored.saveWithoutMetadata(registries);
		if (!first.equals(second)) {
			return "the re-save differs in " + differingKeys(first, second) + " — first " + first
					+ ", then " + second;
		}
		if (source instanceof Container before && restored instanceof Container after) {
			for (int slot = 0; slot < before.getContainerSize(); slot++) {
				if (!ItemStack.matches(before.getItem(slot), after.getItem(slot))) {
					return "slot " + slot + " held " + before.getItem(slot) + " and came back as "
							+ after.getItem(slot);
				}
			}
		}
		return null;
	}

	/** Top-level keys whose values differ between the two tags, or that only one of them has. */
	private static TreeSet<String> differingKeys(CompoundTag a, CompoundTag b) {
		TreeSet<String> keys = new TreeSet<>(a.keySet());
		keys.addAll(b.keySet());
		keys.removeIf(key -> a.contains(key) && b.contains(key) && a.get(key).equals(b.get(key)));
		return keys;
	}

	/**
	 * Vanilla items in registry order, one per slot, so that every slot of the largest inventory holds
	 * something no other slot holds. Vanilla only: a mod item's registration order moves whenever the
	 * mod adds one, a vanilla one only with a game version.
	 */
	private static List<Item> distinctItems() {
		List<Item> items = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (item != Items.AIR && "minecraft".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
				items.add(item);
			}
		}
		return items;
	}
}
