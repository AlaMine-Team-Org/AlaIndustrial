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
import dev.alaindustrial.block.MetalStairBlock;
import dev.alaindustrial.block.MobRepellerBlock;
import dev.alaindustrial.block.MobRepellerHvBlock;
import dev.alaindustrial.block.MobRepellerMvBlock;
import dev.alaindustrial.block.IndustrialLightBlock;
import dev.alaindustrial.block.ReinforcedGlassBlock;
import dev.alaindustrial.block.TemperedIronBarsBlock;
import dev.alaindustrial.block.TemperedIronLadderBlock;
import dev.alaindustrial.block.TemperedIronTrapdoorBlock;
import dev.alaindustrial.block.entity.MobRepellerBlockEntity;
import dev.alaindustrial.block.entity.MobRepellerHvBlockEntity;
import dev.alaindustrial.block.entity.MobRepellerMvBlockEntity;
import dev.alaindustrial.compat.LineBlockProps;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.material.MapColor;

// size-justified: a declarative table (coding.md §10, under 600 lines per domain); the metal family of
// MOD-796 alone is 47 generated blocks, written by tools/gen_metal_blocks.py between its markers.
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
			machine(MapColor.TERRACOTTA_LIGHT_BLUE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.TEMPERED_IRON_BLOCK = s);
	public static final BlockDef<Block> SILVER_PLATE_BLOCK = block("silver_plate_block", Block::new,
			machine(p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)), s -> ModContent.SILVER_PLATE_BLOCK = s);
	public static final BlockDef<Block> TEMPERED_IRON_PLATE_BLOCK = block("tempered_iron_plate_block", Block::new,
			machine(MapColor.TERRACOTTA_LIGHT_BLUE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.TEMPERED_IRON_PLATE_BLOCK = s);
	// MOD-796 - storage blocks of the mod's ingots, their stairs, slabs and walls, the tempered iron fence.
	// BEGIN GENERATED metal blocks (tools/gen_metal_blocks.py)
	public static final BlockDef<Block> TIN_BLOCK = block("tin_block", Block::new,
			machine(MapColor.COLOR_LIGHT_GRAY, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.TIN_BLOCK = s);
	public static final BlockDef<MetalStairBlock> TIN_STAIRS = block("tin_stairs",
			p -> new MetalStairBlock(registeredBlock("tin_block").defaultBlockState(), p),
			machine(MapColor.COLOR_LIGHT_GRAY, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.TIN_STAIRS = s);
	public static final BlockDef<SlabBlock> TIN_SLAB = block("tin_slab", SlabBlock::new,
			machine(MapColor.COLOR_LIGHT_GRAY, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.TIN_SLAB = s);
	public static final BlockDef<WallBlock> TIN_WALL = block("tin_wall", WallBlock::new,
			machine(MapColor.COLOR_LIGHT_GRAY, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL).forceSolidOn()),
			s -> ModContent.TIN_WALL = s);
	public static final BlockDef<Block> SILVER_BLOCK = block("silver_block", Block::new,
			machine(MapColor.METAL, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.SILVER_BLOCK = s);
	public static final BlockDef<MetalStairBlock> SILVER_STAIRS = block("silver_stairs",
			p -> new MetalStairBlock(registeredBlock("silver_block").defaultBlockState(), p),
			machine(MapColor.METAL, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.SILVER_STAIRS = s);
	public static final BlockDef<SlabBlock> SILVER_SLAB = block("silver_slab", SlabBlock::new,
			machine(MapColor.METAL, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.SILVER_SLAB = s);
	public static final BlockDef<WallBlock> SILVER_WALL = block("silver_wall", WallBlock::new,
			machine(MapColor.METAL, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL).forceSolidOn()),
			s -> ModContent.SILVER_WALL = s);
	public static final BlockDef<Block> NICKEL_BLOCK = block("nickel_block", Block::new,
			machine(MapColor.SAND, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.NICKEL_BLOCK = s);
	public static final BlockDef<MetalStairBlock> NICKEL_STAIRS = block("nickel_stairs",
			p -> new MetalStairBlock(registeredBlock("nickel_block").defaultBlockState(), p),
			machine(MapColor.SAND, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.NICKEL_STAIRS = s);
	public static final BlockDef<SlabBlock> NICKEL_SLAB = block("nickel_slab", SlabBlock::new,
			machine(MapColor.SAND, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.NICKEL_SLAB = s);
	public static final BlockDef<WallBlock> NICKEL_WALL = block("nickel_wall", WallBlock::new,
			machine(MapColor.SAND, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL).forceSolidOn()),
			s -> ModContent.NICKEL_WALL = s);
	public static final BlockDef<Block> URANIUM_BLOCK = block("uranium_block", Block::new,
			machine(MapColor.COLOR_LIGHT_GREEN, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.URANIUM_BLOCK = s);
	public static final BlockDef<MetalStairBlock> URANIUM_STAIRS = block("uranium_stairs",
			p -> new MetalStairBlock(registeredBlock("uranium_block").defaultBlockState(), p),
			machine(MapColor.COLOR_LIGHT_GREEN, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.URANIUM_STAIRS = s);
	public static final BlockDef<SlabBlock> URANIUM_SLAB = block("uranium_slab", SlabBlock::new,
			machine(MapColor.COLOR_LIGHT_GREEN, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.URANIUM_SLAB = s);
	public static final BlockDef<WallBlock> URANIUM_WALL = block("uranium_wall", WallBlock::new,
			machine(MapColor.COLOR_LIGHT_GREEN, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL).forceSolidOn()),
			s -> ModContent.URANIUM_WALL = s);
	public static final BlockDef<Block> PALLADIUM_BLOCK = block("palladium_block", Block::new,
			machine(MapColor.DIAMOND, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.PALLADIUM_BLOCK = s);
	public static final BlockDef<MetalStairBlock> PALLADIUM_STAIRS = block("palladium_stairs",
			p -> new MetalStairBlock(registeredBlock("palladium_block").defaultBlockState(), p),
			machine(MapColor.DIAMOND, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.PALLADIUM_STAIRS = s);
	public static final BlockDef<SlabBlock> PALLADIUM_SLAB = block("palladium_slab", SlabBlock::new,
			machine(MapColor.DIAMOND, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.PALLADIUM_SLAB = s);
	public static final BlockDef<WallBlock> PALLADIUM_WALL = block("palladium_wall", WallBlock::new,
			machine(MapColor.DIAMOND, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL).forceSolidOn()),
			s -> ModContent.PALLADIUM_WALL = s);
	public static final BlockDef<Block> BRONZE_BLOCK = block("bronze_block", Block::new,
			machine(MapColor.COLOR_ORANGE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.BRONZE_BLOCK = s);
	public static final BlockDef<MetalStairBlock> BRONZE_STAIRS = block("bronze_stairs",
			p -> new MetalStairBlock(registeredBlock("bronze_block").defaultBlockState(), p),
			machine(MapColor.COLOR_ORANGE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.BRONZE_STAIRS = s);
	public static final BlockDef<SlabBlock> BRONZE_SLAB = block("bronze_slab", SlabBlock::new,
			machine(MapColor.COLOR_ORANGE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.BRONZE_SLAB = s);
	public static final BlockDef<WallBlock> BRONZE_WALL = block("bronze_wall", WallBlock::new,
			machine(MapColor.COLOR_ORANGE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL).forceSolidOn()),
			s -> ModContent.BRONZE_WALL = s);
	public static final BlockDef<Block> INVAR_BLOCK = block("invar_block", Block::new,
			machine(MapColor.STONE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.INVAR_BLOCK = s);
	public static final BlockDef<MetalStairBlock> INVAR_STAIRS = block("invar_stairs",
			p -> new MetalStairBlock(registeredBlock("invar_block").defaultBlockState(), p),
			machine(MapColor.STONE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.INVAR_STAIRS = s);
	public static final BlockDef<SlabBlock> INVAR_SLAB = block("invar_slab", SlabBlock::new,
			machine(MapColor.STONE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.INVAR_SLAB = s);
	public static final BlockDef<WallBlock> INVAR_WALL = block("invar_wall", WallBlock::new,
			machine(MapColor.STONE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL).forceSolidOn()),
			s -> ModContent.INVAR_WALL = s);
	public static final BlockDef<Block> CUPRONICKEL_BLOCK = block("cupronickel_block", Block::new,
			machine(MapColor.TERRACOTTA_PINK, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.CUPRONICKEL_BLOCK = s);
	public static final BlockDef<MetalStairBlock> CUPRONICKEL_STAIRS = block("cupronickel_stairs",
			p -> new MetalStairBlock(registeredBlock("cupronickel_block").defaultBlockState(), p),
			machine(MapColor.TERRACOTTA_PINK, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.CUPRONICKEL_STAIRS = s);
	public static final BlockDef<SlabBlock> CUPRONICKEL_SLAB = block("cupronickel_slab", SlabBlock::new,
			machine(MapColor.TERRACOTTA_PINK, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.CUPRONICKEL_SLAB = s);
	public static final BlockDef<WallBlock> CUPRONICKEL_WALL = block("cupronickel_wall", WallBlock::new,
			machine(MapColor.TERRACOTTA_PINK, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL).forceSolidOn()),
			s -> ModContent.CUPRONICKEL_WALL = s);
	public static final BlockDef<Block> ELECTRUM_BLOCK = block("electrum_block", Block::new,
			machine(MapColor.GOLD, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.ELECTRUM_BLOCK = s);
	public static final BlockDef<MetalStairBlock> ELECTRUM_STAIRS = block("electrum_stairs",
			p -> new MetalStairBlock(registeredBlock("electrum_block").defaultBlockState(), p),
			machine(MapColor.GOLD, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.ELECTRUM_STAIRS = s);
	public static final BlockDef<SlabBlock> ELECTRUM_SLAB = block("electrum_slab", SlabBlock::new,
			machine(MapColor.GOLD, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.ELECTRUM_SLAB = s);
	public static final BlockDef<WallBlock> ELECTRUM_WALL = block("electrum_wall", WallBlock::new,
			machine(MapColor.GOLD, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL).forceSolidOn()),
			s -> ModContent.ELECTRUM_WALL = s);
	public static final BlockDef<Block> NETHERITE_ALLOY_BLOCK = block("netherite_alloy_block", Block::new,
			machine(MapColor.COLOR_BROWN, p -> p.strength(50.0f, 1200.0f).sound(SoundType.NETHERITE_BLOCK)),
			s -> ModContent.NETHERITE_ALLOY_BLOCK = s);
	public static final BlockDef<MetalStairBlock> NETHERITE_ALLOY_STAIRS = block("netherite_alloy_stairs",
			p -> new MetalStairBlock(registeredBlock("netherite_alloy_block").defaultBlockState(), p),
			machine(MapColor.COLOR_BROWN, p -> p.strength(50.0f, 1200.0f).sound(SoundType.NETHERITE_BLOCK)),
			s -> ModContent.NETHERITE_ALLOY_STAIRS = s);
	public static final BlockDef<SlabBlock> NETHERITE_ALLOY_SLAB = block("netherite_alloy_slab", SlabBlock::new,
			machine(MapColor.COLOR_BROWN, p -> p.strength(50.0f, 1200.0f).sound(SoundType.NETHERITE_BLOCK)),
			s -> ModContent.NETHERITE_ALLOY_SLAB = s);
	public static final BlockDef<WallBlock> NETHERITE_ALLOY_WALL = block("netherite_alloy_wall", WallBlock::new,
			machine(MapColor.COLOR_BROWN,
					p -> p.strength(50.0f, 1200.0f).sound(SoundType.NETHERITE_BLOCK).forceSolidOn()),
			s -> ModContent.NETHERITE_ALLOY_WALL = s);
	public static final BlockDef<Block> SHIELDING_ALLOY_BLOCK = block("shielding_alloy_block", Block::new,
			machine(MapColor.DEEPSLATE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.SHIELDING_ALLOY_BLOCK = s);
	public static final BlockDef<MetalStairBlock> SHIELDING_ALLOY_STAIRS = block("shielding_alloy_stairs",
			p -> new MetalStairBlock(registeredBlock("shielding_alloy_block").defaultBlockState(), p),
			machine(MapColor.DEEPSLATE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.SHIELDING_ALLOY_STAIRS = s);
	public static final BlockDef<SlabBlock> SHIELDING_ALLOY_SLAB = block("shielding_alloy_slab", SlabBlock::new,
			machine(MapColor.DEEPSLATE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.SHIELDING_ALLOY_SLAB = s);
	public static final BlockDef<WallBlock> SHIELDING_ALLOY_WALL = block("shielding_alloy_wall", WallBlock::new,
			machine(MapColor.DEEPSLATE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL).forceSolidOn()),
			s -> ModContent.SHIELDING_ALLOY_WALL = s);
	public static final BlockDef<MetalStairBlock> TEMPERED_IRON_STAIRS = block("tempered_iron_stairs",
			p -> new MetalStairBlock(registeredBlock("tempered_iron_block").defaultBlockState(), p),
			machine(MapColor.TERRACOTTA_LIGHT_BLUE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.TEMPERED_IRON_STAIRS = s);
	public static final BlockDef<SlabBlock> TEMPERED_IRON_SLAB = block("tempered_iron_slab", SlabBlock::new,
			machine(MapColor.TERRACOTTA_LIGHT_BLUE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.TEMPERED_IRON_SLAB = s);
	public static final BlockDef<WallBlock> TEMPERED_IRON_WALL = block("tempered_iron_wall", WallBlock::new,
			machine(MapColor.TERRACOTTA_LIGHT_BLUE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL).forceSolidOn()),
			s -> ModContent.TEMPERED_IRON_WALL = s);
	public static final BlockDef<FenceBlock> TEMPERED_IRON_FENCE = block("tempered_iron_fence", FenceBlock::new,
			machine(MapColor.TERRACOTTA_LIGHT_BLUE, p -> p.strength(5.0f, 6.0f).sound(SoundType.METAL)),
			s -> ModContent.TEMPERED_IRON_FENCE = s);
	// END GENERATED metal blocks
	// MOD-795 — reinforced glass, the panel lamp and the tempered iron twins of vanilla bars, ladder, trapdoor
	// and chain. Vanilla behaviour throughout; tempered iron hardness, a pickaxe for the drop.
	public static final BlockDef<ReinforcedGlassBlock> REINFORCED_GLASS = block("reinforced_glass",
			ReinforcedGlassBlock::new,
			// Ten times vanilla glass to break; blast resistance of the reactor shell, so a creeper or TNT holds.
			machine(MapColor.NONE, p -> LineBlockProps.seeThrough(p.strength(3.0f, 30.0f).sound(SoundType.GLASS)
					.noOcclusion())),
			s -> ModContent.REINFORCED_GLASS = s);
	public static final BlockDef<IndustrialLightBlock> INDUSTRIAL_LIGHT = block("industrial_light",
			IndustrialLightBlock::new,
			// Always the full light level: no power, no switch.
			machine(MapColor.NONE, p -> p.strength(1.5f, 6.0f).sound(SoundType.GLASS).lightLevel(state -> 15)
					.noOcclusion()), s -> ModContent.INDUSTRIAL_LIGHT = s);
	public static final BlockDef<TemperedIronBarsBlock> TEMPERED_IRON_BARS = block("tempered_iron_bars",
			TemperedIronBarsBlock::new,
			machine(MapColor.TERRACOTTA_LIGHT_BLUE, p -> p.strength(5.0f, 6.0f).sound(SoundType.IRON).noOcclusion()),
			s -> ModContent.TEMPERED_IRON_BARS = s);
	public static final BlockDef<TemperedIronLadderBlock> TEMPERED_IRON_LADDER = block("tempered_iron_ladder",
			TemperedIronLadderBlock::new,
			// A piston pops it like the vanilla ladder.
			machine(MapColor.TERRACOTTA_LIGHT_BLUE, p -> LineBlockProps.popsOnPush(p).strength(3.0f, 6.0f)
					.sound(SoundType.METAL).noOcclusion()), s -> ModContent.TEMPERED_IRON_LADDER = s);
	public static final BlockDef<TemperedIronTrapdoorBlock> TEMPERED_IRON_TRAPDOOR = block("tempered_iron_trapdoor",
			TemperedIronTrapdoorBlock::new,
			machine(MapColor.TERRACOTTA_LIGHT_BLUE, p -> p.strength(5.0f, 6.0f).noOcclusion()
					.isValidSpawn((state, level, pos, type) -> false)), s -> ModContent.TEMPERED_IRON_TRAPDOOR = s);
	public static final BlockDef<ChainBlock> TEMPERED_IRON_CHAIN = block("tempered_iron_chain", ChainBlock::new,
			machine(MapColor.NONE, p -> p.strength(5.0f, 6.0f).sound(SoundType.CHAIN).noOcclusion().forceSolidOn()),
			s -> ModContent.TEMPERED_IRON_CHAIN = s);
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
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_0 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_1 =
			block("engraved_plate_1", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_1 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_2 =
			block("engraved_plate_2", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_2 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_3 =
			block("engraved_plate_3", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_3 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_4 =
			block("engraved_plate_4", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_4 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_5 =
			block("engraved_plate_5", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_5 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_6 =
			block("engraved_plate_6", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_6 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_7 =
			block("engraved_plate_7", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_7 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_8 =
			block("engraved_plate_8", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_8 = s);
	public static final BlockDef<EngravedPlateBlock> ENGRAVED_PLATE_9 =
			block("engraved_plate_9", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.ENGRAVED_PLATE_9 = s);
	public static final BlockDef<EngravedPlateBlock> BROKEN_ENGRAVED_PLATE_W =
			block("broken_engraved_plate_w", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.BROKEN_ENGRAVED_PLATE_W = s);
	public static final BlockDef<EngravedPlateBlock> BROKEN_ENGRAVED_PLATE_K =
			block("broken_engraved_plate_k", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.BROKEN_ENGRAVED_PLATE_K = s);
	public static final BlockDef<EngravedPlateBlock> BROKEN_ENGRAVED_PLATE_P =
			block("broken_engraved_plate_p", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.BROKEN_ENGRAVED_PLATE_P = s);
	public static final BlockDef<EngravedPlateBlock> BROKEN_ENGRAVED_PLATE_B =
			block("broken_engraved_plate_b", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.BROKEN_ENGRAVED_PLATE_B = s);
	public static final BlockDef<EngravedPlateBlock> BROKEN_ENGRAVED_PLATE_D =
			block("broken_engraved_plate_d", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.BROKEN_ENGRAVED_PLATE_D = s);
	public static final BlockDef<EngravedPlateBlock> BROKEN_ENGRAVED_PLATE_R =
			block("broken_engraved_plate_r", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
					s -> ModContent.BROKEN_ENGRAVED_PLATE_R = s);
	public static final BlockDef<EngravedPlateBlock> BROKEN_ENGRAVED_PLATE_M =
			block("broken_engraved_plate_m", EngravedPlateBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.5f, 6.0f).sound(SoundType.POLISHED_DEEPSLATE)),
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
			// MOD-796 - storage blocks of the mod's ingots, their stairs, slabs and walls, the tempered iron fence.
			// BEGIN GENERATED metal blocks (tools/gen_metal_blocks.py)
			blockItem("tin_block", s -> ModContent.TIN_BLOCK_ITEM = s),
			blockItem("tin_stairs", s -> ModContent.TIN_STAIRS_ITEM = s),
			blockItem("tin_slab", s -> ModContent.TIN_SLAB_ITEM = s),
			blockItem("tin_wall", s -> ModContent.TIN_WALL_ITEM = s),
			blockItem("silver_block", s -> ModContent.SILVER_BLOCK_ITEM = s),
			blockItem("silver_stairs", s -> ModContent.SILVER_STAIRS_ITEM = s),
			blockItem("silver_slab", s -> ModContent.SILVER_SLAB_ITEM = s),
			blockItem("silver_wall", s -> ModContent.SILVER_WALL_ITEM = s),
			blockItem("nickel_block", s -> ModContent.NICKEL_BLOCK_ITEM = s),
			blockItem("nickel_stairs", s -> ModContent.NICKEL_STAIRS_ITEM = s),
			blockItem("nickel_slab", s -> ModContent.NICKEL_SLAB_ITEM = s),
			blockItem("nickel_wall", s -> ModContent.NICKEL_WALL_ITEM = s),
			blockItem("uranium_block", s -> ModContent.URANIUM_BLOCK_ITEM = s),
			blockItem("uranium_stairs", s -> ModContent.URANIUM_STAIRS_ITEM = s),
			blockItem("uranium_slab", s -> ModContent.URANIUM_SLAB_ITEM = s),
			blockItem("uranium_wall", s -> ModContent.URANIUM_WALL_ITEM = s),
			blockItem("palladium_block", s -> ModContent.PALLADIUM_BLOCK_ITEM = s),
			blockItem("palladium_stairs", s -> ModContent.PALLADIUM_STAIRS_ITEM = s),
			blockItem("palladium_slab", s -> ModContent.PALLADIUM_SLAB_ITEM = s),
			blockItem("palladium_wall", s -> ModContent.PALLADIUM_WALL_ITEM = s),
			blockItem("bronze_block", s -> ModContent.BRONZE_BLOCK_ITEM = s),
			blockItem("bronze_stairs", s -> ModContent.BRONZE_STAIRS_ITEM = s),
			blockItem("bronze_slab", s -> ModContent.BRONZE_SLAB_ITEM = s),
			blockItem("bronze_wall", s -> ModContent.BRONZE_WALL_ITEM = s),
			blockItem("invar_block", s -> ModContent.INVAR_BLOCK_ITEM = s),
			blockItem("invar_stairs", s -> ModContent.INVAR_STAIRS_ITEM = s),
			blockItem("invar_slab", s -> ModContent.INVAR_SLAB_ITEM = s),
			blockItem("invar_wall", s -> ModContent.INVAR_WALL_ITEM = s),
			blockItem("cupronickel_block", s -> ModContent.CUPRONICKEL_BLOCK_ITEM = s),
			blockItem("cupronickel_stairs", s -> ModContent.CUPRONICKEL_STAIRS_ITEM = s),
			blockItem("cupronickel_slab", s -> ModContent.CUPRONICKEL_SLAB_ITEM = s),
			blockItem("cupronickel_wall", s -> ModContent.CUPRONICKEL_WALL_ITEM = s),
			blockItem("electrum_block", s -> ModContent.ELECTRUM_BLOCK_ITEM = s),
			blockItem("electrum_stairs", s -> ModContent.ELECTRUM_STAIRS_ITEM = s),
			blockItem("electrum_slab", s -> ModContent.ELECTRUM_SLAB_ITEM = s),
			blockItem("electrum_wall", s -> ModContent.ELECTRUM_WALL_ITEM = s),
			blockItem("netherite_alloy_block", "netherite_alloy_block", Item.Properties::fireResistant,
					s -> ModContent.NETHERITE_ALLOY_BLOCK_ITEM = s),
			blockItem("netherite_alloy_stairs", "netherite_alloy_stairs", Item.Properties::fireResistant,
					s -> ModContent.NETHERITE_ALLOY_STAIRS_ITEM = s),
			blockItem("netherite_alloy_slab", "netherite_alloy_slab", Item.Properties::fireResistant,
					s -> ModContent.NETHERITE_ALLOY_SLAB_ITEM = s),
			blockItem("netherite_alloy_wall", "netherite_alloy_wall", Item.Properties::fireResistant,
					s -> ModContent.NETHERITE_ALLOY_WALL_ITEM = s),
			blockItem("shielding_alloy_block", s -> ModContent.SHIELDING_ALLOY_BLOCK_ITEM = s),
			blockItem("shielding_alloy_stairs", s -> ModContent.SHIELDING_ALLOY_STAIRS_ITEM = s),
			blockItem("shielding_alloy_slab", s -> ModContent.SHIELDING_ALLOY_SLAB_ITEM = s),
			blockItem("shielding_alloy_wall", s -> ModContent.SHIELDING_ALLOY_WALL_ITEM = s),
			blockItem("tempered_iron_stairs", s -> ModContent.TEMPERED_IRON_STAIRS_ITEM = s),
			blockItem("tempered_iron_slab", s -> ModContent.TEMPERED_IRON_SLAB_ITEM = s),
			blockItem("tempered_iron_wall", s -> ModContent.TEMPERED_IRON_WALL_ITEM = s),
			blockItem("tempered_iron_fence", s -> ModContent.TEMPERED_IRON_FENCE_ITEM = s),
			// END GENERATED metal blocks
			blockItem("reinforced_glass", s -> ModContent.REINFORCED_GLASS_ITEM = s),
			blockItem("industrial_light", s -> ModContent.INDUSTRIAL_LIGHT_ITEM = s),
			blockItem("tempered_iron_bars", s -> ModContent.TEMPERED_IRON_BARS_ITEM = s),
			blockItem("tempered_iron_ladder", s -> ModContent.TEMPERED_IRON_LADDER_ITEM = s),
			blockItem("tempered_iron_trapdoor", s -> ModContent.TEMPERED_IRON_TRAPDOOR_ITEM = s),
			blockItem("tempered_iron_chain", s -> ModContent.TEMPERED_IRON_CHAIN_ITEM = s),
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

	/**
	 * The metal family (MOD-796): the storage blocks of the mod's ingots, then their stairs, slabs and walls
	 * and the tempered iron fence. Building material rather than machinery, so the mod's tab keeps it at the
	 * very bottom, after both bands and the lab plaque (owner decision 2026-10-09); cubes before shapes.
	 */
	public static void metalFamily(Sink out) {
		// BEGIN GENERATED metal blocks (tools/gen_metal_blocks.py)
		show(out, ModContent.TIN_BLOCK_ITEM);
		show(out, ModContent.SILVER_BLOCK_ITEM);
		show(out, ModContent.NICKEL_BLOCK_ITEM);
		show(out, ModContent.URANIUM_BLOCK_ITEM);
		show(out, ModContent.PALLADIUM_BLOCK_ITEM);
		show(out, ModContent.BRONZE_BLOCK_ITEM);
		show(out, ModContent.INVAR_BLOCK_ITEM);
		show(out, ModContent.CUPRONICKEL_BLOCK_ITEM);
		show(out, ModContent.ELECTRUM_BLOCK_ITEM);
		show(out, ModContent.NETHERITE_ALLOY_BLOCK_ITEM);
		show(out, ModContent.SHIELDING_ALLOY_BLOCK_ITEM);
		// END GENERATED metal blocks
		// BEGIN GENERATED metal blocks (tools/gen_metal_blocks.py)
		show(out, ModContent.TIN_STAIRS_ITEM);
		show(out, ModContent.TIN_SLAB_ITEM);
		show(out, ModContent.TIN_WALL_ITEM);
		show(out, ModContent.SILVER_STAIRS_ITEM);
		show(out, ModContent.SILVER_SLAB_ITEM);
		show(out, ModContent.SILVER_WALL_ITEM);
		show(out, ModContent.NICKEL_STAIRS_ITEM);
		show(out, ModContent.NICKEL_SLAB_ITEM);
		show(out, ModContent.NICKEL_WALL_ITEM);
		show(out, ModContent.URANIUM_STAIRS_ITEM);
		show(out, ModContent.URANIUM_SLAB_ITEM);
		show(out, ModContent.URANIUM_WALL_ITEM);
		show(out, ModContent.PALLADIUM_STAIRS_ITEM);
		show(out, ModContent.PALLADIUM_SLAB_ITEM);
		show(out, ModContent.PALLADIUM_WALL_ITEM);
		show(out, ModContent.BRONZE_STAIRS_ITEM);
		show(out, ModContent.BRONZE_SLAB_ITEM);
		show(out, ModContent.BRONZE_WALL_ITEM);
		show(out, ModContent.INVAR_STAIRS_ITEM);
		show(out, ModContent.INVAR_SLAB_ITEM);
		show(out, ModContent.INVAR_WALL_ITEM);
		show(out, ModContent.CUPRONICKEL_STAIRS_ITEM);
		show(out, ModContent.CUPRONICKEL_SLAB_ITEM);
		show(out, ModContent.CUPRONICKEL_WALL_ITEM);
		show(out, ModContent.ELECTRUM_STAIRS_ITEM);
		show(out, ModContent.ELECTRUM_SLAB_ITEM);
		show(out, ModContent.ELECTRUM_WALL_ITEM);
		show(out, ModContent.NETHERITE_ALLOY_STAIRS_ITEM);
		show(out, ModContent.NETHERITE_ALLOY_SLAB_ITEM);
		show(out, ModContent.NETHERITE_ALLOY_WALL_ITEM);
		show(out, ModContent.SHIELDING_ALLOY_STAIRS_ITEM);
		show(out, ModContent.SHIELDING_ALLOY_SLAB_ITEM);
		show(out, ModContent.SHIELDING_ALLOY_WALL_ITEM);
		show(out, ModContent.TEMPERED_IRON_STAIRS_ITEM);
		show(out, ModContent.TEMPERED_IRON_SLAB_ITEM);
		show(out, ModContent.TEMPERED_IRON_WALL_ITEM);
		show(out, ModContent.TEMPERED_IRON_FENCE_ITEM);
		// END GENERATED metal blocks
		fittings(out);
	}

	/** MOD-795 — reinforced glass, the panel lamp, and tempered iron bars, ladder, trapdoor and chain. */
	public static void fittings(Sink out) {
		show(out, ModContent.REINFORCED_GLASS_ITEM);
		show(out, ModContent.INDUSTRIAL_LIGHT_ITEM);
		show(out, ModContent.TEMPERED_IRON_BARS_ITEM);
		show(out, ModContent.TEMPERED_IRON_LADDER_ITEM);
		show(out, ModContent.TEMPERED_IRON_TRAPDOOR_ITEM);
		show(out, ModContent.TEMPERED_IRON_CHAIN_ITEM);
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
