package dev.alaindustrial.registry;

import dev.alaindustrial.registry.content.AgricultureContent;
import dev.alaindustrial.registry.content.DecorContent;
import dev.alaindustrial.registry.content.EnergyGenerationContent;
import dev.alaindustrial.registry.content.EnergyGridContent;
import dev.alaindustrial.registry.content.FluidContent;
import dev.alaindustrial.registry.content.MaterialsContent;
import dev.alaindustrial.registry.content.ProcessingContent;
import dev.alaindustrial.registry.content.ReactorContent;
import dev.alaindustrial.registry.content.StorageContent;
import dev.alaindustrial.registry.content.ToolsAndGearContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ItemLike;

/**
 * Loader-neutral source of truth for public creative-inventory visibility — the table of contents.
 *
 * <p><b>The entries live in the domain files</b> (MOD-711, batch 4; ADR-041): every run of the tab is a
 * section method of the {@code registry/content/<Domain>Content.java} that coding.md §1 files it under, next to
 * the declarations of that domain. This class only says WHICH sections a tab shows and in WHAT order — the
 * hand-made reading order of MOD-407 and the two bands of MOD-574 stay here, where one screen shows them whole.
 * A new entry goes into its domain's section; a new section is one call in the right place below.
 *
 * <p>This deliberately lists only player-visible MVP content. Registered-hidden entries carry
 * {@code hiddenFromPlayers("why")} on their manifest declaration, and the both-lane
 * {@code CreativeTabCoverageScenarios} fails on an entry that is neither shown nor flagged.
 *
 * <p><b>Vanilla Combat and Tools &amp; Utilities are shared here</b> (MOD-477 → MOD-555): an {@link AnchoredSink}
 * places the mod's gear NEXT TO the matching vanilla gear, and a loader supplies only its own way of placing an
 * entry after an anchor — the two loaders once kept a copy each, and the copies drifted (MOD-478).
 */
public final class CreativeTabContent {
	private CreativeTabContent() {
	}

	@FunctionalInterface
	public interface Sink {
		void accept(ItemLike item);
	}

	/**
	 * A {@link Sink} that can also POSITION an entry, for the vanilla tabs where the mod's gear has to
	 * stand next to the matching vanilla gear rather than at the end (MOD-555).
	 *
	 * <p>One verb, because that is all the two loaders have in common. Fabric's tab output takes an anchor
	 * and a whole group at once ({@code insertAfter(anchor, a, b, c)}); NeoForge's event takes one stack at
	 * a time and asserts the anchor is present, so its adapter chains the group — anchor → a, a → b, b → c —
	 * and falls back to an append when a third-party mod has removed the anchor (MOD-349). Both produce the
	 * same order; neither shape belongs in this file.
	 */
	public interface AnchoredSink extends Sink {
		/** Places {@code items} directly after {@code anchor}, in the order given. */
		void insertAfter(ItemLike anchor, List<ItemLike> items);
	}

	/**
	 * Items that belong in the BLOCK band even though they are not {@code BlockItem}s (MOD-574).
	 *
	 * <p>Exactly one so far: the Garden Drone is an item, but it is the thing the Garden Drone Station
	 * launches, and the two are read as one machine. Splitting them across the two bands would put a
	 * station in the first half of the tab and its drone somewhere among the ingots.
	 *
	 * <p>Registry paths rather than handles: this is asked once per entry while the tab is built, and a
	 * path lookup cannot resolve a half-built handle by accident.
	 */
	private static final Set<String> ITEMS_SHOWN_WITH_BLOCKS = Set.of("garden_drone");

	/**
	 * Buffers the tab and hands it back in two bands: everything placeable first, loose items after.
	 *
	 * <p><b>Two bands, not three.</b> An earlier revision sorted by silhouette — cubes, then shaped
	 * blocks, then items — and that read worse, not better: it tore families apart. The reactor room
	 * came out as six cubes near the top of the tab and its door, button, lever, nozzle and fuel column
	 * a hundred entries later, with the uranium that feeds it in a third place. Shape is a weaker
	 * signal than subject; a player hunting for a reactor part looks for the reactor, not for a cube.
	 *
	 * <p>So the only thing this class separates is placeable from carryable, and every subject stays in
	 * one run: the eight cables together, the six buckets together, the twelve reactor blocks together,
	 * the thirteen uranium items together. Within a band the sequence {@link #fill} produced is kept
	 * exactly, so the MOD-407 reading order survives; the shape ordering that remains is done by hand
	 * INSIDE each section, where it cannot separate a family from itself.
	 */
	private static final class ShapeSorted implements Sink {
		private final List<ItemLike> placeable = new ArrayList<>();
		private final List<ItemLike> carryable = new ArrayList<>();

		@Override
		public void accept(ItemLike item) {
			Identifier id = BuiltInRegistries.ITEM.getKey(item.asItem());
			boolean withBlocks = item.asItem() instanceof BlockItem
					|| (id != null && ITEMS_SHOWN_WITH_BLOCKS.contains(id.getPath()));
			(withBlocks ? placeable : carryable).add(item);
		}

		void drainTo(Sink out) {
			placeable.forEach(out::accept);
			carryable.forEach(out::accept);
		}
	}

	/**
	 * The mod's own tab, in the order a player actually meets the mod (MOD-407).
	 *
	 * <p>Energy first and in the order it flows — where it comes FROM, where it is KEPT, how it is
	 * CARRIED, what SPENDS it — then the two logistics networks, then what the player holds, and only
	 * then the raw materials and crafting parts everything above is built from. Blocks and the armour
	 * lines close the list; after them, outside both bands, come the decorative lab plaque plates
	 * (MOD-513), which a player never builds with and only looks up.
	 *
	 * <p><b>On top of that order the tab is split into two bands</b> (MOD-574): everything placeable
	 * first, loose items after — see {@link ShapeSorted} for why two and not three. Within a section the
	 * full cubes come first and the shaped models follow; sorting the whole tab by shape was tried and
	 * reverted, because it tore every family in half.
	 */
	public static void main(Sink out) {
		ShapeSorted sorted = new ShapeSorted();
		fill(sorted);
		sorted.drainTo(out);
		// MOD-513 — the lab plaque plates close the tab, after both bands: pure decoration with no
		// recipe and no function, so they must not sit among the blocks a player builds with.
		DecorContent.labPlaque(out);
	}

	/** The tab's content in reading order, before {@link ShapeSorted} groups it by silhouette. */
	private static void fill(Sink out) {
		// 1 - where energy comes from.
		EnergyGenerationContent.generators(out);
		// 2 - where it is kept (and, for the teleporter, banked to be spent in one go).
		StorageContent.energyStorage(out);
		// 3 - how it is carried: the conductor ladder, then the accessory installed on it.
		EnergyGridContent.energyTransfer(out);
		EnergyGridContent.cableAccessories(out);
		// 4 - what spends it: the processing line, LV first and the MV assembler last, then the farm machines.
		ProcessingContent.machines(out);
		AgricultureContent.farmMachines(out);
		// 5 - the fluid chain, from the pump that fills a tank to the column that splits oil, then what
		// carries those fluids by hand.
		FluidContent.fluids(out);
		FluidContent.fluidCarriers(out);
		// 6 - item logistics: the containers the pipe serves.
		StorageContent.itemLogistics(out);
		// 6b - the chest boats (MOD-785): the mod chests again, on the water.
		StorageContent.chestBoats(out);
		// 7 - what the player holds: hand tools first (no charge needed), then the powered gear.
		ToolsAndGearContent.handTools(out);
		ToolsAndGearContent.poweredGear(out);
		// 8 - what goes INTO a machine to change how it runs.
		ToolsAndGearContent.upgrades(out);
		// 9 - what machines and blocks are built from.
		MaterialsContent.craftingComponents(out);
		// 10 - raw materials: ore -> dust -> ingot per metal, then plates and the alloys.
		MaterialsContent.materials(out);
		// 11 - the nuclear line, kept together and away from ordinary materials.
		ReactorContent.nuclear(out);
		// 12 - blocks: ores as placed, metal and plate blocks, the workbench, the torch.
		blocks(out);
		// 13 - the armour and weapon lines close the tab, tempered iron then Fluxweave.
		ToolsAndGearContent.wearablesAndWeapons(out);
	}

	/** 12 - blocks: ores and plantings as found, then what is built out of metal, the stations and the torch. */
	private static void blocks(Sink out) {
		naturalBlocks(out);
		DecorContent.metalBlocks(out);
		DecorContent.mobRepellers(out);
		ProcessingContent.stations(out);
		// Shaped last (MOD-574): the torch is a sprite among cubes.
		DecorContent.torch(out);
	}

	/** The mod's contribution to the VANILLA Combat tab (MOD-478 → MOD-555). */
	public static void combat(AnchoredSink out) {
		ToolsAndGearContent.vanillaCombat(out);
	}

	/** The mod's contribution to the VANILLA Tools &amp; Utilities tab (MOD-478 → MOD-555). */
	public static void toolsAndUtilities(AnchoredSink out) {
		ToolsAndGearContent.vanillaToolsAndUtilities(out);
	}

	public static void ingredients(Sink out) {
		MaterialsContent.vanillaIngredients(out);
	}

	public static void buildingBlocks(Sink out) {
		DecorContent.metalBlocks(out);
		DecorContent.labPlaque(out);
	}

	public static void naturalBlocks(Sink out) {
		MaterialsContent.ores(out);
		AgricultureContent.crops(out);
	}

	public static void functionalBlocks(Sink out) {
		EnergyGenerationContent.generators(out);
		StorageContent.energyStorage(out);
		EnergyGridContent.energyTransfer(out);
		ProcessingContent.machines(out);
		AgricultureContent.farmMachines(out);
		FluidContent.fluids(out);
		StorageContent.itemLogistics(out);
		utility(out);
	}

	/** Decorative and utility blocks of vanilla Functional Blocks: the torch, the repellers, the two stations. */
	private static void utility(Sink out) {
		DecorContent.torch(out);
		DecorContent.mobRepellers(out);
		ProcessingContent.stations(out);
	}
}
