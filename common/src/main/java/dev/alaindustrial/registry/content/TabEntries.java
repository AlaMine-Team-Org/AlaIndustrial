package dev.alaindustrial.registry.content;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.CreativeTabContent.AnchoredSink;
import dev.alaindustrial.registry.CreativeTabContent.Sink;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

/**
 * The guarded verbs every creative-tab section of a domain file writes its entries with (MOD-407, MOD-555).
 *
 * <p>Moved out of {@link dev.alaindustrial.registry.CreativeTabContent} when its groups became sections of the
 * domain files (MOD-711, batch 4): the sections live here, in {@code registry/content}, and they all need the
 * same two verbs. Unchanged otherwise — a tab is a display, and it must degrade to one icon fewer, never to no
 * tab.
 */
final class TabEntries {
	private TabEntries() {
	}

	/** Logged once per run: a broken handle would otherwise print the same line for every tab rebuild. */
	private static boolean warnedAboutMissingEntry;

	/**
	 * Show one entry, and survive it being unavailable (MOD-407).
	 *
	 * <p>Every section reads a {@code ModContent} handle, and a handle that was never bound throws on
	 * {@code get()}. That throw happens INSIDE the creative-tab fill callback, so one bad entry took the whole
	 * tab with it — the player opened creative and found the mod's tab empty or the screen gone, with no clue
	 * which item caused it. A tab is a display; it must degrade to "one icon fewer", never to "no tab".
	 *
	 * <p>This is not a way to tolerate missing content: {@code CreativeTabCoverageScenarios} fails both
	 * gametest lanes when a registered item is neither listed nor flagged hidden (MOD-711, batch 5), so a real
	 * gap is caught long before a player sees it. What this guard removes is the crash as a failure mode.
	 */
	static void show(Sink out, Supplier<? extends ItemLike> handle) {
		ItemLike item = resolve(handle);
		if (item != null) {
			out.accept(item);
		}
	}

	/**
	 * Place {@code entries} after a VANILLA anchor. Unresolvable entries are skipped one by one, the same
	 * way {@link #show} skips them — a tab is a display, and it must degrade to one icon fewer.
	 */
	@SafeVarargs
	static void after(AnchoredSink out, ItemLike anchor, Supplier<? extends ItemLike>... entries) {
		List<ItemLike> items = resolveAll(entries);
		if (!items.isEmpty()) {
			out.insertAfter(anchor, items);
		}
	}

	/**
	 * Place {@code entries} after one of the mod's OWN items, already placed by an earlier call. An
	 * anchor that cannot be resolved takes its whole group with it: there is nowhere to put them.
	 */
	@SafeVarargs
	static void after(AnchoredSink out, Supplier<? extends ItemLike> anchor,
			Supplier<? extends ItemLike>... entries) {
		ItemLike resolvedAnchor = resolve(anchor);
		if (resolvedAnchor == null) {
			return;
		}
		after(out, resolvedAnchor, entries);
	}

	/** The item behind a handle, or {@code null} if it cannot be resolved. See {@link #show}. */
	private static @Nullable ItemLike resolve(Supplier<? extends ItemLike> handle) {
		try {
			return handle.get();
		} catch (RuntimeException e) {
			if (!warnedAboutMissingEntry) {
				warnedAboutMissingEntry = true;
				Industrialization.LOGGER.error(
						"[creative] an entry could not be resolved and was skipped; the rest of the tab is"
								+ " unaffected. Further occurrences this run are not logged.", e);
			}
			return null;
		}
	}

	private static List<ItemLike> resolveAll(Supplier<? extends ItemLike>[] handles) {
		List<ItemLike> items = new ArrayList<>(handles.length);
		for (Supplier<? extends ItemLike> handle : handles) {
			ItemLike item = resolve(handle);
			if (item != null) {
				items.add(item);
			}
		}
		return items;
	}
}
