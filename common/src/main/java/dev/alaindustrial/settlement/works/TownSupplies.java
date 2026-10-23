package dev.alaindustrial.settlement.works;

import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.item.misc.ConstructionDroneItem;
import dev.alaindustrial.item.misc.HousePlanItem;
import dev.alaindustrial.item.misc.RestorationPlan;
import dev.alaindustrial.item.misc.RestorationPlanItem;
import dev.alaindustrial.item.misc.TownHallPlanSheetItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.drone.DroneCellSurvey;
import dev.alaindustrial.settlement.drone.DroneNestRules;
import dev.alaindustrial.settlement.drone.RoadWork;
import dev.alaindustrial.settlement.drone.TownHall;
import dev.alaindustrial.settlement.drone.TownRoadNetwork;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Everything a town still needs to be built out to the end, for the test command {@code /ala town supplies}
 * (0.1.219): the dispatcher's own bills, summed over the whole town, plus a margin, plus the items a town needs to
 * work from nothing. Never part of play: only the dev tree's command reads it.
 *
 * <p><b>What is counted</b>, each with the rule the dispatcher uses for it:
 * <ul>
 *   <li>every building the town knows that has an intact template — the hall and the houses — what its missing cells
 *       still take ({@link BuildMaterials.StagedPlan#progress}), or its whole bill when a cell lies in an unloaded
 *       chunk; a house also the earth its foundation still wants ({@link DroneCellSurvey#foundationWanted});</li>
 *   <li>the town's roads: every building's course on the road network ({@link RoadWork#course}), its road blocks and
 *       fill as {@link RoadWork#bill} counts them, a slot shared by two courses once, and any planned street cell no
 *       course passes;</li>
 *   <li>one new house of every type of the town's style, from its intact template, with the deepest foundation its
 *       footprint can take and {@link #ROAD_PER_NEW_HOUSE} road blocks and as much fill for its branch — an estimate,
 *       the house is not laid yet;</li>
 *   <li>the hall from its intact template while the town does not know its hall yet.</li>
 * </ul>
 * All of it plus {@link #MARGIN_PERCENT} percent, rounded up. Then, exactly: the drone nests the core's tier allows
 * with a restored hall and as many charged drones, the three hall sheets and the hall's restoration plan, the house
 * plans of every type, a dispatcher, and at least one house inlet and one charging bed per house.
 */
public final class TownSupplies {

	/** The margin on top of the counted materials, in percent. */
	public static final int MARGIN_PERCENT = 20;
	/** The road blocks — and as much fill — counted for the branch of a house not laid yet: an estimate. */
	public static final int ROAD_PER_NEW_HOUSE = 32;
	/** The style of a town that names none and knows no hall. */
	private static final String DEFAULT_STYLE = PlanSheetRules.BUILT_IN_STYLES.getFirst();

	private TownSupplies() {
	}

	/** Every stack of the supplies for {@code town}, full stacks first by item, the exact items after them. */
	public static List<ItemStack> stacks(ServerLevel level, Settlement town) {
		String style = styleOf(level, town);
		Map<String, Integer> counted = new LinkedHashMap<>();
		int houses = buildings(level, town.id(), counted);
		roads(level, town.id(), counted);
		for (int type = 1; type <= PlanSheetRules.HOUSE_TYPES; type++) {
			newBuilding(level, PlanSheetRules.intactTemplate(style, PlanSheetRules.house(type)), true, counted);
		}
		houses += PlanSheetRules.HOUSE_TYPES;
		boolean hallKnown = SettlementRegistry.get(level.getServer()).works().find(town.id())
				.map(works -> TownHall.index(works).isPresent()).orElse(false);
		if (!hallKnown) {
			newBuilding(level, PlanSheetRules.intactTemplate(style, PlanSheetRules.TOWN_HALL), false, counted);
		}
		Map<String, Integer> materials = new LinkedHashMap<>();
		counted.forEach((item, count) -> materials.put(item, withMargin(count)));
		atLeast(materials, ModContent.HOUSE_INLET_ITEM.get(), houses);
		atLeast(materials, ModContent.CHARGING_BED_ITEM.get(), houses);
		atLeast(materials, ModContent.TOWN_DISPATCHER_ITEM.get(), 1);
		int nests = DroneNestRules.nestLimit(town.tier(), true);
		atLeast(materials, ModContent.DRONE_NEST_ITEM.get(), nests);
		List<ItemStack> out = new ArrayList<>();
		materials.forEach((item, count) -> fullStacks(item, count, out));
		for (int i = 0; i < nests; i++) {
			out.add(ConstructionDroneItem.withCharge(DroneNestRules.batteryCapacity()));
		}
		String hallTemplate = PlanSheetRules.intactTemplate(style, PlanSheetRules.TOWN_HALL);
		Identifier styleId = Identifier.parse(hallTemplate.substring(0, hallTemplate.indexOf(':') + 1)
				+ hallTemplate.substring(hallTemplate.indexOf("ala_town/") + "ala_town/".length(),
						hallTemplate.indexOf("/intact/")));
		for (int sheet = 1; sheet <= PlanSheetRules.SHEETS; sheet++) {
			out.add(TownHallPlanSheetItem.of(styleId, sheet));
		}
		out.add(RestorationPlanItem.of(RestorationPlan.of(new PlanSheetRules.Plan(style,
				PlanSheetRules.intactTemplate(style, PlanSheetRules.TOWN_HALL)))));
		for (int type = 1; type <= PlanSheetRules.HOUSE_TYPES; type++) {
			out.add(HousePlanItem.of(RestorationPlan.of(new PlanSheetRules.Plan(style,
					PlanSheetRules.intactTemplate(style, PlanSheetRules.house(type))))));
		}
		return out;
	}

	/** {@code count} plus the margin, rounded up. */
	public static int withMargin(int count) {
		return (int) Math.ceil(count * (100 + MARGIN_PERCENT) / 100.0);
	}

	/**
	 * The town's style: its own, else the style of its hall's intact template, else the first built-in one. A style
	 * with no namespace is the mod's ({@link PlanSheetRules#intactTemplate}).
	 */
	static String styleOf(ServerLevel level, Settlement town) {
		if (!town.style().isEmpty()) {
			return town.style();
		}
		Optional<TownWorks> works = SettlementRegistry.get(level.getServer()).works().find(town.id());
		OptionalInt hall = works.map(TownHall::index).orElse(OptionalInt.empty());
		if (hall.isPresent()) {
			Optional<Identifier> intact = works.get().buildings().get(hall.getAsInt()).intact();
			if (intact.isPresent() && intact.get().getPath().startsWith("ala_town/")
					&& intact.get().getPath().contains("/intact/")) {
				String path = intact.get().getPath();
				return intact.get().getNamespace() + ":"
						+ path.substring("ala_town/".length(), path.indexOf("/intact/"));
			}
		}
		return DEFAULT_STYLE;
	}

	/** The missing materials of every building the town knows, into {@code out}; how many of them are houses. */
	private static int buildings(ServerLevel level, int town, Map<String, Integer> out) {
		Optional<TownWorks> works = SettlementRegistry.get(level.getServer()).works().find(town);
		if (works.isEmpty()) {
			return 0;
		}
		int houses = 0;
		List<TownBuilding> all = works.get().buildings();
		for (int index = 0; index < all.size(); index++) {
			boolean house = TownHouses.isHouse(all.get(index));
			if (house) {
				houses++;
			}
			Optional<BuildMaterials.StagedPlan> staged = BuildMaterials.forBuilding(level, town, index);
			if (staged.isEmpty()) {
				continue;
			}
			BuildStageRules.Progress progress = staged.get().progress(level).orElseGet(staged.get()::bill);
			for (BuildStageRules.Need need : progress.needed()) {
				out.merge(need.item(), need.count(), Integer::sum);
			}
			if (house) {
				int earth = DroneCellSurvey.foundationWanted(level, staged.get().plan());
				if (earth > 0) {
					out.merge(RoadWork.FILL_ITEM, earth, Integer::sum);
				}
			}
		}
		return houses;
	}

	/** The road blocks and fill the town's road network still takes, into {@code out}. */
	private static void roads(ServerLevel level, int town, Map<String, Integer> out) {
		Optional<TownWorks> works = SettlementRegistry.get(level.getServer()).works().find(town);
		if (works.isEmpty()) {
			return;
		}
		Set<Long> seen = new HashSet<>();
		int roads = 0;
		int fills = 0;
		for (int index = 0; index < works.get().buildings().size(); index++) {
			RoadWork.Course course = RoadWork.course(level, town, index);
			if (course.problem().isPresent()) {
				continue;
			}
			for (RoadWork.Slot slot : RoadWork.slots(course.cells())) {
				if (slot.kind() == RoadWork.Kind.CUT || !seen.add(slot.pos().asLong())) {
					continue;
				}
				boolean wants = !level.isLoaded(slot.pos()) || RoadWork.want(level, slot) != RoadWork.Want.NONE;
				if (wants && slot.kind() == RoadWork.Kind.ROAD) {
					roads++;
				} else if (wants) {
					fills++;
				}
			}
		}
		for (TownRoadNetwork.PlanCell cell : TownRoadNetwork.cells(level, town)) {
			if (!cell.built() && seen.add(cell.pos().asLong())) {
				roads++;
			}
		}
		add(out, RoadWork.ROAD_ITEM, roads);
		add(out, RoadWork.FILL_ITEM, fills);
	}

	/**
	 * The whole bill of a building not in the town yet, from intact template {@code template}: every cell as
	 * {@link BuildMaterials} prices it, the core and the capsule left out; for a house also the deepest foundation and
	 * its branch's road.
	 */
	private static void newBuilding(ServerLevel level, String template, boolean house, Map<String, Integer> out) {
		Identifier id = Identifier.tryParse(template);
		Optional<Map<BlockPos, BlockState>> cells = id == null ? Optional.empty()
				: BuildingPlan.cellsOf(LineStructures.templates(level), id);
		if (cells.isEmpty()) {
			return;
		}
		Set<Long> columns = new HashSet<>();
		for (Map.Entry<BlockPos, BlockState> cell : cells.get().entrySet()) {
			BlockState want = cell.getValue();
			if (want.isAir() || want.is(ModTags.Blocks.TOWN_BUILD_NOT_BUILT)) {
				continue;
			}
			columns.add(BlockPos.asLong(cell.getKey().getX(), 0, cell.getKey().getZ()));
			String item = BuildMaterials.itemOf(want);
			if (!item.equals(BuildStageRules.NO_ITEM)) {
				add(out, item, BuildMaterials.countOf(want));
			}
		}
		if (house) {
			add(out, RoadWork.FILL_ITEM, columns.size() * BuildingPlan.FOUNDATION_DEPTH + ROAD_PER_NEW_HOUSE);
			add(out, RoadWork.ROAD_ITEM, ROAD_PER_NEW_HOUSE);
		}
	}

	private static void add(Map<String, Integer> out, String item, int count) {
		if (count > 0) {
			out.merge(item, count, Integer::sum);
		}
	}

	private static void atLeast(Map<String, Integer> materials, Item item, int count) {
		materials.merge(BuiltInRegistries.ITEM.getKey(item).toString(), count, Math::max);
	}

	/** {@code count} of the item with registry id {@code item} as full stacks into {@code out}; unknown ids skipped. */
	private static void fullStacks(String item, int count, List<ItemStack> out) {
		Identifier id = Identifier.tryParse(item);
		Optional<Item> found = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
		if (found.isEmpty()) {
			return;
		}
		int max = Math.max(1, new ItemStack(found.get()).getMaxStackSize());
		for (int left = count; left > 0; left -= max) {
			out.add(new ItemStack(found.get(), Math.min(max, left)));
		}
	}
}
