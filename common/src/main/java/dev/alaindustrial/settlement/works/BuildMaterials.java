package dev.alaindustrial.settlement.works;

import dev.alaindustrial.compat.LineStructures;
import dev.alaindustrial.registry.ModTags;
import dev.alaindustrial.settlement.SettlementRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The material list of a town building and its stages (MOD-860, epic decision R84): every cell of its plan
 * ({@link BuildingPlan}) with the stage it belongs to and the item its block takes, so the stage rules
 * ({@link BuildStageRules}) can tell what the building still needs — the intact template minus what already stands.
 *
 * <p><b>Which cells count.</b> Every cell of the plan but the ruin's debris (clearing, not building) and the blocks of
 * {@link ModTags.Blocks#TOWN_BUILD_NOT_BUILT} (the core and the capsule: they stand already and no builder touches
 * them, R93). The exact block everywhere: a cell takes the item of its own block, never «any stone».
 *
 * <p><b>The item of a block</b> is the item that places it ({@link #itemOf}); how many it takes is {@link #countOf}: a
 * door, a bed, a tall plant counted once on one half, a double slab twice, three candles three times. A block the game
 * hands out no item for costs nothing and is noted ({@link BuildStageRules.StageProgress#withoutItem()}).
 *
 * <p><b>The stage of a cell</b>, first rule that answers:
 * <ol>
 *   <li>the building's definition says so ({@link TownBuildingDef#stages()}, a datapack's say, R28);</li>
 *   <li>its block is in {@link ModTags.Blocks#TOWN_BUILD_OPENINGS} → windows and doors;</li>
 *   <li>its block is in {@link ModTags.Blocks#TOWN_BUILD_INTERIOR} → fittings;</li>
 *   <li>it lies on the bottom layer → foundation;</li>
 *   <li>it lies on or above the roof line — the highest layer, above the bottom one, that covers at least
 *       {@link #ROOF_LAYER_SHARE} of the building's footprint → roof;</li>
 *   <li>otherwise → walls.</li>
 * </ol>
 *
 * <p>A staged plan is worked out once per plan and kept while its plan lives: a town forgets its plans when a
 * definition changes, and the staged plan goes with them.
 */
public final class BuildMaterials {

	/** How much of the footprint a layer must cover to be the roof line. */
	public static final double ROOF_LAYER_SHARE = 0.9;

	private static final Map<BuildingPlan, StagedPlan> STAGED = Collections.synchronizedMap(new WeakHashMap<>());

	/** Finds the material source of a town — its dispatcher's buffer (MOD-858). */
	@FunctionalInterface
	public interface SourceLookup {
		/** The material source of town {@code settlementId}, or empty while it has none that works. */
		Optional<MaterialSource> find(ServerLevel level, int settlementId);
	}

	private static volatile SourceLookup sourceLookup = (level, settlementId) -> Optional.empty();

	private BuildMaterials() {
	}

	/**
	 * Installs how a town's material source is found — the dispatcher (MOD-858) installs its buffer once, at mod
	 * start. Until then every town has none, and the «Construction» tab lists what a stage needs instead of what its
	 * buffer lacks.
	 */
	public static void installSourceLookup(SourceLookup lookup) {
		sourceLookup = Objects.requireNonNull(lookup, "lookup");
	}

	/** The material source of town {@code settlementId}, if one is installed and the town has it. */
	public static Optional<MaterialSource> source(ServerLevel level, int settlementId) {
		return sourceLookup.find(level, settlementId);
	}

	/**
	 * The staged plan of building {@code index} of town {@code settlementId}, or empty for a building with no intact
	 * template (or no definition).
	 */
	public static Optional<StagedPlan> forBuilding(ServerLevel level, int settlementId, int index) {
		Optional<BuildingPlan> plan = TownWorksScanner.plan(level, settlementId, index);
		if (plan.isEmpty()) {
			return Optional.empty();
		}
		Optional<TownBuilding> building = SettlementRegistry.get(level.getServer()).works().find(settlementId)
				.flatMap(works -> works.building(index));
		if (building.isEmpty()) {
			return Optional.empty();
		}
		TownBuildingDef def = TownBuildingDef.byTemplate(level.registryAccess()).get(building.get().template());
		return Optional.of(staged(level, plan.get(), building.get(), def));
	}

	/** The staged form of {@code plan}, worked out once. {@code def} may be null: no stage overrides then. */
	public static StagedPlan staged(ServerLevel level, BuildingPlan plan, TownBuilding building, TownBuildingDef def) {
		StagedPlan known = STAGED.get(plan);
		if (known != null) {
			return known;
		}
		Map<Long, BlockPos> locals = def == null || def.stages().isEmpty() ? Map.of() : locals(level, building);
		StagedPlan made = StagedPlan.of(plan, def == null ? List.of() : def.stages(), locals);
		STAGED.put(plan, made);
		return made;
	}

	/** The registry id of the item that places {@code state}'s block, or {@link BuildStageRules#NO_ITEM}. */
	public static String itemOf(BlockState state) {
		Item item = state.getBlock().asItem();
		if (item == Items.AIR) {
			return BuildStageRules.NO_ITEM;
		}
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/**
	 * How many of its item {@code state} takes: none for the half of a two-cell block whose other half pays (a door's
	 * or tall plant's upper half, a bed's head), two for a double slab, the count for a block that stacks several items
	 * in one cell (candles, sea pickles, turtle eggs, petals, leaf litter, snow layers), one otherwise.
	 */
	public static int countOf(BlockState state) {
		if (state.getValueOrElse(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER) == DoubleBlockHalf.UPPER
				|| state.getValueOrElse(BlockStateProperties.BED_PART, BedPart.FOOT) == BedPart.HEAD) {
			return 0;
		}
		if (state.getValueOrElse(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM) == SlabType.DOUBLE) {
			return 2;
		}
		for (IntegerProperty several : List.of(BlockStateProperties.CANDLES, BlockStateProperties.PICKLES,
				BlockStateProperties.EGGS, BlockStateProperties.FLOWER_AMOUNT, BlockStateProperties.SEGMENT_AMOUNT,
				BlockStateProperties.LAYERS)) {
			if (state.hasProperty(several)) {
				return Math.max(1, state.getValue(several));
			}
		}
		return 1;
	}

	/** The translation key of the item with registry id {@code item} — what a reason or a journal line names. */
	public static String describe(String item) {
		Identifier id = Identifier.tryParse(item);
		if (id == null) {
			return item;
		}
		return BuiltInRegistries.ITEM.getOptional(id).map(Item::getDescriptionId).orElse(item);
	}

	/** «No materials: Glass ×6, Bricks ×12» for {@code missing}, items named in the player's language. */
	public static BlockReason reason(List<BuildStageRules.Need> missing) {
		return BuildStageRules.reason(missing, BuildMaterials::describe);
	}

	/** The intact template's own coordinates of the plan's cells, by packed world position. */
	private static Map<Long, BlockPos> locals(ServerLevel level, TownBuilding building) {
		if (building.intact().isEmpty()) {
			return Map.of();
		}
		Optional<Map<BlockPos, BlockState>> cells = BuildingPlan.cellsOf(LineStructures.templates(level),
				building.intact().get());
		if (cells.isEmpty()) {
			return Map.of();
		}
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(building.rotation());
		Map<Long, BlockPos> out = new HashMap<>();
		for (BlockPos local : cells.get().keySet()) {
			BlockPos world = StructureTemplate.calculateRelativePosition(settings, local).offset(building.origin());
			out.put(world.asLong(), local.immutable());
		}
		return out;
	}

	/**
	 * A building's plan with the stage and the item of every cell. Immutable.
	 */
	public static final class StagedPlan {

		private final BuildingPlan plan;
		/** The stage of each plan cell; null for a cell the material list does not count. */
		private final BuildStage[] stages;
		private final String[] items;
		private final int[] counts;

		private StagedPlan(BuildingPlan plan, BuildStage[] stages, String[] items, int[] counts) {
			this.plan = plan;
			this.stages = stages;
			this.items = items;
			this.counts = counts;
		}

		static StagedPlan of(BuildingPlan plan, List<TownBuildingDef.StageOverride> overrides,
				Map<Long, BlockPos> locals) {
			int size = plan.size();
			boolean[] counted = new boolean[size];
			int minY = Integer.MAX_VALUE;
			Map<Integer, Integer> perLayer = new HashMap<>();
			Set<Long> columns = new HashSet<>();
			for (int i = 0; i < size; i++) {
				if (plan.debris(i)) {
					continue;
				}
				BlockPos pos = plan.pos(i);
				perLayer.merge(pos.getY(), 1, Integer::sum);
				columns.add(BlockPos.asLong(pos.getX(), 0, pos.getZ()));
				BlockState want = plan.expected(i);
				if (!want.isAir() && !want.is(ModTags.Blocks.TOWN_BUILD_NOT_BUILT)) {
					counted[i] = true;
					minY = Math.min(minY, pos.getY());
				}
			}
			int roofLine = Integer.MAX_VALUE;
			for (Map.Entry<Integer, Integer> layer : perLayer.entrySet()) {
				if (layer.getKey() > minY && layer.getValue() >= ROOF_LAYER_SHARE * columns.size()) {
					roofLine = roofLine == Integer.MAX_VALUE ? layer.getKey() : Math.max(roofLine, layer.getKey());
				}
			}
			BuildStage[] stages = new BuildStage[size];
			String[] items = new String[size];
			int[] counts = new int[size];
			for (int i = 0; i < size; i++) {
				items[i] = BuildStageRules.NO_ITEM;
				if (!counted[i]) {
					continue;
				}
				BlockState want = plan.expected(i);
				stages[i] = stageOf(want, plan.pos(i), locals.get(plan.packedPos(i)), overrides, minY, roofLine);
				items[i] = itemOf(want);
				counts[i] = countOf(want);
			}
			return new StagedPlan(plan, stages, items, counts);
		}

		private static BuildStage stageOf(BlockState want, BlockPos pos, BlockPos local,
				List<TownBuildingDef.StageOverride> overrides, int minY, int roofLine) {
			if (local != null) {
				for (TownBuildingDef.StageOverride override : overrides) {
					Optional<BuildStage> stage = override.buildStage();
					if (stage.isPresent() && override.covers(local, want)) {
						return stage.get();
					}
				}
			}
			if (want.is(ModTags.Blocks.TOWN_BUILD_OPENINGS)) {
				return BuildStage.OPENINGS;
			}
			if (want.is(ModTags.Blocks.TOWN_BUILD_INTERIOR)) {
				return BuildStage.INTERIOR;
			}
			if (pos.getY() == minY) {
				return BuildStage.FOUNDATION;
			}
			if (pos.getY() >= roofLine) {
				return BuildStage.ROOF;
			}
			return BuildStage.WALLS;
		}

		/** The plan it stages. */
		public BuildingPlan plan() {
			return plan;
		}

		/** Whether the material list counts plan cell {@code index}: not debris, not the core or the capsule. */
		public boolean counted(int index) {
			return stages[index] != null;
		}

		/** The stage of plan cell {@code index}, or empty for a cell the list does not count. */
		public Optional<BuildStage> stage(int index) {
			return Optional.ofNullable(stages[index]);
		}

		/** The registry id of the item plan cell {@code index} takes, or {@link BuildStageRules#NO_ITEM}. */
		public String item(int index) {
			return items[index];
		}

		/** How many of its item plan cell {@code index} takes. */
		public int count(int index) {
			return counts[index];
		}

		/**
		 * Plan cell {@code index} as the stage rules see it, the world holding {@code world} there; empty for a cell
		 * the list does not count. What the drone hands {@link BuildStageRules#mayPlace} and
		 * {@link BuildStageRules#consumeOnPlace}.
		 */
		public Optional<BuildStageRules.Cell> cell(int index, BlockState world) {
			if (stages[index] == null) {
				return Optional.empty();
			}
			return Optional.of(new BuildStageRules.Cell(stages[index], items[index], counts[index],
					plan.matches(index, world)));
		}

		/** Every counted cell, as the world holds them now; empty when a cell lies in a chunk nobody keeps loaded. */
		public Optional<List<BuildStageRules.Cell>> cells(Level level) {
			List<BuildStageRules.Cell> out = new ArrayList<>();
			for (int i = 0; i < stages.length; i++) {
				if (stages[i] == null) {
					continue;
				}
				BlockPos pos = plan.pos(i);
				if (!level.isLoaded(pos)) {
					return Optional.empty();
				}
				out.add(new BuildStageRules.Cell(stages[i], items[i], counts[i],
						plan.matches(i, level.getBlockState(pos))));
			}
			return Optional.of(out);
		}

		/** Where the building stands, stage by stage; empty when a cell lies in an unloaded chunk. */
		public Optional<BuildStageRules.Progress> progress(Level level) {
			return cells(level).map(BuildStageRules::progress);
		}

		/** The whole material list — every counted cell as if nothing stood yet: the intact template's bill. */
		public BuildStageRules.Progress bill() {
			List<BuildStageRules.Cell> out = new ArrayList<>();
			for (int i = 0; i < stages.length; i++) {
				if (stages[i] != null) {
					out.add(new BuildStageRules.Cell(stages[i], items[i], counts[i], false));
				}
			}
			return BuildStageRules.progress(out);
		}
	}
}
