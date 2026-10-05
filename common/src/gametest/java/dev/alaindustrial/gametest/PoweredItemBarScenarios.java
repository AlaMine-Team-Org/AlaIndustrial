package dev.alaindustrial.gametest;

import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.energy.ItemEnergy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * MOD-707 batch 0 — characterization of the charge bar of every powered item: visibility, width at an
 * empty, a half and a full buffer, and colour. Until now no test read the bar at all, and the formula is
 * repeated in thirteen classes.
 *
 * <p>Expected values are literals: width 0 / 6 / 13 (13 is the vanilla bar width; half of any even buffer
 * truncates to 6), the battery bar hidden only when empty, LV colour everywhere except the crystal blanks
 * (MV for the energy crystal, HV for lapotron and resonant).
 */
public final class PoweredItemBarScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(PoweredItemBarScenarios::barMatchesContract, "powered_item_bar_matches_contract")
						.fabricId("PoweredItemContractGameTest", "barMatchesContract").ticks(20, 40));

		private Roster() {}
	}

	private PoweredItemBarScenarios() {
	}

	private static final Map<String, Integer> COLOR_OVERRIDES = Map.of(
			"energy_crystal_blank", EnergyTier.MV.color(),
			"lapotron_crystal_blank", EnergyTier.HV.color(),
			"resonant_crystal_blank", EnergyTier.HV.color());

	public static void barMatchesContract(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (Map.Entry<String, PoweredItemEnergySnapshotScenarios.Energy> row
				: PoweredItemEnergySnapshotScenarios.SNAPSHOT.entrySet()) {
			String path = row.getKey();
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("alaindustrial", path));
			long capacity = row.getValue().capacity();
			int color = COLOR_OVERRIDES.getOrDefault(path, EnergyTier.LV.color());
			check(problems, path, item, 0L, !"battery".equals(path), 0, color);
			check(problems, path, item, capacity / 2, true, 6, color);
			check(problems, path, item, capacity, true, 13, color);
		}
		if (!problems.isEmpty()) {
			helper.fail("charge bar drifted: " + String.join("; ", problems));
		}
		helper.succeed();
	}

	private static void check(List<String> problems, String path, Item item, long eu, boolean visible,
			int width, int color) {
		ItemStack stack = new ItemStack(item);
		ItemEnergy.set(stack, eu);
		if (item.isBarVisible(stack) != visible) {
			problems.add(path + "@" + eu + " visible=" + item.isBarVisible(stack));
		}
		if (item.getBarWidth(stack) != width) {
			problems.add(path + "@" + eu + " width=" + item.getBarWidth(stack) + " (want " + width + ")");
		}
		if (item.getBarColor(stack) != color) {
			problems.add(path + "@" + eu + " color=" + Integer.toHexString(item.getBarColor(stack)));
		}
	}
}
