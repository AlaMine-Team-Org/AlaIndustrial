package dev.alaindustrial.registry.content;

import static dev.alaindustrial.registry.content.ContentDeclarations.beginBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.block;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockEntity;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockItem;
import static dev.alaindustrial.registry.content.ContentDeclarations.endBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.hiddenFromPlayers;
import static dev.alaindustrial.registry.content.ContentDeclarations.machine;
import static dev.alaindustrial.registry.content.ContentDeclarations.menu;
import static dev.alaindustrial.registry.content.ContentDeclarations.plain;
import static dev.alaindustrial.registry.content.TabEntries.show;

import dev.alaindustrial.block.CrystalFarmControllerBlock;
import dev.alaindustrial.block.CrystalFarmDoorBlock;
import dev.alaindustrial.block.CrystalFarmShellBlock;
import dev.alaindustrial.block.CrystalSeedbedBlock;
import dev.alaindustrial.block.GardenDroneStationBlock;
import dev.alaindustrial.block.IncubatorBlock;
import dev.alaindustrial.block.IncubatorDomeBlock;
import dev.alaindustrial.block.KokSagyzBlock;
import dev.alaindustrial.block.KokSagyzRootBlock;
import dev.alaindustrial.block.SprinklerBlock;
import dev.alaindustrial.block.TrellisBlock;
import dev.alaindustrial.block.entity.CrystalFarmControllerBlockEntity;
import dev.alaindustrial.block.entity.GardenDroneStationBlockEntity;
import dev.alaindustrial.block.entity.IncubatorBlockEntity;
import dev.alaindustrial.block.entity.KokSagyzRootBlockEntity;
import dev.alaindustrial.block.entity.SprinklerBlockEntity;
import dev.alaindustrial.compat.LineBlockProps;
import dev.alaindustrial.menu.GardenDroneStationMenu;
import dev.alaindustrial.menu.IncubatorMenu;
import dev.alaindustrial.menu.SprinklerMenu;
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

/**
 * The Agriculture domain of the content manifest (MOD-711; coding.md §1, owner decision D5). Farming: the
 * incubator, the garden drone station, the sprinkler, the trellis and kok sagyz crops, and the crystal
 * greenhouse.
 *
 * <p>Declared here and registered through {@link dev.alaindustrial.registry.ContentManifest}, which joins the
 * ten domains in the fixed order of {@code ContentManifest.DOMAINS}; both loaders replay the joined lists. A new
 * entry of this domain is written here and nowhere else: a block between {@code beginBlocks()} and
 * {@link #BLOCKS}, an item, block entity or menu at the end of its list. This file declares no other domain's
 * entries and reads nothing of {@code ContentManifest} but its records
 * ({@code ArchitectureRules.contentDomainsInitialiseOnTheirOwn}): its static initialiser runs on its own.
 */
public final class AgricultureContent {
	private AgricultureContent() {
	}

	static {
		beginBlocks();
	}

	public static final BlockDef<GardenDroneStationBlock> GARDEN_DRONE_STATION =
			block("garden_drone_station", GardenDroneStationBlock::new,
					// noOcclusion: the dock is a 4px plate, not a full cube — without it the faces below/around
					// it would be culled as if a solid block sat there.
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()),
					s -> ModContent.GARDEN_DRONE_STATION = s);
	public static final BlockDef<IncubatorBlock> INCUBATOR = block("incubator", IncubatorBlock::new,
			// The emitter ring lights the chamber while an operation runs, so the block emits too.
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::litLight)), s -> ModContent.INCUBATOR = s);
	public static final BlockDef<IncubatorDomeBlock> INCUBATOR_DOME = block("incubator_dome", IncubatorDomeBlock::new,
			// The dome is see-through: noOcclusion keeps the chamber (and the item inside) visible.
			// A piston must not take it: the dome is half of a multiblock and its glass is remembered
			// by the base below, so moving it away from its base would strand both.
			machine(p -> LineBlockProps.pinnedAgainstPistons(p).strength(1.0f, 2.0f)
					.sound(SoundType.GLASS).noOcclusion()), s -> ModContent.INCUBATOR_DOME = s,
			hiddenFromPlayers("the incubator places its own dome; the dome has no item"));
	// Cotton trellis (MOD-280) — the mod's first crop; a two-block plant support, not a machine.
	public static final BlockDef<TrellisBlock> TRELLIS = block("trellis", TrellisBlock::new,
			// Cotton trellis (MOD-280) — a plant, not a machine: no requiresCorrectToolForDrops (it comes
			// apart by hand), and randomTicks() is load-bearing rather than decoration — without it the
			// block never receives randomTick and the crop would simply never grow. Deliberately NOT
			// noCollision: the trellis is a structure the player builds, so it blocks movement like a fence
			// post rather than being walked through like wheat. A piston must not drag half a two-block
			// plant away from its other half.
			p -> LineBlockProps.popsOnPush(p).strength(0.2f).sound(SoundType.GRASS)
					.noOcclusion().randomTicks(), s -> ModContent.TRELLIS = s);
	// MOD-537 — kok sagyz, the rubber dandelion. Two blocks, three states of one plant: the flower
	// the player plants, and the root column it grows downward (tip=true is the harvestable end).
	// The root has no BlockItem of its own: it is dug, never placed — its plain item is the harvest.
	public static final BlockDef<KokSagyzBlock> KOK_SAGYZ = block("kok_sagyz", KokSagyzBlock::new,
			// MOD-537 — kok sagyz. A vanilla-flower block: instabreak, walked through, and randomTicks()
			// is load-bearing (the plant advances on the random tick, like the trellis). A piston must
			// not drag the flower away from the root column it owns.
			p -> LineBlockProps.popsOnPush(p).instabreak().sound(SoundType.GRASS)
					.noCollision().randomTicks(), s -> ModContent.KOK_SAGYZ = s);
	public static final BlockDef<KokSagyzRootBlock> KOK_SAGYZ_ROOT = block("kok_sagyz_root", KokSagyzRootBlock::new,
			// The root is a full dirt-strength cube and ticks never: growth is driven from the flower
			// above, so a random tick here would be work nothing reads.
			p -> p.strength(0.6f).sound(SoundType.ROOTED_DIRT), s -> ModContent.KOK_SAGYZ_ROOT = s,
			hiddenFromPlayers("dug, never placed: it grows from the flower; the kok_sagyz_root item is the harvest"));

	// ── MOD-505: the crystal greenhouse. Glass and door come from tags, and what grows is vanilla
	// amethyst, so the mod adds only the deck, the brain and the bed. ──
	/** The deck a greenhouse stands on; wears the sealed look once the room passes its scan. */
	public static final BlockDef<CrystalFarmShellBlock> CRYSTAL_FARM_FLOOR =
			block("crystal_farm_floor", CrystalFarmShellBlock::new,
					// ── MOD-505: the crystal greenhouse. Ordinary machine-grade blocks — the room contains
					// nothing more dangerous than a growing crystal, so none of the reactor's toughness.
					// Deliberately NO randomTicks() anywhere here: growth is driven by the controller's tick,
					// so a random tick would be work the farm never reads.
					// pushReaction BLOCK on all three: they carry the sealed look (and the seedbed its "tended"
					// flag), and a piston shoving one clear of the room's footprint would strand it wearing a
					// state nothing owns any more — the sweep only reaches the box it remembers (found by audit).
					// The incubator's dome is pinned for the same class of reason.
					machine(p -> LineBlockProps.pinnedAgainstPistons(p).strength(3.0f, 6.0f)
							.sound(SoundType.METAL)), s -> ModContent.CRYSTAL_FARM_FLOOR = s);
	/** The glazing above it — same class, same sealed look, so the dome closes with the floor. */
	public static final BlockDef<CrystalFarmShellBlock> CRYSTAL_FARM_GLASS =
			block("crystal_farm_glass", CrystalFarmShellBlock::new,
					// noOcclusion is mandatory on the glazing: a transparent full cube that occludes would cull
					// the room away behind it and the greenhouse would show nothing (the reactor glass note).
					machine(p -> LineBlockProps.pinnedAgainstPistons(p).strength(3.0f, 6.0f)
							.sound(SoundType.GLASS).noOcclusion()), s -> ModContent.CRYSTAL_FARM_GLASS = s);
	/** The way in: a glazed door in the same frame, so the shell is not broken by a wooden one. */
	public static final BlockDef<CrystalFarmDoorBlock> CRYSTAL_FARM_DOOR =
			block("crystal_farm_door", CrystalFarmDoorBlock::new,
					// A door is never a full cube, so noOcclusion is mandatory; popping on a push keeps a piston
					// from tearing one half of a two-block door away from the other.
					machine(p -> LineBlockProps.popsOnPush(p).strength(3.0f, 6.0f)
							.sound(SoundType.COPPER).noOcclusion()), s -> ModContent.CRYSTAL_FARM_DOOR = s);
	/** The room's brain: seals the greenhouse, then grows every seedbed inside it. */
	public static final BlockDef<CrystalFarmControllerBlock> CRYSTAL_FARM_CONTROLLER =
			block("crystal_farm_controller", CrystalFarmControllerBlock::new,
					machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)),
					s -> ModContent.CRYSTAL_FARM_CONTROLLER = s);
	/** Dead until fed amethyst, then buds real vanilla clusters until its charge runs out. */
	public static final BlockDef<CrystalSeedbedBlock> CRYSTAL_SEEDBED =
			block("crystal_seedbed", CrystalSeedbedBlock::new,
					// The bed is a block of amethyst that happens to be machinery, so it sounds like the stone
					// it is made of rather than like metal — the cue that it is the thing crystals come out of.
					machine(p -> LineBlockProps.pinnedAgainstPistons(p).strength(3.0f, 6.0f)
							.sound(SoundType.AMETHYST)), s -> ModContent.CRYSTAL_SEEDBED = s);
	public static final BlockDef<SprinklerBlock> SPRINKLER = block("sprinkler", SprinklerBlock::new,
			// MOD-525: base plus mast, so the shape is far from a full cube — noOcclusion is mandatory
			// or it would cull its neighbours' faces as if a solid block stood there (R-PHY-05).
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()), s -> ModContent.SPRINKLER = s);

	/** This domain's blocks, in declaration order — collected since {@code beginBlocks()} above. */
	private static final List<BlockDef<?>> BLOCKS = endBlocks();

	private static final List<ItemDef> ITEMS = List.of(
			// Cotton (MOD-280): the seed is planted onto a trellis by right-click (the block handles it, so this
			// stays a plain Item — no BlockItem/ItemNameBlockItem), the fibre is the harvest.
			plain("cotton_seeds", s -> ModContent.COTTON_SEEDS = s),
			plain("cotton_fiber", s -> ModContent.COTTON_FIBER = s),
			// Kok sagyz (MOD-537): the dug root macerates into raw rubber; inulin rides along as the
			// by-product. The seeds are a BlockItem — see the blockItem block below.
			plain("kok_sagyz_root", s -> ModContent.KOK_SAGYZ_ROOT_ITEM = s),
			// MOD-537 — the kok sagyz harvest and its by-product: the dug root (macerable into
			// raw rubber + inulin) and the inulin itself.
			plain("inulin", s -> ModContent.INULIN = s),
			blockItem("sprinkler", s -> ModContent.SPRINKLER_ITEM = s),
			blockItem("incubator", s -> ModContent.INCUBATOR_ITEM = s),
			blockItem("trellis", s -> ModContent.TRELLIS_ITEM = s),
			// MOD-537 — the seeds carry the flower's id-in-name-only ("kok_sagyz_seeds"): planting is just
			// placing the block, so a BlockItem is exactly right. The root has NO block item: it is dug,
			// never placed — the plain "kok_sagyz_root" item above is the harvest.
			blockItem("kok_sagyz_seeds", "kok_sagyz", s -> ModContent.KOK_SAGYZ_SEEDS = s),
			// MOD-505 — the greenhouse. The bud has no item: it is grown, never placed.
			blockItem("crystal_farm_floor", s -> ModContent.CRYSTAL_FARM_FLOOR_ITEM = s),
			blockItem("crystal_farm_glass", s -> ModContent.CRYSTAL_FARM_GLASS_ITEM = s),
			blockItem("crystal_farm_door", s -> ModContent.CRYSTAL_FARM_DOOR_ITEM = s),
			blockItem("crystal_farm_controller", s -> ModContent.CRYSTAL_FARM_CONTROLLER_ITEM = s),
			blockItem("crystal_seedbed", s -> ModContent.CRYSTAL_SEEDBED_ITEM = s),
			blockItem("garden_drone_station", s -> ModContent.GARDEN_DRONE_STATION_ITEM = s),
			plain("garden_drone", s -> ModContent.GARDEN_DRONE = s));

	/*
	 * Block entities named by a constant because code outside the list refers to the entry itself
	 * (MOD-711): BlockCapabilityRoster.NO_ENERGY_CAPABILITY holds these definitions, so a block id can no
	 * longer be put there by mistake. Each still takes its own place in BLOCK_ENTITIES below.
	 */
	public static final BlockEntityDef<SprinklerBlockEntity> SPRINKLER_BE =
			blockEntity("sprinkler", SprinklerBlockEntity.class, SprinklerBlockEntity::new,
					s -> ModContent.SPRINKLER_BE = s, SPRINKLER);

	private static final List<BlockEntityDef<?>> BLOCK_ENTITIES = List.of(
			blockEntity("kok_sagyz_root", KokSagyzRootBlockEntity.class,
					KokSagyzRootBlockEntity::new,
					s -> ModContent.KOK_SAGYZ_ROOT_BE = s, KOK_SAGYZ_ROOT),
			blockEntity("incubator", IncubatorBlockEntity.class, IncubatorBlockEntity::new,
					s -> ModContent.INCUBATOR_BE = s, INCUBATOR),
			blockEntity("garden_drone_station", GardenDroneStationBlockEntity.class, GardenDroneStationBlockEntity::new,
					s -> ModContent.GARDEN_DRONE_STATION_BE = s, GARDEN_DRONE_STATION),
			// MOD-505: the greenhouse's only ticking object. The seedbeds and buds it drives have none,
			// which is what lets one room hold a hundred of them without a hundred tickers.
			blockEntity("crystal_farm_controller", CrystalFarmControllerBlockEntity.class,
					CrystalFarmControllerBlockEntity::new, s -> ModContent.CRYSTAL_FARM_CONTROLLER_BE = s,
					CRYSTAL_FARM_CONTROLLER),
			SPRINKLER_BE);

	private static final List<MenuDef<?>> MENUS = List.of(
			menu("incubator", IncubatorMenu::new, s -> ModContent.INCUBATOR_MENU = s),
			// MOD-525 — the sprinkler: one gauge and a container pair, the mod's smallest machine menu.
			menu("sprinkler", SprinklerMenu::new, s -> ModContent.SPRINKLER_MENU = s),
			menu("garden_drone_station", GardenDroneStationMenu::new,
					s -> ModContent.GARDEN_DRONE_STATION_MENU = s));

	/** Everything this domain declares; {@link dev.alaindustrial.registry.ContentManifest#DOMAINS} joins it in. */
	public static final Domain DOMAIN = new Domain("Agriculture", BLOCKS, ITEMS, BLOCK_ENTITIES, MENUS);

	// ---- Creative tab sections (MOD-711, batch 4; ADR-041). CreativeTabContent calls them in the tab's
	// reading order. A section holds the run of the tab that coding.md §1 files under this domain, so it may
	// show a neighbour's handle where the tab has always grouped it here.

	/**
	 * 4b - the farm machines that close the processing line in the tab: the incubator, the garden drone
	 * station with the drone it flies, and the sprinkler. Shown right after {@code ProcessingContent.machines}
	 * in the mod's tab and in vanilla Functional Blocks, where they always stood (MOD-407, MOD-604).
	 */
	public static void farmMachines(Sink out) {
		// Agriculture opens with the incubator: MOD-604 gave it a volumetric model, so it is no longer
		// a cube and cannot stand among them (creative_tab_shape_check). It also heads the farming run
		// that follows, which is where it belonged by theme all along.
		show(out, ModContent.INCUBATOR_ITEM);
		// Agriculture: the station and the drone it flies, kept adjacent.
		show(out, ModContent.GARDEN_DRONE_STATION_ITEM);
		show(out, ModContent.GARDEN_DRONE);
		// MOD-525: the sprinkler belongs with the farm blocks, not with the machines — it takes
		// no cable and its whole job is the plot around it.
		show(out, ModContent.SPRINKLER_ITEM);
	}

	/**
	 * The plantings and the greenhouse, after the ores in vanilla Natural Blocks and in the mod's own block
	 * run: the trellis, the crystal farm (MOD-505), cotton and kok-sagyz (MOD-537).
	 */
	public static void crops(Sink out) {
		show(out, ModContent.TRELLIS_ITEM);
		// MOD-505 — the greenhouse, next to the mod's other agriculture blocks.
		show(out, ModContent.CRYSTAL_FARM_FLOOR_ITEM);
		show(out, ModContent.CRYSTAL_FARM_GLASS_ITEM);
		show(out, ModContent.CRYSTAL_FARM_DOOR_ITEM);
		show(out, ModContent.CRYSTAL_FARM_CONTROLLER_ITEM);
		show(out, ModContent.CRYSTAL_SEEDBED_ITEM);
		show(out, ModContent.COTTON_SEEDS);
		// MOD-537 — the second crop, next to the first: seeds to plant, the dug root, and its by-product.
		show(out, ModContent.KOK_SAGYZ_SEEDS);
		show(out, ModContent.KOK_SAGYZ_ROOT_ITEM);
		show(out, ModContent.INULIN);
	}
}
