package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ContentManifest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Everything the mod registers is shown to the player in a creative tab, or says why not (MOD-711, batch 5),
 * on both loaders.
 *
 * <p><b>The rule.</b> Every item of the mod's namespace is shown by some tab entry point a loader calls
 * ({@code CreativeTabContent.main} and the six vanilla insertions — exactly what
 * {@link CreativeTabSnapshotScenarios#capture()} records), unless its manifest entry carries
 * {@link ContentManifest.HiddenFromPlayers}; every block is shown through the item that places it
 * ({@code block.asItem()}), unless its entry carries the flag. A hidden entry must be shown nowhere: a flag
 * that lies is as bad as a missing one. An item or block of the namespace that no manifest entry declares
 * fails too, so nothing escapes the walk.
 *
 * <p><b>Why here and not in a text gate.</b> The rule used to live in {@code registry_check.py}: a hand
 * list of fourteen exempt ids and a text walk of {@code CreativeTabContent.java} that matched {@code ModContent}
 * slot names against ids. Visibility is behaviour — the order and the groups of the tab are decided when
 * the entry points run — so it is checked on what they hand the loader (ADR-014: one oracle per rule).
 */
public final class CreativeTabCoverageScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(CreativeTabCoverageScenarios::everyEntryIsShownOrHiddenWithAReason,
								"creative_tab_every_entry_shown_or_hidden")
						.fabricId("CreativeTabCoverageGameTest", "everyEntryIsShownOrHiddenWithAReason").ticks(20, 40));

		private Roster() {}
	}

	private CreativeTabCoverageScenarios() {}

	/** At most this many findings are spelled out in the failure message; the rest are counted. */
	private static final int SHOWN_FINDINGS = 12;

	/** Every item and block is shown in a creative tab or hidden with a reason; a hidden one is shown nowhere. */
	public static void everyEntryIsShownOrHiddenWithAReason(GameTestHelper helper) {
		Set<String> shown = shownItemIds();
		if (shown.isEmpty()) {
			helper.fail("MOD-711: the creative tab entry points showed no item at all — nothing to check against");
			return;
		}
		List<String> problems = problems(shown);
		if (!problems.isEmpty()) {
			String listed = String.join("; ", problems.subList(0, Math.min(SHOWN_FINDINGS, problems.size())));
			String more = problems.size() > SHOWN_FINDINGS ? " (+" + (problems.size() - SHOWN_FINDINGS) + " more)" : "";
			helper.fail("MOD-711: " + problems.size() + " creative tab coverage finding(s): " + listed + more
					+ ". Show the entry in CreativeTabContent, or declare it hiddenFromPlayers(\"why\") in"
					+ " ContentManifest.");
			return;
		}
		helper.succeed();
	}

	/** Ids of every item some tab entry point shows, appended or inserted after a vanilla anchor. */
	static Set<String> shownItemIds() {
		Set<String> shown = new HashSet<>();
		for (String line : CreativeTabSnapshotScenarios.capture()) {
			String entry = line.substring(line.indexOf(' ') + 1);
			if (entry.startsWith("after ")) {
				for (String id : entry.substring(entry.indexOf(": ") + 2).split(" ")) {
					shown.add(id);
				}
			} else {
				shown.add(entry);
			}
		}
		return shown;
	}

	/** Every finding of the rule in the class javadoc, for the given set of shown item ids. */
	static List<String> problems(Set<String> shown) {
		List<String> problems = new ArrayList<>();
		Map<String, ContentManifest.ItemDef> items = new HashMap<>();
		for (ContentManifest.ItemDef def : ContentManifest.ITEMS) {
			items.put(def.id(), def);
		}
		for (Item item : BuiltInRegistries.ITEM) {
			Identifier key = BuiltInRegistries.ITEM.getKey(item);
			if (!Industrialization.MOD_ID.equals(key.getNamespace())) {
				continue;
			}
			ContentManifest.ItemDef def = items.get(key.getPath());
			if (def == null) {
				problems.add("item " + key + " is registered but declared by no ContentManifest.ITEMS entry");
				continue;
			}
			judge(problems, "item " + key, shown.contains(key.toString()), def.hidden());
		}
		Map<String, ContentManifest.BlockDef<?>> blocks = new HashMap<>();
		for (ContentManifest.BlockDef<?> def : ContentManifest.BLOCKS) {
			blocks.put(def.id(), def);
		}
		for (Block block : BuiltInRegistries.BLOCK) {
			Identifier key = BuiltInRegistries.BLOCK.getKey(block);
			if (!Industrialization.MOD_ID.equals(key.getNamespace())) {
				continue;
			}
			ContentManifest.BlockDef<?> def = blocks.get(key.getPath());
			if (def == null) {
				problems.add("block " + key + " is registered but declared by no ContentManifest.BLOCKS entry");
				continue;
			}
			Item placer = block.asItem();
			boolean isShown = placer != Items.AIR && shown.contains(BuiltInRegistries.ITEM.getKey(placer).toString());
			judge(problems, "block " + key + (placer == Items.AIR ? " (no item places it)"
					: " (placed by " + BuiltInRegistries.ITEM.getKey(placer) + ")"), isShown, def.hidden());
		}
		return problems;
	}

	private static void judge(List<String> problems, String what, boolean isShown,
			ContentManifest.HiddenFromPlayers hidden) {
		if (hidden == null && !isShown) {
			problems.add(what + " is in no creative tab and is not declared hiddenFromPlayers");
		} else if (hidden != null && isShown) {
			problems.add(what + " is declared hiddenFromPlayers (\"" + hidden.reason() + "\") but a creative tab"
					+ " shows it");
		}
	}
}
