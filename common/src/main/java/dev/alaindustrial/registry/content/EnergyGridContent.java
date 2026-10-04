package dev.alaindustrial.registry.content;

import static dev.alaindustrial.registry.content.ContentDeclarations.beginBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.block;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockEntity;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockItem;
import static dev.alaindustrial.registry.content.ContentDeclarations.endBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.hint;
import static dev.alaindustrial.registry.content.ContentDeclarations.machine;
import static dev.alaindustrial.registry.content.TabEntries.show;

import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.registry.ContentManifest.BlockDef;
import dev.alaindustrial.registry.ContentManifest.BlockEntityDef;
import dev.alaindustrial.registry.ContentManifest.Domain;
import dev.alaindustrial.registry.ContentManifest.ItemDef;
import dev.alaindustrial.registry.ContentManifest.MenuDef;
import dev.alaindustrial.registry.CreativeTabContent.Sink;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.world.level.block.SoundType;

/**
 * The EnergyGrid domain of the content manifest (MOD-711; coding.md §1, owner decision D5). Energy transfer: the
 * cable ladder, each conductor with its insulated form, and the breaker that is installed on a line.
 *
 * <p>Declared here and registered through {@link dev.alaindustrial.registry.ContentManifest}, which joins the
 * ten domains in the fixed order of {@code ContentManifest.DOMAINS}; both loaders replay the joined lists. A new
 * entry of this domain is written here and nowhere else: a block between {@code beginBlocks()} and
 * {@link #BLOCKS}, an item, block entity or menu at the end of its list. This file declares no other domain's
 * entries and reads nothing of {@code ContentManifest} but its records
 * ({@code ArchitectureRules.contentDomainsInitialiseOnTheirOwn}): its static initialiser runs on its own.
 */
public final class EnergyGridContent {
	private EnergyGridContent() {
	}

	static {
		beginBlocks();
	}

	// Cables (MOD-219 / MOD-259): each grade passes its CableType; rubber insulation keeps the
	// conductor's tier/cap/buffer and halves its attenuation.
	public static final BlockDef<CableBlock> COPPER_CABLE =
			block("copper_cable", p -> new CableBlock(CableType.COPPER, p),
					machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion()),
					s -> ModContent.COPPER_CABLE = s);
	public static final BlockDef<CableBlock> TIN_CABLE = block("tin_cable", p -> new CableBlock(CableType.TIN, p),
			machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion()), s -> ModContent.TIN_CABLE = s);
	public static final BlockDef<CableBlock> GOLD_CABLE = block("gold_cable", p -> new CableBlock(CableType.GOLD, p),
			machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion()), s -> ModContent.GOLD_CABLE = s);
	public static final BlockDef<CableBlock> ELECTRUM_CABLE =
			block("electrum_cable", p -> new CableBlock(CableType.ELECTRUM, p),
					machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.COPPER).noOcclusion()),
					s -> ModContent.ELECTRUM_CABLE = s);
	public static final BlockDef<CableBlock> INSULATED_COPPER_CABLE =
			block("insulated_copper_cable", p -> new CableBlock(CableType.INSULATED_COPPER, p),
					machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion()),
					s -> ModContent.INSULATED_COPPER_CABLE = s);
	public static final BlockDef<CableBlock> INSULATED_TIN_CABLE =
			block("insulated_tin_cable", p -> new CableBlock(CableType.INSULATED_TIN, p),
					machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion()),
					s -> ModContent.INSULATED_TIN_CABLE = s);
	public static final BlockDef<CableBlock> INSULATED_GOLD_CABLE =
			block("insulated_gold_cable", p -> new CableBlock(CableType.INSULATED_GOLD, p),
					machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion()),
					s -> ModContent.INSULATED_GOLD_CABLE = s);
	public static final BlockDef<CableBlock> INSULATED_ELECTRUM_CABLE =
			block("insulated_electrum_cable", p -> new CableBlock(CableType.INSULATED_ELECTRUM, p),
					machine(p -> p.strength(0.2f, 0.5f).sound(SoundType.WOOL).noOcclusion()),
					s -> ModContent.INSULATED_ELECTRUM_CABLE = s);

	/** This domain's blocks, in declaration order — collected since {@code beginBlocks()} above. */
	private static final List<BlockDef<?>> BLOCKS = endBlocks();

	private static final List<ItemDef> ITEMS = List.of(
			// Cable breaker (MOD-276): clamps onto a laid cable and cuts the line for maintenance. A hint
			// item because the whole control scheme (install / throw / pry off) is gestures on the wire,
			// with no GUI anywhere to explain itself.
			// Cable breaker (MOD-276): clamps onto a laid cable to cut the line for maintenance.
			hint("cable_breaker", s -> ModContent.CABLE_BREAKER = s),
			blockItem("copper_cable", s -> ModContent.COPPER_CABLE_ITEM = s),
			blockItem("tin_cable", s -> ModContent.TIN_CABLE_ITEM = s),
			blockItem("gold_cable", s -> ModContent.GOLD_CABLE_ITEM = s),
			blockItem("electrum_cable", s -> ModContent.ELECTRUM_CABLE_ITEM = s),
			blockItem("insulated_copper_cable", s -> ModContent.INSULATED_COPPER_CABLE_ITEM = s),
			blockItem("insulated_tin_cable", s -> ModContent.INSULATED_TIN_CABLE_ITEM = s),
			blockItem("insulated_gold_cable", s -> ModContent.INSULATED_GOLD_CABLE_ITEM = s),
			blockItem("insulated_electrum_cable", s -> ModContent.INSULATED_ELECTRUM_CABLE_ITEM = s));

	private static final List<BlockEntityDef<?>> BLOCK_ENTITIES = List.of(
			blockEntity("copper_cable", CableBlockEntity.class, CableBlockEntity::new,
					s -> ModContent.COPPER_CABLE_BE = s, COPPER_CABLE, TIN_CABLE, GOLD_CABLE, ELECTRUM_CABLE,
					INSULATED_COPPER_CABLE, INSULATED_TIN_CABLE, INSULATED_GOLD_CABLE, INSULATED_ELECTRUM_CABLE));

	private static final List<MenuDef<?>> MENUS = List.of();

	/** Everything this domain declares; {@link dev.alaindustrial.registry.ContentManifest#DOMAINS} joins it in. */
	public static final Domain DOMAIN = new Domain("EnergyGrid", BLOCKS, ITEMS, BLOCK_ENTITIES, MENUS);

	// ---- Creative tab sections (MOD-711, batch 4; ADR-041). CreativeTabContent calls them in the tab's
	// reading order. A section holds the run of the tab that coding.md §1 files under this domain, so it may
	// show a neighbour's handle where the tab has always grouped it here.

	/** 3 - the conductor ladder, each grade immediately followed by its insulated form. */
	public static void energyTransfer(Sink out) {
		show(out, ModContent.TIN_CABLE_ITEM);
		show(out, ModContent.INSULATED_TIN_CABLE_ITEM);
		show(out, ModContent.COPPER_CABLE_ITEM);
		show(out, ModContent.INSULATED_COPPER_CABLE_ITEM);
		show(out, ModContent.GOLD_CABLE_ITEM);
		show(out, ModContent.INSULATED_GOLD_CABLE_ITEM);
		show(out, ModContent.ELECTRUM_CABLE_ITEM);
		show(out, ModContent.INSULATED_ELECTRUM_CABLE_ITEM);
	}

	/**
	 * 3b - the accessory installed on a cable (MOD-276). The breaker is an ITEM, so it is its own section and
	 * not part of {@link #energyTransfer}: that group also feeds vanilla Functional Blocks, which takes blocks
	 * only.
	 */
	public static void cableAccessories(Sink out) {
		show(out, ModContent.CABLE_BREAKER);
	}
}
