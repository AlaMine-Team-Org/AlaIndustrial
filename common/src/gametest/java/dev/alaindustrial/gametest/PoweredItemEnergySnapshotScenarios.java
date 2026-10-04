package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.energy.ItemEnergy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * MOD-707 batch 0 — characterization of the item energy table: {@code id -> (capacity, inputRate)} for
 * EVERY item of the {@code alaindustrial} namespace, as {@link ItemEnergy} answers it with the default
 * config.
 *
 * <p>The etalon is a hand-written literal and is never regenerated: an etalon a generator rewrites
 * characterizes nothing (the MOD-201 lesson). Items absent from {@link #SNAPSHOT} must report
 * {@code (0, 0)} — finished crystals and every non-powered item included — so a lost dispatch branch and
 * a wrong branch order (the netherite drill, MOD-534) both show up as a named mismatch.
 *
 * <p>The numbers are the {@code Config} defaults. No gametest in any source set assigns the buffer or
 * input-rate knobs of these items, so reading them here does not race a neighbouring scenario; if one
 * ever does, this scenario must move to its own batch rather than start reading {@code Config}.
 */
public final class PoweredItemEnergySnapshotScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(PoweredItemEnergySnapshotScenarios::energyTableMatchesSnapshot,
								"powered_item_energy_table_matches_snapshot")
						.fabricId("PoweredItemContractGameTest", "energyTableMatchesSnapshot").ticks(20, 40));

		private Roster() {}
	}

	private PoweredItemEnergySnapshotScenarios() {
	}

	/** Capacity and per-tick input rate of one item. */
	public record Energy(long capacity, long inputRate) {
	}

	private static final Energy NONE = new Energy(0L, 0L);

	/** The etalon, by registry path. Edit by hand only, and only for an intended balance change. */
	public static final Map<String, Energy> SNAPSHOT = new TreeMap<>(Map.ofEntries(
			Map.entry("battery_pouch", new Energy(2_000L, 32L)),
			Map.entry("shielding_pouch", new Energy(2_000L, 32L)),
			Map.entry("battery", new Energy(2_000L, 32L)),
			Map.entry("energy_pack", new Energy(20_000L, 32L)),
			Map.entry("electric_drill", new Energy(10_000L, 32L)),
			Map.entry("electric_drill_diamond_tip", new Energy(10_000L, 32L)),
			Map.entry("electric_drill_netherite_tip", new Energy(15_000L, 32L)),
			Map.entry("electric_chainsaw", new Energy(10_000L, 32L)),
			Map.entry("electric_chainsaw_diamond_tip", new Energy(10_000L, 32L)),
			Map.entry("electric_shovel", new Energy(10_000L, 32L)),
			Map.entry("electric_shovel_diamond_tip", new Energy(10_000L, 32L)),
			Map.entry("electric_hoe", new Energy(10_000L, 32L)),
			Map.entry("electric_hoe_diamond_tip", new Energy(10_000L, 32L)),
			Map.entry("electric_saber", new Energy(10_000L, 32L)),
			Map.entry("electric_bow", new Energy(10_000L, 32L)),
			Map.entry("electromagnet", new Energy(5_000L, 32L)),
			Map.entry("electromagnet_advanced", new Energy(20_000L, 128L)),
			Map.entry("jetpack", new Energy(30_000L, 32L)),
			Map.entry("fluxweave_helmet", new Energy(15_000L, 32L)),
			Map.entry("fluxweave_chestplate", new Energy(15_000L, 32L)),
			Map.entry("fluxweave_leggings", new Energy(15_000L, 32L)),
			Map.entry("fluxweave_boots", new Energy(15_000L, 32L)),
			Map.entry("energy_crystal_blank", new Energy(100_000L, 128L)),
			Map.entry("lapotron_crystal_blank", new Energy(500_000L, 128L)),
			Map.entry("resonant_crystal_blank", new Energy(1_500_000L, 128L))));

	/** Every mod item answers exactly its snapshot row (or zeros), and every snapshot row names an item. */
	public static void energyTableMatchesSnapshot(GameTestHelper helper) {
		List<String> mismatches = new ArrayList<>();
		int seen = 0;
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (!Industrialization.MOD_ID.equals(id.getNamespace())) {
				continue;
			}
			Item item = BuiltInRegistries.ITEM.getValue(id);
			if (item == null) {
				continue;
			}
			ItemStack stack = new ItemStack(item);
			Energy actual = new Energy(ItemEnergy.capacity(stack), ItemEnergy.inputRate(stack));
			Energy expected = SNAPSHOT.getOrDefault(id.getPath(), NONE);
			if (SNAPSHOT.containsKey(id.getPath())) {
				seen++;
			}
			if (!expected.equals(actual)) {
				mismatches.add(id.getPath() + ": expected " + expected + ", got " + actual);
			}
		}
		if (seen != SNAPSHOT.size()) {
			helper.fail("snapshot names " + SNAPSHOT.size() + " items but only " + seen
					+ " are registered — a powered item disappeared or was renamed");
		}
		if (!mismatches.isEmpty()) {
			helper.fail("item energy table drifted from the MOD-707 snapshot: " + String.join("; ", mismatches));
		}
		helper.succeed();
	}
}
