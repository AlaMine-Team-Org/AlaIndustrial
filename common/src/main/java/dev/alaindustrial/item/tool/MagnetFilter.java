package dev.alaindustrial.item.tool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The settings of one magnet filter module (MOD-592) — the value behind the
 * {@code alaindustrial:magnet_filter} component, kept on the MODULE's stack, so a filter moved to
 * another magnet takes its list with it.
 *
 * <p><b>Entries are strings, not items.</b> A cell names an item id and, optionally, one of that item's
 * tags. A save from a build that has an item this one does not must still load: an unknown id decodes
 * fine, matches nothing and shows as an empty cell — the rule {@code DrillUpgrades} follows for the
 * same reason. Nothing about the sample's durability or data is stored, because the filter does not
 * look at either.
 *
 * <p><b>Three ways to compare, one for the whole filter.</b> By item (a stone cell matches stone), by
 * mod (a cell from mod X matches everything from mod X), by category (a cell matches every item in the
 * tag chosen for it; a cell with no tag chosen yet falls back to its item). The owner chose one switch
 * for the whole filter over one per cell: fewer buttons, and a filter is usually one kind of list.
 *
 * <p><b>The default is "everything except these".</b> A freshly crafted filter is empty, and an empty
 * deny-list lets everything through — so fitting a new filter changes nothing until the player puts
 * something in it, instead of switching the magnet off on the spot.
 */
public record MagnetFilter(List<Cell> cells, boolean allowList, Match match) {

	/** Cells a filter shows. */
	public static final int CELLS = 16;
	/**
	 * Upper bound on the list, on disk and on the wire — deliberately above {@link #CELLS}. A wire limit
	 * can be raised later but never lowered: a filter saved with more cells than a new, smaller limit
	 * would disconnect its owner at the next inventory sync.
	 */
	public static final int MAX_CELLS = 32;
	/** Upper bound on one id or tag string. */
	private static final int MAX_ID_LENGTH = 256;

	/** How a cell is compared with a pulled item. Ordinals are not stored — names are. */
	public enum Match {
		ITEM, MOD, TAG;

		private static final Match[] VALUES = values();

		public Match next() {
			return VALUES[(ordinal() + 1) % VALUES.length];
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Match byKey(String key) {
			for (Match m : VALUES) {
				if (m.key().equals(key)) {
					return m;
				}
			}
			return ITEM;
		}

		static final Codec<Match> CODEC = Codec.STRING.xmap(Match::byKey, Match::key);
	}

	/** One sample: an item id, and the tag chosen for it in category mode ({@code ""} = none). */
	public record Cell(String item, String tag) {
		public static final Cell EMPTY = new Cell("", "");

		public static final Codec<Cell> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("item", "").forGetter(Cell::item),
				Codec.STRING.optionalFieldOf("tag", "").forGetter(Cell::tag)).apply(i, Cell::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, Cell> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(MAX_ID_LENGTH), Cell::item,
				ByteBufCodecs.stringUtf8(MAX_ID_LENGTH), Cell::tag,
				Cell::new);

		public boolean isEmpty() {
			return item.isEmpty();
		}

		/** The sample as a one-item stack, or empty when the id is blank or unknown to this build. */
		public ItemStack stack() {
			Item item = itemOrNull();
			return item == null ? ItemStack.EMPTY : new ItemStack(item);
		}

		@Nullable
		Item itemOrNull() {
			if (item.isEmpty()) {
				return null;
			}
			Identifier id = Identifier.tryParse(item);
			return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		}

		@Nullable
		public TagKey<Item> tagKey() {
			if (tag.isEmpty()) {
				return null;
			}
			Identifier id = Identifier.tryParse(tag);
			return id == null ? null : TagKey.create(Registries.ITEM, id);
		}
	}

	/** A fresh filter: every cell empty, "everything except these", compared by item. */
	public static final MagnetFilter EMPTY = new MagnetFilter(List.of(), false, Match.ITEM);

	public static final Codec<MagnetFilter> CODEC = RecordCodecBuilder.create(i -> i.group(
			Cell.CODEC.sizeLimitedListOf(MAX_CELLS).optionalFieldOf("cells", List.of()).forGetter(MagnetFilter::cells),
			Codec.BOOL.optionalFieldOf("allow_list", false).forGetter(MagnetFilter::allowList),
			Match.CODEC.optionalFieldOf("match", Match.ITEM).forGetter(MagnetFilter::match))
			.apply(i, MagnetFilter::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, MagnetFilter> STREAM_CODEC = StreamCodec.composite(
			Cell.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CELLS)), MagnetFilter::cells,
			ByteBufCodecs.BOOL, MagnetFilter::allowList,
			ByteBufCodecs.STRING_UTF8.map(Match::byKey, Match::key), MagnetFilter::match,
			MagnetFilter::new);

	public MagnetFilter {
		cells = List.copyOf(cells);
	}

	/** Cell {@code index}, or empty. */
	public Cell cell(int index) {
		return index >= 0 && index < cells.size() ? cells.get(index) : Cell.EMPTY;
	}

	/** How many cells hold a sample. */
	public int filled() {
		int n = 0;
		for (Cell c : cells) {
			if (!c.isEmpty()) {
				n++;
			}
		}
		return n;
	}

	/** Whether the magnet may pull {@code stack} through this filter. */
	public boolean passes(ItemStack stack) {
		boolean hit = matchesAny(stack);
		return allowList == hit;
	}

	private boolean matchesAny(ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		for (Cell cell : cells) {
			if (!cell.isEmpty() && matches(cell, stack, id)) {
				return true;
			}
		}
		return false;
	}

	private boolean matches(Cell cell, ItemStack stack, Identifier id) {
		return switch (match) {
			case ITEM -> cell.item().equals(id.toString());
			case MOD -> {
				Identifier cellId = Identifier.tryParse(cell.item());
				yield cellId != null && cellId.getNamespace().equals(id.getNamespace());
			}
			case TAG -> {
				TagKey<Item> tag = cell.tagKey();
				yield tag != null ? stack.is(tag) : cell.item().equals(id.toString());
			}
		};
	}

	// --- edits: each returns a new value; nothing mutates one already written onto a stack ----

	/** This filter with cell {@code index} set to {@code sample} (empty clears it). */
	public MagnetFilter withCell(int index, ItemStack sample) {
		if (index < 0 || index >= CELLS) {
			return this;
		}
		Cell next = sample.isEmpty() ? Cell.EMPTY
				: new Cell(BuiltInRegistries.ITEM.getKey(sample.getItem()).toString(), "");
		return withCell(index, next);
	}

	private MagnetFilter withCell(int index, Cell next) {
		List<Cell> grown = new ArrayList<>(cells);
		while (grown.size() <= index) {
			grown.add(Cell.EMPTY);
		}
		grown.set(index, next);
		while (!grown.isEmpty() && grown.get(grown.size() - 1).isEmpty()) {
			grown.remove(grown.size() - 1);
		}
		return new MagnetFilter(grown, allowList, match);
	}

	/**
	 * This filter with the tag of cell {@code index} moved to the next one its item carries, in a
	 * stable alphabetical order, and back to "no tag" after the last. An item with no tags stays as is.
	 */
	public MagnetFilter withNextTag(int index) {
		Cell cell = cell(index);
		Item item = cell.itemOrNull();
		if (item == null) {
			return this;
		}
		List<String> tags = tagsOf(item);
		if (tags.isEmpty()) {
			return this;
		}
		int at = tags.indexOf(cell.tag());
		String next = at + 1 < tags.size() ? tags.get(at + 1) : "";
		return withCell(index, new Cell(cell.item(), next));
	}

	/** The item's tags as ids, sorted, so cycling visits them in the same order every time. */
	public static List<String> tagsOf(Item item) {
		return BuiltInRegistries.ITEM.wrapAsHolder(item).tags()
				.map(t -> t.location().toString())
				.sorted(Comparator.naturalOrder())
				.toList();
	}

	public MagnetFilter withAllowList(boolean allow) {
		return new MagnetFilter(cells, allow, match);
	}

	public MagnetFilter withMatch(Match next) {
		return new MagnetFilter(cells, allowList, next);
	}

	// --- stack access ---------------------------------------------------------------------------

	/** The filter settings on a filter-module stack. */
	public static MagnetFilter of(ItemStack module) {
		return module.getOrDefault(ModDataComponents.MAGNET_FILTER.get(), EMPTY);
	}

	/** Write {@code filter} onto a filter-module stack; a fresh filter removes the component. */
	public static void set(ItemStack module, MagnetFilter filter) {
		if (filter.equals(EMPTY)) {
			module.remove(ModDataComponents.MAGNET_FILTER.get());
		} else {
			module.set(ModDataComponents.MAGNET_FILTER.get(), filter);
		}
	}
}
