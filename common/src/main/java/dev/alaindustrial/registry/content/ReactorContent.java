package dev.alaindustrial.registry.content;

import static dev.alaindustrial.registry.content.ContentDeclarations.beginBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.block;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockEntity;
import static dev.alaindustrial.registry.content.ContentDeclarations.blockItem;
import static dev.alaindustrial.registry.content.ContentDeclarations.endBlocks;
import static dev.alaindustrial.registry.content.ContentDeclarations.item;
import static dev.alaindustrial.registry.content.ContentDeclarations.machine;
import static dev.alaindustrial.registry.content.ContentDeclarations.menu;
import static dev.alaindustrial.registry.content.ContentDeclarations.plain;
import static dev.alaindustrial.registry.content.TabEntries.show;

import dev.alaindustrial.block.FuelRodAssemblyBlock;
import dev.alaindustrial.block.IrradiatedSoilBlock;
import dev.alaindustrial.block.ReactorButtonBlock;
import dev.alaindustrial.block.ReactorControllerBlock;
import dev.alaindustrial.block.ReactorDoorBlock;
import dev.alaindustrial.block.ReactorLampBlock;
import dev.alaindustrial.block.ReactorLeverBlock;
import dev.alaindustrial.block.ReactorOutletBlock;
import dev.alaindustrial.block.ReactorPortBlock;
import dev.alaindustrial.block.ReactorShellBlock;
import dev.alaindustrial.block.SteamNozzleBlock;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.block.entity.ReactorControllerBlockEntity;
import dev.alaindustrial.block.entity.ReactorDoorBlockEntity;
import dev.alaindustrial.block.entity.ReactorOutletBlockEntity;
import dev.alaindustrial.block.entity.ReactorPortBlockEntity;
import dev.alaindustrial.block.entity.SteamNozzleBlockEntity;
import dev.alaindustrial.compat.LineBlockProps;
import dev.alaindustrial.core.structure.FuelRodMath;
import dev.alaindustrial.menu.ReactorControllerMenu;
import dev.alaindustrial.registry.ContentManifest.BlockDef;
import dev.alaindustrial.registry.ContentManifest.BlockEntityDef;
import dev.alaindustrial.registry.ContentManifest.Domain;
import dev.alaindustrial.registry.ContentManifest.ItemDef;
import dev.alaindustrial.registry.ContentManifest.MenuDef;
import dev.alaindustrial.registry.CreativeTabContent.Sink;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/**
 * The Reactor domain of the content manifest (MOD-711; coding.md §1, owner decision D5). The nuclear line: the
 * reactor room and every block of it, the fuel rods and what they leave behind. The shielding suit is worn, so
 * it lives in ToolsAndGear.
 *
 * <p>Declared here and registered through {@link dev.alaindustrial.registry.ContentManifest}, which joins the
 * ten domains in the fixed order of {@code ContentManifest.DOMAINS}; both loaders replay the joined lists. A new
 * entry of this domain is written here and nowhere else: a block between {@code beginBlocks()} and
 * {@link #BLOCKS}, an item, block entity or menu at the end of its list. This file declares no other domain's
 * entries and reads nothing of {@code ContentManifest} but its records
 * ({@code ArchitectureRules.contentDomainsInitialiseOnTheirOwn}): its static initialiser runs on its own.
 */
public final class ReactorContent {
	private ReactorContent() {
	}

	static {
		beginBlocks();
	}

	// ── MOD-468, stage 1: the reactor room's shell. Four inert building blocks and one brain. ──
	/** Wall, floor and ceiling of a reactor room — the only material its shell may be built from. */
	public static final BlockDef<ReactorShellBlock> REACTOR_CASING = block("reactor_casing", ReactorShellBlock::new,
			// ── MOD-468, stage 1: the reactor room's shell. Tougher than a machine casing (5.0) and far
			// harder to blow up (30.0): the room is what stands between a meltdown and the world, so a
			// creeper must not be able to open it. Glass and door are the same material, hence the same
			// numbers — a window is not a weak point, it just costs the same palladium as a wall.
			machine(MapColor.COLOR_GRAY, p -> p.strength(5.0f, 30.0f).sound(SoundType.METAL)),
			s -> ModContent.REACTOR_CASING = s);
	/** A window that still counts as shell; capped by share, so a room cannot be all windows. */
	public static final BlockDef<ReactorShellBlock> REACTOR_GLASS = block("reactor_glass", ReactorShellBlock::new,
			machine(MapColor.NONE, p -> p.strength(5.0f, 30.0f).sound(SoundType.GLASS)
					.noOcclusion()), s -> ModContent.REACTOR_GLASS = s);
	/** Feedthrough: pipes and cables cross the shell here instead of breaking it (live in stage 3). */
	public static final BlockDef<ReactorPortBlock> REACTOR_PORT = block("reactor_port", ReactorPortBlock::new,
			machine(MapColor.COLOR_GRAY, p -> p.strength(5.0f, 30.0f).sound(SoundType.METAL)),
			s -> ModContent.REACTOR_PORT = s);
	/** The airlock — pulse-only, self-closing; a room without one cannot be entered and does not form. */
	public static final BlockDef<ReactorDoorBlock> REACTOR_DOOR = block("reactor_door", ReactorDoorBlock::new,
			machine(MapColor.COLOR_GRAY, p -> LineBlockProps.popsOnPush(p).strength(5.0f, 30.0f)
					.sound(SoundType.METAL).noOcclusion()), s -> ModContent.REACTOR_DOOR = s);
	/** The room's brain: scans the shell, reports what is wrong and where. */
	public static final BlockDef<ReactorControllerBlock> REACTOR_CONTROLLER =
			block("reactor_controller", ReactorControllerBlock::new,
					machine(MapColor.COLOR_GRAY, p -> p.strength(5.0f, 30.0f).sound(SoundType.METAL)),
					s -> ModContent.REACTOR_CONTROLLER = s);
	/** Shell block that lights the inside of the room — and only once the room is sealed. */
	public static final BlockDef<ReactorLampBlock> REACTOR_LAMP = block("reactor_lamp", ReactorLampBlock::new,
			// The lamp glows only while its shell passes the scan — light is the room's "done" signal.
			machine(MapColor.COLOR_GRAY, p -> p.strength(5.0f, 30.0f).sound(SoundType.METAL)
					.lightLevel(ReactorLampBlock::lightLevel)), s -> ModContent.REACTOR_LAMP = s);
	/** The way out: a shielded button that survives what the room is built to contain. */
	public static final BlockDef<ReactorButtonBlock> REACTOR_BUTTON = block("reactor_button", ReactorButtonBlock::new,
			// A button is not a wall: no tool requirement, no collision, and it must not resist an
			// explosion the way the shell does, or it would survive a blast that took the wall with it.
			p -> LineBlockProps.popsOnPush(p).strength(0.5f).sound(SoundType.METAL)
					.noCollision(), s -> ModContent.REACTOR_BUTTON = s);
	/** MOD-468 stage 2 — the fuel rack: stands on the floor, fills with rods, shows its level. */
	public static final BlockDef<FuelRodAssemblyBlock> FUEL_ROD_ASSEMBLY =
			block("fuel_rod_assembly", FuelRodAssemblyBlock::new,
					// A rack, not armour: it lives inside the shell, so it needs none of the shell's toughness.
					// noOcclusion because the casing is transparent and the rods inside have to be drawn.
					machine(MapColor.COLOR_GRAY, p -> p.strength(3.0f, 6.0f).sound(SoundType.METAL)
							.noOcclusion()), s -> ModContent.FUEL_ROD_ASSEMBLY = s);

	public static final BlockDef<ReactorOutletBlock> REACTOR_OUTLET = block("reactor_outlet", ReactorOutletBlock::new,
			machine(MapColor.COLOR_GRAY, p -> p.strength(5.0f, 30.0f).sound(SoundType.METAL)),
			s -> ModContent.REACTOR_OUTLET = s);
	/**
	 * MOD-471 — ground a reactor accident poisoned. No block item: it is left behind, never placed.
	 */
	public static final BlockDef<IrradiatedSoilBlock> IRRADIATED_SOIL =
			block("irradiated_soil", IrradiatedSoilBlock::new,
					// MOD-471 — fallout. Soft as the dirt it replaces and no tool requirement: the scar is meant
					// to be shovelled away by a player who would rather not wait for it to fade. randomTicks()
					// is load-bearing — without it the decay in IrradiatedSoilBlock would never run and the
					// contamination would be permanent.
					p -> p.strength(0.6f).mapColor(MapColor.TERRACOTTA_GRAY).sound(SoundType.GRAVEL).randomTicks(),
					s -> ModContent.IRRADIATED_SOIL = s);

	public static final BlockDef<SteamNozzleBlock> STEAM_NOZZLE = block("steam_nozzle", SteamNozzleBlock::new,
			// Bolted to the outside of the shell: the shell's toughness, none of its bulk.
			machine(p -> p.strength(4.0f, 20.0f).sound(SoundType.METAL)
					.noOcclusion()), s -> ModContent.STEAM_NOZZLE = s);
	/** The button's twin for a signal that stays on: scram switch, throttle, any held redstone. */
	public static final BlockDef<ReactorLeverBlock> REACTOR_LEVER = block("reactor_lever", ReactorLeverBlock::new,
			// The lever is the button's twin down to the numbers: vanilla's own lever is noCollision +
			// strength 0.5 + popped by a piston, and ours differs only in the sound family. It must not
			// out-live the wall it hangs on either.
			p -> LineBlockProps.popsOnPush(p).strength(0.5f).sound(SoundType.METAL)
					.noCollision(), s -> ModContent.REACTOR_LEVER = s);

	/** This domain's blocks, in declaration order — collected since {@code beginBlocks()} above. */
	private static final List<BlockDef<?>> BLOCKS = endBlocks();

	private static final List<ItemDef> ITEMS = List.of(
			// Uranium Fuel Rod (MOD-468 stage 4). Durability IS its remaining charge: the column wears the
			// rod down as the reactor draws on it, so a half-spent rod shows a half-empty bar in the hand
			// and can be pulled out and put back without losing what is left. Before this it was a plain
			// item and a rod taken out mid-burn was simply destroyed.
			item("uranium_fuel_rod", p -> new Item(p.durability(FuelRodMath.ROD_DURABILITY)),
					s -> ModContent.URANIUM_FUEL_ROD = s),
			// MOD-468 stage 4: the empty casing. Crafted 3x3, filled with one refined uranium, and
			// handed back by the column when its charge is spent — refuelling a reactor is topping up
			// casings you already own, not building rods from scratch every time.
			plain("empty_fuel_rod", s -> ModContent.EMPTY_FUEL_ROD = s),
			plain("depleted_uranium", s -> ModContent.DEPLETED_URANIUM = s),
			// MOD-468, stage 1 — block items for the reactor shell.
			blockItem("reactor_casing", s -> ModContent.REACTOR_CASING_ITEM = s),
			blockItem("irradiated_soil", s -> ModContent.IRRADIATED_SOIL_ITEM = s),
			blockItem("reactor_glass", s -> ModContent.REACTOR_GLASS_ITEM = s),
			blockItem("reactor_port", s -> ModContent.REACTOR_PORT_ITEM = s),
			blockItem("reactor_door", s -> ModContent.REACTOR_DOOR_ITEM = s),
			blockItem("reactor_controller", s -> ModContent.REACTOR_CONTROLLER_ITEM = s),
			blockItem("reactor_lamp", s -> ModContent.REACTOR_LAMP_ITEM = s),
			blockItem("steam_nozzle", s -> ModContent.STEAM_NOZZLE_ITEM = s),
			blockItem("reactor_outlet", s -> ModContent.REACTOR_OUTLET_ITEM = s),
			blockItem("reactor_button", s -> ModContent.REACTOR_BUTTON_ITEM = s),
			blockItem("reactor_lever", s -> ModContent.REACTOR_LEVER_ITEM = s),
			blockItem("fuel_rod_assembly", s -> ModContent.FUEL_ROD_ASSEMBLY_ITEM = s));

	private static final List<BlockEntityDef<?>> BLOCK_ENTITIES = List.of(
			blockEntity("reactor_controller", ReactorControllerBlockEntity.class,
					ReactorControllerBlockEntity::new, s -> ModContent.REACTOR_CONTROLLER_BE = s,
					REACTOR_CONTROLLER),
			blockEntity("fuel_rod_assembly", FuelRodAssemblyBlockEntity.class,
					FuelRodAssemblyBlockEntity::new, s -> ModContent.FUEL_ROD_ASSEMBLY_BE = s,
					FUEL_ROD_ASSEMBLY),
			blockEntity("reactor_port", ReactorPortBlockEntity.class,
					ReactorPortBlockEntity::new, s -> ModContent.REACTOR_PORT_BE = s,
					REACTOR_PORT),
			blockEntity("steam_nozzle", SteamNozzleBlockEntity.class,
					SteamNozzleBlockEntity::new, s -> ModContent.STEAM_NOZZLE_BE = s,
					STEAM_NOZZLE),
			blockEntity("reactor_outlet", ReactorOutletBlockEntity.class,
					ReactorOutletBlockEntity::new, s -> ModContent.REACTOR_OUTLET_BE = s,
					REACTOR_OUTLET),
			// MOD-493: the airlock's panel slides rather than swings, and a block state cannot hold a
			// position between two ticks. This block entity stores no game state at all — it is the
			// clock the client times that travel by.
			blockEntity("reactor_door", ReactorDoorBlockEntity.class,
					ReactorDoorBlockEntity::new, s -> ModContent.REACTOR_DOOR_BE = s,
					REACTOR_DOOR));

	private static final List<MenuDef<?>> MENUS = List.of(
			// MOD-468 — the reactor controller: a slotless diagnostic readout for the room multiblock.
			menu("reactor_controller", ReactorControllerMenu::new,
					s -> ModContent.REACTOR_CONTROLLER_MENU = s));

	/** Everything this domain declares; {@link dev.alaindustrial.registry.ContentManifest#DOMAINS} joins it in. */
	public static final Domain DOMAIN = new Domain("Reactor", BLOCKS, ITEMS, BLOCK_ENTITIES, MENUS);

	// ---- Creative tab sections (MOD-711, batch 4; ADR-041). CreativeTabContent calls them in the tab's
	// reading order. A section holds the run of the tab that coding.md §1 files under this domain, so it may
	// show a neighbour's handle where the tab has always grouped it here.

	/** 11 - the nuclear line, kept together so it reads as one chain rather than stray oddities. */
	public static void nuclear(Sink out) {
		show(out, ModContent.RAW_URANIUM);
		show(out, ModContent.URANIUM_DUST);
		show(out, ModContent.URANIUM_INGOT);
		// The centrifuge branch, right after the plain smelting one it forks from: the same dust either
		// becomes an ingot in a furnace or twice as many shavings here, which press into refined uranium.
		show(out, ModContent.URANIUM_SHAVINGS);
		show(out, ModContent.REFINED_URANIUM);
		show(out, ModContent.UNSTABLE_ISOTOPE);
		show(out, ModContent.IRRADIATED_SLAG);
		show(out, ModContent.IRRADIATED_DIAMOND);
		// MOD-145 - the Recycler's family: its three grades of slag, its ash and its blades.
		show(out, ModContent.SLAG_POOR);
		show(out, ModContent.SLAG);
		show(out, ModContent.SLAG_RICH);
		show(out, ModContent.SLAG_BLOCK_ITEM);
		show(out, ModContent.ASH);
		// MOD-590 - the coal sink the slag feeds, listed in the order the player walks it: dust is
		// pressed into rods, two rods are hammered into one, two of those are fired with slag into a
		// briquette, the briquette cuts into plates, four plates make the block.
		show(out, ModContent.CARBON_ROD);
		show(out, ModContent.CARBON_ROD_DOUBLE);
		show(out, ModContent.SOOT);
		show(out, ModContent.CARBON_BRIQUETTE);
		show(out, ModContent.CERAMIC_PLATE);
		show(out, ModContent.CARBON_CERAMIC_ITEM);
		show(out, ModContent.RECYCLER_BLADES_IRON);
		show(out, ModContent.RECYCLER_BLADES_TEMPERED);
		show(out, ModContent.RECYCLER_BLADES_DIAMOND);
		show(out, ModContent.RESONANT_SHARD);
		show(out, ModContent.MUTAGEN_DUST);
		// MOD-468, stage 1 - the reactor room's shell. Kept in the nuclear group rather than with the
		// building blocks: these are reactor parts that happen to be cubes, and a player hunting for
		// them will look here.
		show(out, ModContent.REACTOR_CASING_ITEM);
		show(out, ModContent.IRRADIATED_SOIL_ITEM);
		show(out, ModContent.REACTOR_GLASS_ITEM);
		show(out, ModContent.REACTOR_PORT_ITEM);
		show(out, ModContent.REACTOR_CONTROLLER_ITEM);
		show(out, ModContent.REACTOR_LAMP_ITEM);
		show(out, ModContent.REACTOR_OUTLET_ITEM);
		// --- the shaped parts of the same room (MOD-574): a door, a nozzle, the two controls and the
		// fuel column all draw something other than a cube. They stay in the room, not in a shape band.
		show(out, ModContent.REACTOR_DOOR_ITEM);
		show(out, ModContent.STEAM_NOZZLE_ITEM);
		show(out, ModContent.REACTOR_BUTTON_ITEM);
		show(out, ModContent.REACTOR_LEVER_ITEM);
		show(out, ModContent.FUEL_ROD_ASSEMBLY_ITEM);
		show(out, ModContent.DEPLETED_URANIUM);
		show(out, ModContent.EMPTY_FUEL_ROD);
		show(out, ModContent.URANIUM_FUEL_ROD);
	}
}
