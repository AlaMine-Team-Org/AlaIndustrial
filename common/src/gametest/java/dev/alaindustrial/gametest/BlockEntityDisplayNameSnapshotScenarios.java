package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.SaveFormatTestSupport.fresh;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ContentManifest;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * L2 characterization of every screen title (MOD-712, batch 0, BE-10): the translation key
 * {@code getDisplayName()} returns for each block entity that opens a menu, for every block it serves.
 *
 * <p>The key is compared, not the text — the text depends on the language, the key is what the 24 lang
 * files are written against. Before BE-10 forty block entities spelled their own title out; after it the
 * base derives the title from the block's description id. The snapshot is what proves the derived title
 * is the same key for every one of them, including the three that are not {@code block.<modid>.<id>}.
 *
 * <p>Block entities are built level-free with each block's default state ({@link SaveFormatTestSupport}).
 */
public final class BlockEntityDisplayNameSnapshotScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(BlockEntityDisplayNameSnapshotScenarios::screenTitlesMatchSnapshot,
								"machine_char_display_name_snapshot")
						.ticks(100));

		private Roster() {}
	}

	private BlockEntityDisplayNameSnapshotScenarios() {}

	/**
	 * @implements MOD-712-CH03 — every block entity that is a {@code MenuProvider} titles its screen with
	 *     the reviewed translation key, for every block it serves.
	 */
	public static void screenTitlesMatchSnapshot(GameTestHelper helper) {
		ReferenceLines.compare(helper, "BlockEntityDisplayNameSnapshot",
				"Reviewed screen-title keys of every menu-opening block entity (MOD-712) — the reference"
						+ " {@link BlockEntityDisplayNameSnapshotScenarios} compares against.",
				"*:machine_char_display_name*", currentTitles(), BlockEntityDisplayNameSnapshot.LINES);
	}

	/** One line per (block entity, block), in manifest order. */
	static List<String> currentTitles() {
		List<String> lines = new ArrayList<>();
		for (ContentManifest.BlockEntityDef<?> def : ContentManifest.BLOCK_ENTITIES) {
			for (String blockId : def.blocks()) {
				Block block = BuiltInRegistries.BLOCK.getValue(Industrialization.id(blockId));
				BlockEntity be = fresh(def.factory(), block);
				if (be instanceof MenuProvider provider) {
					lines.add(def.id() + " @" + blockId + ": " + keyOf(provider.getDisplayName()));
				}
			}
		}
		return lines;
	}

	private static String keyOf(Component title) {
		return title.getContents() instanceof TranslatableContents translatable
				? translatable.getKey() : "literal \"" + title.getString() + "\"";
	}
}
