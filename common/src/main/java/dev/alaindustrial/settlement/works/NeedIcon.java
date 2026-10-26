package dev.alaindustrial.settlement.works;

import java.util.Map;

/**
 * The picture of one of the town's needs on the core's tab (MOD-890, owner's playtest of 0.1.220): a need reads at a
 * glance when it carries what it is about. The server words it as a short string the client draws:
 * <ul>
 * <li>{@link #ENERGY} — the energy picture;</li>
 * <li>{@code building:<style>/<building>} — the building's picture, the one the «Construction» tab's cards draw
 * ({@link BuildingTile#icon()}); a home is the town's first house type;</li>
 * <li>{@code item:<translation key>} — the item an order lacks, by the key the need names it with.</li>
 * </ul>
 * An empty string draws no picture.
 *
 * <p>Minecraft-free on purpose: the L1 lane checks the choice ({@code NeedIconTest}).
 */
public final class NeedIcon {

	/** The energy picture. */
	public static final String ENERGY = "energy";
	/** The prefix of a building's picture, {@code <style>/<building>} after it. */
	public static final String BUILDING = "building:";
	/** The prefix of an item's picture, its translation key after it. */
	public static final String ITEM = "item:";
	/** The item a workplace need shows. */
	public static final String WORKPLACE_ITEM = "block.minecraft.crafting_table";
	/** The house type a home need shows. */
	public static final String HOUSE = "capsule_house_1";
	/** The style a town of no known building shows its home in. */
	public static final String DEFAULT_STYLE = "plains";

	private NeedIcon() {
	}

	/**
	 * The picture of {@code need}.
	 *
	 * @param buildings the town's buildings' pictures by the name key a need names them with
	 * @param style     the town's style, {@code plains} or {@code desert}
	 */
	public static String of(TownNeeds.Need need, Map<String, String> buildings, String style) {
		ConstructionRow.Text text = need.text();
		return switch (need.kind()) {
			case ENERGY -> ENERGY;
			case MATERIALS -> text.args().isEmpty() ? "" : ITEM + ConstructionRow.Text.counted(text.args().getFirst())
					.map(ConstructionRow.Text.Counted::key).orElse(text.args().getFirst());
			case HOME -> BUILDING + style + "/" + HOUSE;
			case RESTORE -> text.args().isEmpty() ? "" : building(buildings.get(text.args().getFirst()));
			case WORKPLACE -> ITEM + WORKPLACE_ITEM;
		};
	}

	/** The style of a town whose buildings show {@code icons}: the first picture's, else {@link #DEFAULT_STYLE}. */
	public static String style(Iterable<String> icons) {
		for (String icon : icons) {
			int slash = icon.indexOf('/');
			if (slash > 0) {
				return icon.substring(0, slash);
			}
		}
		return DEFAULT_STYLE;
	}

	private static String building(String icon) {
		return icon == null || icon.isEmpty() ? "" : BUILDING + icon;
	}
}
