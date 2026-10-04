package dev.alaindustrial.registry.content;

import static dev.alaindustrial.registry.content.ContentDeclarations.beginBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.block;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockEntity;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockItem;
import static dev.alaindustrial.registry.content.ContentDeclarations.bucket;
import static dev.alaindustrial.registry.content.ContentDeclarations.endBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.hiddenFromPlayers;
import static dev.alaindustrial.registry.content.ContentDeclarations.item;
import static dev.alaindustrial.registry.content.ContentDeclarations.machine;
import static dev.alaindustrial.registry.content.ContentDeclarations.menu;
import static dev.alaindustrial.registry.content.ContentDeclarations.registeredBlock;
import static dev.alaindustrial.registry.content.ContentDeclarations.registeredItem;
import static dev.alaindustrial.registry.content.TabEntries.show;

import dev.alaindustrial.block.AdvancedFluidPipeBlock;
import dev.alaindustrial.block.DistillationColumnBlock;
import dev.alaindustrial.block.DistillationColumnMiddleBlock;
import dev.alaindustrial.block.DistillationColumnTopBlock;
import dev.alaindustrial.block.FermenterBlock;
import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.FluidTankBlock;
import dev.alaindustrial.block.ModLiquidBlock;
import dev.alaindustrial.block.OilFireBlock;
import dev.alaindustrial.block.OilLiquidBlock;
import dev.alaindustrial.block.PumpBlock;
import dev.alaindustrial.block.RectificationSectionBlock;
import dev.alaindustrial.block.ReinforcedFluidPipeBlock;
import dev.alaindustrial.block.ReinforcedSteamPipeBlock;
import dev.alaindustrial.block.SootLayerBlock;
import dev.alaindustrial.block.SteamPipeBlock;
import dev.alaindustrial.block.entity.DistillationColumnBlockEntity;
import dev.alaindustrial.block.entity.DistillationColumnSegmentBlockEntity;
import dev.alaindustrial.block.entity.FermenterBlockEntity;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.compat.LineBlockProps;
import dev.alaindustrial.item.fluid.FilledCapsuleItem;
import dev.alaindustrial.item.fluid.FluidTankBlockItem;
import dev.alaindustrial.item.fluid.VacuumCapsuleItem;
import dev.alaindustrial.item.misc.FluidPipeBlockItem;
import dev.alaindustrial.menu.DistillationColumnMenu;
import dev.alaindustrial.menu.FermenterMenu;
import dev.alaindustrial.menu.PumpMenu;
import dev.alaindustrial.registry.ContentManifest.BlockDef;
import dev.alaindustrial.registry.ContentManifest.BlockEntityDef;
import dev.alaindustrial.registry.ContentManifest.Domain;
import dev.alaindustrial.registry.ContentManifest.ItemDef;
import dev.alaindustrial.registry.ContentManifest.MenuDef;
import dev.alaindustrial.registry.CreativeTabContent.Sink;
import dev.alaindustrial.registry.ModBlockProperties;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/**
 * The Fluid domain of the content manifest (MOD-711; coding.md §1, owner decision D5). Pumps, tanks, fluid and
 * steam pipes, the machines that work fluids (distillation column, fermenter), buckets and capsules, and the
 * fluids' own blocks — burning oil and its soot included.
 *
 * <p>Declared here and registered through {@link dev.alaindustrial.registry.ContentManifest}, which joins the
 * ten domains in the fixed order of {@code ContentManifest.DOMAINS}; both loaders replay the joined lists. A new
 * entry of this domain is written here and nowhere else: a block between {@code beginBlocks()} and
 * {@link #BLOCKS}, an item, block entity or menu at the end of its list. This file declares no other domain's
 * entries and reads nothing of {@code ContentManifest} but its records
 * ({@code ArchitectureRules.contentDomainsInitialiseOnTheirOwn}): its static initialiser runs on its own.
 */
public final class FluidContent {
	private FluidContent() {
	}

	static {
		beginBlocks();
	}

	public static final BlockDef<PumpBlock> PUMP = block("pump", PumpBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.PUMP = s);
	public static final BlockDef<FluidTankBlock> FLUID_TANK = block("fluid_tank", FluidTankBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()), s -> ModContent.FLUID_TANK = s);
	/**
	 * The second grade (MOD-612). Same block class: the tank has no behaviour that differs by grade —
	 * the capacity comes from {@link dev.alaindustrial.core.fluid.FluidTankTier}, which reads the
	 * block. A subclass would exist only to carry a number.
	 */
	public static final BlockDef<FluidTankBlock> FLUID_TANK_ADVANCED = block("fluid_tank_advanced", FluidTankBlock::new,
			// MOD-612: the advanced grade is harder to break and twice as hard to blow up. A tank of lava
			// next to a creeper is the way this block is actually lost, so resistance is a real upgrade
			// and not a decorative number.
			machine(p -> p.strength(4.0f, 12.0f).sound(SoundType.METAL).noOcclusion()),
			s -> ModContent.FLUID_TANK_ADVANCED = s);
	public static final BlockDef<FluidPipeBlock> FLUID_PIPE = block("fluid_pipe", FluidPipeBlock::new,
			machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion()), s -> ModContent.FLUID_PIPE = s);
	// MOD-660 — the fluid pipe in a shielding jacket, the one pipe a running reactor room does not melt.
	// MOD-675 — the fluid pipe's second grade: twice the segment, twice the throughput, thicker body.
	public static final BlockDef<AdvancedFluidPipeBlock> FLUID_PIPE_ADVANCED = block("fluid_pipe_advanced",
			AdvancedFluidPipeBlock::new,
			// MOD-675 — the advanced grade takes the advanced item pipe's numbers: a sturdier body, same family.
			machine(p -> p.strength(0.3f, 0.6f).sound(SoundType.COPPER).noOcclusion()),
			s -> ModContent.FLUID_PIPE_ADVANCED = s);

	public static final BlockDef<ReinforcedFluidPipeBlock> REINFORCED_FLUID_PIPE =
			block("reinforced_fluid_pipe", ReinforcedFluidPipeBlock::new,
					// MOD-660 — a pipe in shielding plate is tougher to break, not a reactor wall: well short of
					// the casing's 5.0 / 30.0, well above the bare pipe it is built from.
					machine(p -> p.strength(1.5f, 12.0f).sound(SoundType.METAL).noOcclusion()),
					s -> ModContent.REINFORCED_FLUID_PIPE = s);
	// MOD-662 — the steam family: same pipe and block entity, steam only, never joined to a fluid pipe.
	public static final BlockDef<SteamPipeBlock> STEAM_PIPE = block("steam_pipe", SteamPipeBlock::new,
			// MOD-662 — the steam pipes take their fluid twins' numbers: the family changes what a pipe
			// carries, not how hard it is to break.
			machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion()), s -> ModContent.STEAM_PIPE = s);
	public static final BlockDef<ReinforcedSteamPipeBlock> REINFORCED_STEAM_PIPE =
			block("reinforced_steam_pipe", ReinforcedSteamPipeBlock::new,
					machine(p -> p.strength(1.5f, 12.0f).sound(SoundType.METAL).noOcclusion()),
					s -> ModContent.REINFORCED_STEAM_PIPE = s);
	// Distillation Column (MOD-251): the 1×1×3 tower. Only the base has a BlockItem; the two segment
	// blocks are placed by the base (setPlacedBy) and are never carried.
	public static final BlockDef<DistillationColumnBlock> DISTILLATION_COLUMN =
			block("distillation_column", DistillationColumnBlock::new,
					// MOD-251 — the distillation tower: all three segments share the machine chain, glowing
					// windows while working (litLight). The base drops the item; the segments have no loot.
					// noOcclusion: the tower is a chamfered ~12px column (round-2 voxel models), not a full
					// cube — a non-full collision shape MUST not occlude (R-PHY-05).
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
							.noOcclusion().lightLevel(ModBlockProperties::litLight)),
					s -> ModContent.DISTILLATION_COLUMN = s);
	public static final BlockDef<DistillationColumnMiddleBlock> DISTILLATION_COLUMN_MIDDLE =
			block("distillation_column_middle", DistillationColumnMiddleBlock::new,
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
							.noOcclusion().lightLevel(ModBlockProperties::litLight)),
					s -> ModContent.DISTILLATION_COLUMN_MIDDLE = s,
					hiddenFromPlayers("a storey the column's base places above itself; only the base has an item"));
	public static final BlockDef<DistillationColumnTopBlock> DISTILLATION_COLUMN_TOP =
			block("distillation_column_top", DistillationColumnTopBlock::new,
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
							.noOcclusion().lightLevel(ModBlockProperties::litLight)),
					s -> ModContent.DISTILLATION_COLUMN_TOP = s,
					hiddenFromPlayers("a storey the column's base places above itself; only the base has an item"));
	// Rectification Section (MOD-251 round 2): the optional fourth storey, crafted and placed by hand.
	public static final BlockDef<RectificationSectionBlock> RECTIFICATION_SECTION =
			block("rectification_section", RectificationSectionBlock::new,
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
							.noOcclusion().lightLevel(ModBlockProperties::litLight)),
					s -> ModContent.RECTIFICATION_SECTION = s);
	// Oil (MOD-238) + the two distillation fractions (MOD-251): in-world liquid blocks, no BlockItem —
	// a liquid block is never held. The fluid comes from ModContent, which BOTH loaders bind before the
	// block factory runs (Fabric: ModFluids.init() ahead of the replay; NeoForge: the FLUID
	// RegisterEvent fires before BLOCK).
	public static final BlockDef<OilLiquidBlock> OIL = block("oil", p -> new OilLiquidBlock(ModContent.OIL.get(), p),
			// Oil (MOD-238): the vanilla liquid-block chain (see Blocks.WATER in 26.2), dark map colour.
			// Not machine(...) - a liquid needs no tool and has no drops.
			p -> LineBlockProps.popsOnPush(p)
					.mapColor(MapColor.COLOR_BLACK).replaceable().noCollision().strength(100.0F).noLootTable().liquid()
					.sound(SoundType.EMPTY), s -> ModContent.OIL_BLOCK = s,
			hiddenFromPlayers("a liquid block is never held: the player places it from its bucket"));
	// ModLiquidBlock, not LiquidBlock: the vanilla constructor is protected and `common` compiles
	// against the un-widened jar (see ModLiquidBlock's javadoc). No behaviour difference.
	public static final BlockDef<ModLiquidBlock> DIESEL = block("diesel",
			p -> new ModLiquidBlock(ModContent.DIESEL.get(), p),
			// Distillation fractions (MOD-251): same vanilla liquid-block chain as oil, their own
			// map colours (diesel golden-yellow, fuel oil dark brown).
			p -> LineBlockProps.popsOnPush(p)
					.mapColor(MapColor.COLOR_YELLOW).replaceable().noCollision().strength(100.0F).noLootTable().liquid()
					.sound(SoundType.EMPTY), s -> ModContent.DIESEL_BLOCK = s,
			hiddenFromPlayers("a liquid block is never held: the player places it from its bucket"));
	public static final BlockDef<ModLiquidBlock> FUEL_OIL =
			block("fuel_oil", p -> new ModLiquidBlock(ModContent.FUEL_OIL.get(), p), p -> LineBlockProps.popsOnPush(p)
					.mapColor(MapColor.TERRACOTTA_BROWN).replaceable().noCollision().strength(100.0F).noLootTable().liquid()
					.sound(SoundType.EMPTY), s -> ModContent.FUEL_OIL_BLOCK = s,
					hiddenFromPlayers("a liquid block is never held: the player places it from its bucket"));
	// The organic chain (MOD-146/MOD-525): the machine that brews waste into biofuel, and the block
	// that sprays what the column cracks out of it.
	public static final BlockDef<FermenterBlock> FERMENTER = block("fermenter", FermenterBlock::new,
			// MOD-146: an ordinary machine cube, lit while a batch brews.
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight)), s -> ModContent.FERMENTER = s);
	// The two fluids' liquid blocks. Same treatment as the fractions above — pourable, never held.
	public static final BlockDef<ModLiquidBlock> BIOFUEL = block("biofuel",
			p -> new ModLiquidBlock(ModContent.BIOFUEL.get(), p),
			// The organic chain's two fluids (MOD-146/MOD-525): same vanilla liquid-block chain, their
			// own map colours — biofuel olive, nutrient solution a brighter green.
			p -> LineBlockProps.popsOnPush(p)
					.mapColor(MapColor.COLOR_GREEN).replaceable().noCollision().strength(100.0F).noLootTable().liquid()
					.sound(SoundType.EMPTY), s -> ModContent.BIOFUEL_BLOCK = s,
			hiddenFromPlayers("a liquid block is never held: the player places it from its bucket"));
	public static final BlockDef<ModLiquidBlock> NUTRIENT_SOLUTION = block("nutrient_solution",
			p -> new ModLiquidBlock(ModContent.NUTRIENT_SOLUTION.get(), p), p -> LineBlockProps.popsOnPush(p)
					.mapColor(MapColor.EMERALD).replaceable().noCollision().strength(100.0F).noLootTable().liquid()
					.sound(SoundType.EMPTY), s -> ModContent.NUTRIENT_SOLUTION_BLOCK = s,
			hiddenFromPlayers("a liquid block is never held: the player places it from its bucket"));
	// MOD-638 — burning oil and what it leaves behind: the fire a burning oil cell turns into (its own
	// block so "burnt out by itself" is unambiguous), and the soot layer that fire may leave on a
	// solid floor. Neither has a BlockItem — nobody holds fire, and the layer drops the `soot` item.
	public static final BlockDef<OilFireBlock> OIL_FIRE = block("oil_fire", OilFireBlock::new,
			// MOD-638 — oil fire: vanilla Blocks.FIRE's chain in 26.2 (replaceable, no collision, breaks
			// instantly, full light, no drops).
			p -> LineBlockProps.popsOnPush(p).mapColor(MapColor.FIRE).replaceable().noCollision()
					.instabreak().lightLevel(state -> 15).sound(SoundType.WOOL).noLootTable(),
			s -> ModContent.OIL_FIRE = s,
			hiddenFromPlayers("fire is never held: a burning oil cell turns into it"));
	public static final BlockDef<SootLayerBlock> SOOT_LAYER = block("soot_layer", SootLayerBlock::new,
			// MOD-638 — the soot layer: snow-like — no collision, breaks with anything, drops only to a
			// shovel (machine(...) adds requiresCorrectToolForDrops), destroyed by a piston.
			machine(p -> LineBlockProps.popsOnPush(p).mapColor(MapColor.COLOR_BLACK)
					.noCollision().noOcclusion().strength(0.1F).sound(SoundType.SAND)), s -> ModContent.SOOT_LAYER = s,
			hiddenFromPlayers("left behind only by burnt-out oil fire; a shovel takes the soot item off it"));

	/** This domain's blocks, in declaration order — collected since {@code beginBlocks()} above. */
	private static final List<BlockDef<?>> BLOCKS = endBlocks();

	private static final List<ItemDef> ITEMS = List.of(
			// Vacuum Capsule (MOD-063): empty (×64) + filled (×16, fluid in the capsule_fluid component).
			item("vacuum_capsule", VacuumCapsuleItem::new, s -> ModContent.VACUUM_CAPSULE = s),
			item("filled_vacuum_capsule", p -> new FilledCapsuleItem(p.stacksTo(FilledCapsuleItem.STACK_SIZE)
					.craftRemainder(registeredItem("vacuum_capsule"))), s -> ModContent.FILLED_VACUUM_CAPSULE = s,
					hiddenFromPlayers("the result of filling an empty vacuum_capsule, not an item to hand out")),
			// Oil Bucket (MOD-238): the vanilla WATER_BUCKET pattern — BucketItem(fluid, props with
			// craftRemainder(BUCKET).stacksTo(1)); the still fluid is resolved by id (see bucket()).
			bucket("oil_bucket", "oil", s -> ModContent.OIL_BUCKET = s),
			// Distillation fraction buckets (MOD-251) — same pattern, filled by the column's output tanks.
			bucket("diesel_bucket", "diesel", s -> ModContent.DIESEL_BUCKET = s),
			bucket("fuel_oil_bucket", "fuel_oil", s -> ModContent.FUEL_OIL_BUCKET = s),
			// The organic chain (MOD-146/MOD-525) — same vanilla BucketItem pattern.
			bucket("biofuel_bucket", "biofuel", s -> ModContent.BIOFUEL_BUCKET = s),
			bucket("nutrient_solution_bucket", "nutrient_solution", s -> ModContent.NUTRIENT_SOLUTION_BUCKET = s),
			blockItem("fluid_pipe", p -> new FluidPipeBlockItem(registeredBlock("fluid_pipe"),
					p.useBlockDescriptionPrefix()), s -> ModContent.FLUID_PIPE_ITEM = s),
			blockItem("fluid_pipe_advanced", p -> new FluidPipeBlockItem(registeredBlock("fluid_pipe_advanced"),
					p.useBlockDescriptionPrefix()), s -> ModContent.FLUID_PIPE_ADVANCED_ITEM = s),
			blockItem("reinforced_fluid_pipe", p -> new FluidPipeBlockItem(registeredBlock("reinforced_fluid_pipe"),
					p.useBlockDescriptionPrefix()), s -> ModContent.REINFORCED_FLUID_PIPE_ITEM = s),
			blockItem("steam_pipe", p -> new FluidPipeBlockItem(registeredBlock("steam_pipe"),
					p.useBlockDescriptionPrefix()), s -> ModContent.STEAM_PIPE_ITEM = s),
			blockItem("reinforced_steam_pipe", p -> new FluidPipeBlockItem(registeredBlock("reinforced_steam_pipe"),
					p.useBlockDescriptionPrefix()), s -> ModContent.REINFORCED_STEAM_PIPE_ITEM = s),
			// Distillation Column (MOD-251): one item raises the whole 1×1×3 tower; segments have no items.
			blockItem("distillation_column", s -> ModContent.DISTILLATION_COLUMN_ITEM = s),
			blockItem("rectification_section", s -> ModContent.RECTIFICATION_SECTION_ITEM = s),
			// The organic chain (MOD-146/MOD-525).
			blockItem("fermenter", s -> ModContent.FERMENTER_ITEM = s),
			blockItem("pump", s -> ModContent.PUMP_ITEM = s),
			blockItem("fluid_tank", p -> new FluidTankBlockItem(registeredBlock("fluid_tank"),
					p.useBlockDescriptionPrefix()), s -> ModContent.FLUID_TANK_ITEM = s),
			// MOD-612 — fireResistant: the advanced tank does not burn when dropped in lava. It is the
			// one lever vanilla hands out for free that matches what this block is for: the player who
			// loses a tank loses it while hauling lava.
			blockItem("fluid_tank_advanced", p -> new FluidTankBlockItem(
					registeredBlock("fluid_tank_advanced"),
					p.useBlockDescriptionPrefix().fireResistant()),
					s -> ModContent.FLUID_TANK_ADVANCED_ITEM = s));

	/*
	 * Block entities named by a constant because code outside the list refers to the entry itself
	 * (MOD-711): BlockCapabilityRoster.NO_ENERGY_CAPABILITY holds these definitions, so a block id can no
	 * longer be put there by mistake. Each still takes its own place in BLOCK_ENTITIES below.
	 */
	public static final BlockEntityDef<FluidPipeBlockEntity> FLUID_PIPE_BE =
			blockEntity("fluid_pipe", FluidPipeBlockEntity.class, FluidPipeBlockEntity::new,
					s -> ModContent.FLUID_PIPE_BE = s, FLUID_PIPE, FLUID_PIPE_ADVANCED, REINFORCED_FLUID_PIPE,
					STEAM_PIPE, REINFORCED_STEAM_PIPE);

	private static final List<BlockEntityDef<?>> BLOCK_ENTITIES = List.of(
			FLUID_PIPE_BE,
			// MOD-251 — the tower: master BE on the base, one shared proxy type on both segments.
			blockEntity("distillation_column", DistillationColumnBlockEntity.class,
					DistillationColumnBlockEntity::new, s -> ModContent.DISTILLATION_COLUMN_BE = s,
					DISTILLATION_COLUMN),
			blockEntity("distillation_column_segment", DistillationColumnSegmentBlockEntity.class,
					DistillationColumnSegmentBlockEntity::new,
					s -> ModContent.DISTILLATION_COLUMN_SEGMENT_BE = s,
					DISTILLATION_COLUMN_MIDDLE, DISTILLATION_COLUMN_TOP),
			blockEntity("pump", PumpBlockEntity.class, PumpBlockEntity::new, s -> ModContent.PUMP_BE = s, PUMP),
			// MOD-612: ONE block-entity type for both grades — it holds a fluid and nothing else, and the
			// capacity is a function of the block it sits in. A second type would add two hand-kept
			// literals to loader_parity_check and buy nothing.
			blockEntity("fluid_tank", FluidTankBlockEntity.class, FluidTankBlockEntity::new,
					s -> ModContent.FLUID_TANK_BE = s, FLUID_TANK, FLUID_TANK_ADVANCED),
			// MOD-146/MOD-525: the organic chain's two ticking blocks.
			blockEntity("fermenter", FermenterBlockEntity.class, FermenterBlockEntity::new,
					s -> ModContent.FERMENTER_BE = s, FERMENTER));

	private static final List<MenuDef<?>> MENUS = List.of(
			// MOD-251 — the distillation column: three tank gauges, warm-up bar, status line.
			menu("distillation_column", DistillationColumnMenu::new,
					s -> ModContent.DISTILLATION_COLUMN_MENU = s),
			// MOD-146 — the fermenter: one input slot, two tank gauges, two container pairs.
			menu("fermenter", FermenterMenu::new, s -> ModContent.FERMENTER_MENU = s),
			menu("pump", PumpMenu::new, s -> ModContent.PUMP_MENU = s));

	/** Everything this domain declares; {@link dev.alaindustrial.registry.ContentManifest#DOMAINS} joins it in. */
	public static final Domain DOMAIN = new Domain("Fluid", BLOCKS, ITEMS, BLOCK_ENTITIES, MENUS);

	// ---- Creative tab sections (MOD-711, batch 4; ADR-041). CreativeTabContent calls them in the tab's
	// reading order. A section holds the run of the tab that coding.md §1 files under this domain, so it may
	// show a neighbour's handle where the tab has always grouped it here.

	/** 5 - the fluid chain: source, storage, transport, then the machines that transform fluids. */
	public static void fluids(Sink out) {
		// Cubes first, shaped after — inside the group only, so the fluid chain stays one subject.
		show(out, ModContent.PUMP_ITEM);
		show(out, ModContent.POLYMERIZER_ITEM);
		show(out, ModContent.VULCANIZER_ITEM);
		show(out, ModContent.GALVANIC_BATH_ITEM);
		// MOD-146: the head of the organic chain, beside the other fluid-fed machines.
		show(out, ModContent.FERMENTER_ITEM);
		// --- shaped: the tank and the two pipes draw flat sprites, the tower is a tall model.
		show(out, ModContent.FLUID_TANK_ITEM);
		// MOD-612: the two grades stand together — a player looking for "the tank" finds both.
		show(out, ModContent.FLUID_TANK_ADVANCED_ITEM);
		show(out, ModContent.FLUID_PIPE_ITEM);
		// MOD-675: the second grade right after the first, as the item pipes stand.
		show(out, ModContent.FLUID_PIPE_ADVANCED_ITEM);
		// MOD-660: the reinforced grade beside the plain one, like the two tanks above.
		show(out, ModContent.REINFORCED_FLUID_PIPE_ITEM);
		// MOD-662: the steam family right after the fluid family, both grades together.
		show(out, ModContent.STEAM_PIPE_ITEM);
		show(out, ModContent.REINFORCED_STEAM_PIPE_ITEM);
		// The item pipe sits next to the fluid pipe: the two carriers are one idea, and a player looking
		// for "the pipe" should find both without scrolling to another group.
		show(out, ModContent.ITEM_PIPE_ITEM);
		show(out, ModContent.ITEM_PIPE_ADVANCED_ITEM);
		// MOD-251: the distillation tower - one item, three blocks tall when placed, plus its optional
		// fourth storey (losses 10 % -> 5 %).
		show(out, ModContent.DISTILLATION_COLUMN_ITEM);
		show(out, ModContent.RECTIFICATION_SECTION_ITEM);
	}

	/**
	 * 5b - the hand-carried fluids. Split out of {@link #fluids} because that group also feeds vanilla
	 * Functional Blocks, and a bucket is not a functional block (MOD-407).
	 */
	public static void fluidCarriers(Sink out) {
		show(out, ModContent.VACUUM_CAPSULE);
		show(out, ModContent.OIL_BUCKET);
		show(out, ModContent.DIESEL_BUCKET);
		show(out, ModContent.FUEL_OIL_BUCKET);
		show(out, ModContent.BIOFUEL_BUCKET);
		show(out, ModContent.NUTRIENT_SOLUTION_BUCKET);
	}
}
