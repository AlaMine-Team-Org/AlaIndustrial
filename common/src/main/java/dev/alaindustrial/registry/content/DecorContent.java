package dev.alaindustrial.registry.content;

import static dev.alaindustrial.registry.content.ContentDeclarations.beginBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.block;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockEntity;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockItem;
import static dev.alaindustrial.registry.content.ContentDeclarations.endBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.machine;
import static dev.alaindustrial.registry.content.ContentDeclarations.menu;
import static dev.alaindustrial.registry.content.ContentDeclarations.registeredBlock;
import static dev.alaindustrial.registry.content.TabEntries.show;

import dev.alaindustrial.block.EngravedPlateBlock;
import dev.alaindustrial.block.EnrichedUraniumTorchBlock;
import dev.alaindustrial.block.EnrichedUraniumWallTorchBlock;
import dev.alaindustrial.block.MobRepellerBlock;
import dev.alaindustrial.block.MobRepellerHvBlock;
import dev.alaindustrial.block.MobRepellerMvBlock;
import dev.alaindustrial.block.entity.MobRepellerBlockEntity;
import dev.alaindustrial.block.entity.MobRepellerHvBlockEntity;
import dev.alaindustrial.block.entity.MobRepellerMvBlockEntity;
import dev.alaindustrial.menu.MobRepellerHvMenu;
import dev.alaindustrial.menu.MobRepellerMenu;
import dev.alaindustrial.menu.MobRepellerMvMenu;
import dev.alaindustrial.registry.ContentManifest.BlockDef;
import dev.alaindustrial.registry.ContentManifest.BlockEntityDef;
import dev.alaindustrial.registry.ContentManifest.Domain;
import dev.alaindustrial.registry.ContentManifest.ItemDef;
import dev.alaindustrial.registry.ContentManifest.MenuDef;
import dev.alaindustrial.registry.CreativeTabContent.Sink;
import dev.alaindustrial.registry.ModBlockProperties;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModParticles;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

/**
 * The Decor domain of the content manifest (MOD-711; coding.md §1, owner decision D5). Blocks with no machine
 * function: metal and plate blocks, the engraved lab plaque, the enriched uranium torch and the mob repellers.
 *
 * <p>Declared here and registered through {@link dev.alaindustrial.registry.ContentManifest}, which joins the
 * ten domains in the fixed order of {@code ContentManifest.DOMAINS}; both loaders replay the joined lists. A new
 * entry of this domain is written here and nowhere else: a block between {@code beginBlocks()} and
 * {@link #BLOCKS}, an item, block entity or menu at the end of its list. This file declares no other domain's
 * entries and reads nothing of {@code ContentManifest} but its records
 * ({@code ArchitectureRules.contentDomainsInitialiseOnTheirOwn}): its static initialiser runs on its own.
 */
public final class DecorContent {
	private DecorContent() {
	}

	static {
		beginBlocks();
	}

	public static final BlockDef<MobRepellerBlock> MOB_REPELLER = block("mob_repeller", MobRepellerBlock::new,
			// MOD-278 — the guard field. The soul emitter glows while the field is up, which is also the
			// "it is powered" signal; identical chain on all three tiers so only the trim differs.
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::repellerLight)), s -> ModContent.MOB_REPELLER = s);
	public static final BlockDef<MobRepellerMvBlock> MOB_REPELLER_MV = block("mob_repeller_mv", MobRepellerMvBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::repellerLight)), s -> ModContent.MOB_REPELLER_MV = s);
	public static final BlockDef<MobRepellerHvBlock> MOB_REPELLER_HV = block("mob_repeller_hv", MobRepellerHvBlock::new,
			machine(p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
					.lightLevel(ModBlockProperties::repellerLight)), s -> ModContent.MOB_REPELLER_HV = s);
	// Material / decorative full cubes: cube_all model, one texture per block.
	public static final BlockDef<Block> TEMPERED_IRON_BLOCK = block("tempered_iron_block", Block::new,
			machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.TEMPERED_IRON_BLOCK = s);
	public static final BlockDef<Block> SILVER_PLATE_BLOCK = block("silver_plate_block", Block::new,
			machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.SILVER_PLATE_BLOCK = s);
	public static final BlockDef<Block> TEMPERED_IRON_PLATE_BLOCK = block("tempered_iron_plate_block", Block::new,
			machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.TEMPERED_IRON_PLATE_BLOCK = s);
	// Enriched Uranium Torch (MOD-085) — vanilla-behaviour torch, light 14, green flame.
	// The WALL variant must stay directly after the standing one: its properties read the
	// already-registered standing torch for its loot table and description.
	public static final BlockDef<EnrichedUraniumTorchBlock> ENRICHED_URANIUM_TORCH =
			block("enriched_uranium_torch", p -> new EnrichedUraniumTorchBlock(ModParticles.ENRICHED_URANIUM_FLAME, p),
					ModBlockProperties::applyTorch, s -> ModContent.ENRICHED_URANIUM_TORCH = s);
	public static final BlockDef<EnrichedUraniumWallTorchBlock> ENRICHED_URANIUM_WALL_TORCH =
			block("enriched_uranium_wall_torch",
					p -> new EnrichedUraniumWallTorchBlock(ModParticles.ENRICHED_URANIUM_FLAME, p),
					// The wall variant adds the vanilla wallVariant mirroring (loot table + description of the
					// standing torch). MOD-403 moved that override off the two loader files into the shared
					// helper — see ModBlockProperties#applyWallTorch for the ordering it relies on.
					ModBlockProperties::applyWallTorch, s -> ModContent.ENRICHED_URANIUM_WALL_TORCH = s);
	// MOD-513 — the lab plaque: ten engraved digits and seven broken prefix letters. Decorative only.
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_0 =
			block("engraved_plate_0", EngravedPlateBlock::new,
					// MOD-513 — the lab plaque plates: polished deepslate in hardness and sound.
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_0 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_1 =
			block("engraved_plate_1", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_1 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_2 =
			block("engraved_plate_2", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_2 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_3 =
			block("engraved_plate_3", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_3 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_4 =
			block("engraved_plate_4", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_4 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_5 =
			block("engraved_plate_5", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_5 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_6 =
			block("engraved_plate_6", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_6 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_7 =
			block("engraved_plate_7", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_7 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_8 =
			block("engraved_plate_8", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_8 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_9 =
			block("engraved_plate_9", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_9 = s);
	public static final BlockDef<EngravedPlateBlock> BROKEN_ENGRAVED_PLATE_W =
			block("broken_engraved_plate_w", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.BROKEN_ENGRAVED_PLATE_W = s);
	public static final BlockDef<EngravedPlateBlock> BROKEN_ENGRAVED_PLATE_K =
			block("broken_engraved_plate_k", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.BROKEN_ENGRAVED_PLATE_K = s);
	public static final BlockDef<EngravedPlateBlock> BROKEN_ENGRAVED_PLATE_P =
			block("broken_engraved_plate_p", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.BROKEN_ENGRAVED_PLATE_P = s);
	public static final BlockDef<EngravedPlateBlock> BROKEN_ENGRAVED_PLATE_B =
			block("broken_engraved_plate_b", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.BROKEN_ENGRAVED_PLATE_B = s);
	public static final BlockDef<EngravedPlateBlock> BROKEN_ENGRAVED_PLATE_D =
			block("broken_engraved_plate_d", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.BROKEN_ENGRAVED_PLATE_D = s);
	public static final BlockDef<EngravedPlateBlock> BROKEN_ENGRAVED_PLATE_R =
			block("broken_engraved_plate_r", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.BROKEN_ENGRAVED_PLATE_R = s);
	public static final BlockDef<EngravedPlateBlock> BROKEN_ENGRAVED_PLATE_M =
			block("broken_engraved_plate_m", EngravedPlateBlock::new,
					machine(p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.BROKEN_ENGRAVED_PLATE_M = s);

	/** This domain's blocks, in declaration order — collected since {@code beginBlocks()} above. */
	private static final List<BlockDef<?>> BLOCKS = endBlocks();

	private static final List<ItemDef> ITEMS = List.of(
			blockItem("mob_repeller", s -> ModContent.MOB_REPELLER_ITEM = s),
			blockItem("mob_repeller_mv", s -> ModContent.MOB_REPELLER_MV_ITEM = s),
			blockItem("mob_repeller_hv", s -> ModContent.MOB_REPELLER_HV_ITEM = s),
			blockItem("tempered_iron_block", s -> ModContent.TEMPERED_IRON_BLOCK_ITEM = s),
			blockItem("silver_plate_block", s -> ModContent.SILVER_PLATE_BLOCK_ITEM = s),
			blockItem("tempered_iron_plate_block", s -> ModContent.TEMPERED_IRON_PLATE_BLOCK_ITEM = s),
			// Enriched Uranium Torch (MOD-085): a StandingAndWallBlockItem (like vanilla Items.TORCH) so using it
			// on a wall places the wall variant and on the floor the standing variant. The wall block has no item
			// of its own — this item maps to both blocks (StandingAndWallBlockItem#registerBlocks).
			blockItem("enriched_uranium_torch",
					p -> new StandingAndWallBlockItem(registeredBlock("enriched_uranium_torch"),
					registeredBlock("enriched_uranium_wall_torch"), Direction.DOWN,
					p.useBlockDescriptionPrefix()), s -> ModContent.ENRICHED_URANIUM_TORCH_ITEM = s),
			blockItem("engraved_plate_0", s -> ModContent.ENGRAVED_PLATE_0_ITEM = s),
			blockItem("engraved_plate_1", s -> ModContent.ENGRAVED_PLATE_1_ITEM = s),
			blockItem("engraved_plate_2", s -> ModContent.ENGRAVED_PLATE_2_ITEM = s),
			blockItem("engraved_plate_3", s -> ModContent.ENGRAVED_PLATE_3_ITEM = s),
			blockItem("engraved_plate_4", s -> ModContent.ENGRAVED_PLATE_4_ITEM = s),
			blockItem("engraved_plate_5", s -> ModContent.ENGRAVED_PLATE_5_ITEM = s),
			blockItem("engraved_plate_6", s -> ModContent.ENGRAVED_PLATE_6_ITEM = s),
			blockItem("engraved_plate_7", s -> ModContent.ENGRAVED_PLATE_7_ITEM = s),
			blockItem("engraved_plate_8", s -> ModContent.ENGRAVED_PLATE_8_ITEM = s),
			blockItem("engraved_plate_9", s -> ModContent.ENGRAVED_PLATE_9_ITEM = s),
			blockItem("broken_engraved_plate_w", s -> ModContent.BROKEN_ENGRAVED_PLATE_W_ITEM = s),
			blockItem("broken_engraved_plate_k", s -> ModContent.BROKEN_ENGRAVED_PLATE_K_ITEM = s),
			blockItem("broken_engraved_plate_p", s -> ModContent.BROKEN_ENGRAVED_PLATE_P_ITEM = s),
			blockItem("broken_engraved_plate_b", s -> ModContent.BROKEN_ENGRAVED_PLATE_B_ITEM = s),
			blockItem("broken_engraved_plate_d", s -> ModContent.BROKEN_ENGRAVED_PLATE_D_ITEM = s),
			blockItem("broken_engraved_plate_r", s -> ModContent.BROKEN_ENGRAVED_PLATE_R_ITEM = s),
			blockItem("broken_engraved_plate_m", s -> ModContent.BROKEN_ENGRAVED_PLATE_M_ITEM = s));

	private static final List<BlockEntityDef<?>> BLOCK_ENTITIES = List.of(
			blockEntity("mob_repeller", MobRepellerBlockEntity.class, MobRepellerBlockEntity::new,
					s -> ModContent.MOB_REPELLER_BE = s, MOB_REPELLER),
			blockEntity("mob_repeller_mv", MobRepellerMvBlockEntity.class, MobRepellerMvBlockEntity::new,
					s -> ModContent.MOB_REPELLER_MV_BE = s, MOB_REPELLER_MV),
			blockEntity("mob_repeller_hv", MobRepellerHvBlockEntity.class, MobRepellerHvBlockEntity::new,
					s -> ModContent.MOB_REPELLER_HV_BE = s, MOB_REPELLER_HV));

	private static final List<MenuDef<?>> MENUS = List.of(
			// MOD-278 — the guard field, one menu per tier (same class, tier-specific client factory:
			// a menu type must map to exactly one block for stillValid, and the tiers are three blocks).
			menu("mob_repeller", MobRepellerMenu::new, s -> ModContent.MOB_REPELLER_MENU = s),
			menu("mob_repeller_mv", MobRepellerMvMenu::new, s -> ModContent.MOB_REPELLER_MV_MENU = s),
			menu("mob_repeller_hv", MobRepellerHvMenu::new, s -> ModContent.MOB_REPELLER_HV_MENU = s));

	/** Everything this domain declares; {@link dev.alaindustrial.registry.ContentManifest#DOMAINS} joins it in. */
	public static final Domain DOMAIN = new Domain("Decor", BLOCKS, ITEMS, BLOCK_ENTITIES, MENUS);

	// ---- Creative tab sections (MOD-711, batch 4; ADR-041). CreativeTabContent calls them in the tab's
	// reading order. A section holds the run of the tab that coding.md §1 files under this domain, so it may
	// show a neighbour's handle where the tab has always grouped it here.

	/**
	 * Blocks built out of metal: the tempered iron block, the plate blocks and the Industrial Workbench, which is
	 * a decorative building block (MOD-062 villager POI). The same run opens vanilla Building Blocks.
	 */
	public static void metalBlocks(Sink out) {
		show(out, ModContent.TEMPERED_IRON_BLOCK_ITEM);
		plateBlocks(out);
		show(out, ModContent.INDUSTRIAL_WORKBENCH_ITEM);
	}

	/** Blocks made from plates (MOD-225): the machine casing and two decorative panels. */
	public static void plateBlocks(Sink out) {
		show(out, ModContent.MACHINE_CASING_ITEM);
		show(out, ModContent.ADVANCED_MACHINE_CASING_ITEM);
		show(out, ModContent.SILVER_PLATE_BLOCK_ITEM);
		show(out, ModContent.TEMPERED_IRON_PLATE_BLOCK_ITEM);
	}

	/**
	 * Mob Repeller family (MOD-278): the crafted LV block followed by the two evolved tiers, which have no
	 * recipe of their own — same listing shape as the evolved wind mills and solar panels. Shown in the mod's
	 * own tab and in vanilla Functional Blocks: those are different tabs, and only what {@code main} calls
	 * reaches the mod's own one.
	 */
	public static void mobRepellers(Sink out) {
		show(out, ModContent.MOB_REPELLER_ITEM);
		show(out, ModContent.MOB_REPELLER_MV_ITEM);
		show(out, ModContent.MOB_REPELLER_HV_ITEM);
	}

	/** The Enriched Uranium Torch (MOD-085) — a sprite among cubes, so it closes the block run (MOD-574). */
	public static void torch(Sink out) {
		show(out, ModContent.ENRICHED_URANIUM_TORCH_ITEM);
	}

	/**
	 * The lab plaque plates (MOD-513): ten engraved digits and seven broken prefix letters. Decorative
	 * only — no recipe, found in abandoned labs. Called from {@code CreativeTabContent.main} after the two
	 * bands, so they close the mod's tab, and from {@code CreativeTabContent.buildingBlocks} for vanilla's
	 * Building Blocks.
	 */
	public static void labPlaque(Sink out) {
		show(out, ModContent.ENGRAVED_PLATE_0_ITEM);
		show(out, ModContent.ENGRAVED_PLATE_1_ITEM);
		show(out, ModContent.ENGRAVED_PLATE_2_ITEM);
		show(out, ModContent.ENGRAVED_PLATE_3_ITEM);
		show(out, ModContent.ENGRAVED_PLATE_4_ITEM);
		show(out, ModContent.ENGRAVED_PLATE_5_ITEM);
		show(out, ModContent.ENGRAVED_PLATE_6_ITEM);
		show(out, ModContent.ENGRAVED_PLATE_7_ITEM);
		show(out, ModContent.ENGRAVED_PLATE_8_ITEM);
		show(out, ModContent.ENGRAVED_PLATE_9_ITEM);
		show(out, ModContent.BROKEN_ENGRAVED_PLATE_W_ITEM);
		show(out, ModContent.BROKEN_ENGRAVED_PLATE_K_ITEM);
		show(out, ModContent.BROKEN_ENGRAVED_PLATE_P_ITEM);
		show(out, ModContent.BROKEN_ENGRAVED_PLATE_B_ITEM);
		show(out, ModContent.BROKEN_ENGRAVED_PLATE_D_ITEM);
		show(out, ModContent.BROKEN_ENGRAVED_PLATE_R_ITEM);
		show(out, ModContent.BROKEN_ENGRAVED_PLATE_M_ITEM);
	}
}
