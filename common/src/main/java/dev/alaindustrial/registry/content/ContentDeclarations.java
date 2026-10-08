package dev.alaindustrial.registry.content;

import dev.alaindustrial.Config;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.compat.LineBlockProps;
import dev.alaindustrial.entity.ChestBoatVariants;
import dev.alaindustrial.entity.StockDisplayFrameEntity;
import dev.alaindustrial.item.misc.DurableComponentItem;
import dev.alaindustrial.item.misc.HintItem;
import dev.alaindustrial.item.misc.OverclockerChipItem;
import dev.alaindustrial.item.tool.ScytheItem;
import dev.alaindustrial.item.tool.ScytheTier;
import dev.alaindustrial.item.tool.ScytheTiers;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ContentManifest.BlockDef;
import dev.alaindustrial.registry.ContentManifest.BlockEntityDef;
import dev.alaindustrial.registry.ContentManifest.HiddenFromPlayers;
import dev.alaindustrial.registry.ContentManifest.ItemDef;
import dev.alaindustrial.registry.ContentManifest.MenuDef;
import dev.alaindustrial.registry.ContentManifest.MenuFactory;
import dev.alaindustrial.registry.ModBlockProperties;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * The words a content domain file is written in (MOD-711, batch 3): {@code block(...)}, {@code item(...)},
 * {@code blockEntity(...)}, {@code menu(...)} and their shorthands. They build the records of
 * {@link ContentManifest} — {@link BlockDef}, {@link ItemDef}, {@link BlockEntityDef}, {@link MenuDef} — and,
 * for blocks, collect the declarations of the domain whose class is being initialised.
 *
 * <p><b>Why blocks are collected and the other lists are not.</b> A block is declared as a constant, because
 * loaders, block entities and gametests name it ({@code handle(EnergyGenerationContent.GENERATOR)}); a second,
 * hand-written list of those constants is what MOD-711 batch 2 removed. So every {@code block(...)} call is
 * added to the domain's open collection, in declaration order: {@link #beginBlocks()} opens it at the top of
 * the domain's static initialiser, {@link #endBlocks()} closes it into the domain's block list. Items, block
 * entities and menus are entries of a list, not constants, and need no collector.
 *
 * <p><b>Why this class touches nothing of {@link ContentManifest} but its nested records.</b> The aggregator
 * initialises the domains (it reads their {@code DOMAIN} fields); a domain that read one of the aggregator's
 * static fields back would re-enter a class whose initialisation is in progress and see its lists as
 * {@code null}. Constructing a nested record ({@code new BlockDef<>(…)}) does not initialise the outer class.
 * {@code ArchitectureRules.contentDomainsInitialiseOnTheirOwn} holds that for the whole package, and the same
 * rule keeps a domain from naming another domain's entries — so no domain's initialiser starts another's,
 * which {@link #beginBlocks()} would refuse at run time anyway.
 *
 * <p>Package-private: only the domain files of this package declare content.
 */
final class ContentDeclarations {
	private ContentDeclarations() {
	}

	/**
	 * The blocks of the domain whose static initialiser runs on this thread, or {@code null} between domains.
	 * Per thread, because a class is initialised on one thread under its own lock and two domains may be
	 * initialised by two threads at once (a loader's handle and the aggregator on a mod-loading worker).
	 */
	private static final ThreadLocal<List<BlockDef<?>>> OPEN_BLOCKS = new ThreadLocal<>();

	/**
	 * Opens the block collection of the domain whose static initialiser is running. Called once, from a
	 * {@code static} block above the domain's first block constant: static initialisers run in textual order.
	 *
	 * @throws IllegalStateException if another domain's collection is still open on this thread — its
	 *         initialiser named an entry of this one, which the domain files must not do
	 */
	static void beginBlocks() {
		if (OPEN_BLOCKS.get() != null) {
			throw new IllegalStateException("a content domain started initialising inside another one — a domain"
					+ " file names no other domain's entries (MOD-711)");
		}
		OPEN_BLOCKS.set(new ArrayList<>());
	}

	/** Closes the collection {@link #beginBlocks()} opened: the domain's blocks, in declaration order. */
	static List<BlockDef<?>> endBlocks() {
		List<BlockDef<?>> blocks = OPEN_BLOCKS.get();
		if (blocks == null) {
			throw new IllegalStateException("endBlocks() without beginBlocks() — the domain's block list is"
					+ " collected between the two");
		}
		OPEN_BLOCKS.remove();
		return List.copyOf(blocks);
	}

	/**
	 * Builds a {@link MenuDef}, capturing the menu-class generic {@code T} from {@code factory} at the call
	 * site so a domain's {@code MENUS} list stays free of explicit type witnesses.
	 *
	 * <p>{@code T} is fixed by {@code factory} (an exact method reference), and the {@code bind} target — a
	 * typed {@code ModContent} slot — is then <i>checked</i> against it (MOD-198). Pointing an entry at the
	 * wrong slot ({@code menu("sawmill", SawmillMenu::new, s -> ModContent.MACERATOR_MENU = s)}) is therefore
	 * a compile error, not a silent swap: {@code Supplier<MenuType<SawmillMenu>> cannot be converted to
	 * Supplier<MenuType<MaceratorMenu>>}. (In {@link dev.alaindustrial.client.screen.MenuScreenManifest#screen}
	 * the same guard shows up one step earlier, as a type-inference conflict rather than an assignment error.)
	 */
	static <T extends AbstractContainerMenu> MenuDef<T> menu(String id, MenuFactory<T> factory,
			Consumer<Supplier<MenuType<T>>> bind) {
		return new MenuDef<>(id, factory, bind);
	}

	/**
	 * Builds a {@link BlockDef}, capturing the block-class generic {@code T} from {@code factory} at the call
	 * site — the same trick as {@link #menu}. Each constant of a domain is public so a loader's typed handle
	 * is derived from <i>this</i> entry ({@code handle(EnergyGenerationContent.GENERATOR)}) rather than from a
	 * string key: pointing a {@code DeferredBlock<SolarPanelBlock>} field at the generator entry is then a
	 * compile error, not a silent mismatch.
	 *
	 * <p>{@code props} is the block's own {@code Properties} chain, written in the declaration (MOD-711; it
	 * used to be a {@code BLOCK_PROPS} map entry under the same id). A transport line listed in
	 * {@link ModBlockProperties#WASHED_AWAY_BY_FLUIDS} gets the line's wash-away marking applied after it.
	 */
	static <T extends Block> BlockDef<T> block(String id, Function<BlockBehaviour.Properties, T> factory,
			UnaryOperator<BlockBehaviour.Properties> props, Consumer<Supplier<Block>> bind) {
		return block(id, factory, props, bind, null);
	}

	/** A block no creative tab shows; {@code hidden} says how the player meets it instead (MOD-711). */
	static <T extends Block> BlockDef<T> block(String id, Function<BlockBehaviour.Properties, T> factory,
			UnaryOperator<BlockBehaviour.Properties> props, Consumer<Supplier<Block>> bind,
			@Nullable HiddenFromPlayers hidden) {
		List<BlockDef<?>> open = OPEN_BLOCKS.get();
		if (open == null) {
			throw new IllegalStateException("BlockDef '" + id + "' declared outside its domain's block collection —"
					+ " declare it between beginBlocks() and the domain's BLOCKS field, or it is never registered");
		}
		UnaryOperator<BlockBehaviour.Properties> chain = ModBlockProperties.WASHED_AWAY_BY_FLUIDS.contains(id)
				? p -> LineBlockProps.washedAwayByFluids(props.apply(p)) : props;
		BlockDef<T> def = new BlockDef<>(id, factory, chain, bind, hidden);
		open.add(def);
		return def;
	}

	static HiddenFromPlayers hiddenFromPlayers(String reason) {
		return new HiddenFromPlayers(reason);
	}

	/**
	 * Wraps a machine/ore/material block's {@code strength/sound/…} chain with the shared base every such
	 * block carries — {@code requiresCorrectToolForDrops()} (a pickaxe is needed to drop; harvest tier is
	 * tag-driven) and the map colour of a steel body, {@link MapColor#METAL} (MOD-752: what vanilla gives
	 * iron machinery — anvil, hopper, cauldron). A block of another material names its colour with
	 * {@link #machine(MapColor, UnaryOperator)}. Torch blocks skip this (they break by hand) and use
	 * {@link ModBlockProperties#applyTorch} directly. {@code setId} is layered by each loader (Fabric from its
	 * key; NeoForge from the deferred key).
	 */
	static UnaryOperator<BlockBehaviour.Properties> machine(UnaryOperator<BlockBehaviour.Properties> chain) {
		return machine(MapColor.METAL, chain);
	}

	/**
	 * {@link #machine(UnaryOperator)} for a block whose top face is not machine steel (MOD-752): the colour
	 * a filled map draws for it — the vanilla colour of its material, or the one nearest its top face.
	 * {@link MapColor#NONE} keeps a see-through block (glass, a glass door or dome) off the map, as vanilla
	 * glass is. A {@code mapColor} call inside {@code chain} still wins: it is applied after this one.
	 */
	static UnaryOperator<BlockBehaviour.Properties> machine(MapColor mapColor,
			UnaryOperator<BlockBehaviour.Properties> chain) {
		return p -> chain.apply(p.requiresCorrectToolForDrops().mapColor(mapColor));
	}

	/** An item with a hand-written construction. */
	static ItemDef item(String id, Function<Item.Properties, ? extends Item> factory,
			Consumer<Supplier<Item>> bind) {
		return new ItemDef(id, factory, bind, null);
	}

	/** An item with a hand-written construction that no creative tab shows (MOD-711). */
	static ItemDef item(String id, Function<Item.Properties, ? extends Item> factory,
			Consumer<Supplier<Item>> bind, HiddenFromPlayers hidden) {
		return new ItemDef(id, factory, bind, hidden);
	}

	/**
	 * A plain crafting component: nothing but {@code new Item(properties)} — dusts, plates, ingots, raw
	 * ores, by-products. The largest group by far.
	 */
	static ItemDef plain(String id, Consumer<Supplier<Item>> bind) {
		return item(id, Item::new, bind);
	}

	/**
	 * Two gray hint lines under the name, keyed {@code item.alaindustrial.<id>.hint} / {@code .hint2}.
	 * The key strings are derived from the id here rather than typed twice per loader.
	 */
	static ItemDef hint(String id, Consumer<Supplier<Item>> bind) {
		return item(id, p -> new HintItem(p, "item.alaindustrial." + id + ".hint",
				"item.alaindustrial." + id + ".hint2"), bind);
	}

	/** An overclocker chip of a fixed tier (MOD-393) — a hint item that also carries its step count. */
	static ItemDef overclockerChip(String id, int tier, Consumer<Supplier<Item>> bind) {
		return item(id, p -> new OverclockerChipItem(p, tier, "item.alaindustrial." + id + ".hint",
				"item.alaindustrial." + id + ".hint2"), bind);
	}

	/**
	 * A wearing machine component (MOD-189): {@code durability(max)} sets the vanilla {@code max_damage}
	 * component, so wear renders as the standard durability bar and the item becomes non-stackable.
	 * {@code max} is read from {@link Config} when the item is constructed — i.e. at registration, so a
	 * config change still needs a restart; the wear RATE is read live each tick in the block entity.
	 *
	 * <p>Note what {@code durability(max)} actually is since MOD-384: the item's DEFAULT ceiling, not a
	 * fixed one. {@code max_damage} is an ordinary stack component, so the repair bench lowers it on the
	 * individual stack it repairs and {@code ItemStack.getMaxDamage()} reads that override.
	 * {@link DurableComponentItem} carries the matching tooltip.
	 */
	static ItemDef durableComponent(String id, IntSupplier maxDamage, Consumer<Supplier<Item>> bind) {
		return item(id, p -> new DurableComponentItem(p.durability(maxDamage.getAsInt())), bind);
	}

	/**
	 * An armour piece (MOD-056/466/470). {@code humanoidArmor(material, type)} wires durability,
	 * attributes, enchantability, the {@code EQUIPPABLE} component (equip sound + asset id from the
	 * material) and the repair tag in one call — exactly how vanilla {@code Items.IRON_HELMET} is built.
	 */
	static ItemDef armor(String id, ArmorMaterial material, ArmorType type, Consumer<Supplier<Item>> bind) {
		return item(id, p -> new Item(p.humanoidArmor(material, type)), bind);
	}

	/**
	 * A scythe tier (MOD-068/168). The id and every stat come from the loader-neutral
	 * {@link ScytheTiers} catalogue, so a balance tweak cannot drift between the loaders.
	 * {@code .hoe(...)} attaches the data-driven tool component exactly like a vanilla hoe, but the
	 * instance is a {@link ScytheItem}: right-click clears an area instead of tilling.
	 */
	static ItemDef scythe(ScytheTier tier, Consumer<Supplier<Item>> bind) {
		return item(tier.id(), p -> {
			Item.Properties props = p.hoe(tier.material(), tier.attackDamage(), -1.0f);
			return new ScytheItem(tier.profile(), tier.fireResistant() ? props.fireResistant() : props);
		}, bind);
	}

	/**
	 * A filled mod-fluid bucket (MOD-238 oil, MOD-251 diesel/fuel oil, MOD-146/525 the organic pair) —
	 * built exactly like vanilla {@code Items.WATER_BUCKET}. The still fluid is resolved by id: on both
	 * loaders the fluid registry is populated before the item factory runs (Fabric calls
	 * {@code ModFluids.init()} first; on NeoForge the FLUID {@code RegisterEvent} fires before ITEM).
	 */
	static ItemDef bucket(String id, String fluidId, Consumer<Supplier<Item>> bind) {
		return item(id, p -> new BucketItem(registeredFluid(fluidId),
				p.craftRemainder(Items.BUCKET).stacksTo(1)), bind);
	}

	/**
	 * An item whose CLASS differs per loader — the manifest owns its id, its place in the order and its
	 * {@link ModContent} slot, and the loader supplies the constructor through its own override map
	 * (see {@code ModItems.LOADER_ITEMS} / {@code ModItemsNeoForge.LOADER_ITEMS}).
	 *
	 * <p>Which items need one is a loader-API question, never one of content: the forge hammer's
	 * craft-remainder hook has a different signature per loader; and where NeoForge gates the hoe and shovel
	 * conversion ({@code compat.RightClickTransform}) on an {@code ItemAbility} the held item must declare
	 * (26.2, MOD-378/MOD-379), so do the four electric hoe/shovel tiers — on 26.3 that conversion is a data
	 * component and they are shared classes. A loader-specific class is a last resort.
	 *
	 * <p><b>There is deliberately no shared default.</b> A default would let a loader that forgot its
	 * override ship the wrong class silently — which is exactly the defect MOD-378 and MOD-379 each
	 * fixed once. With {@code factory == null} the replay throws at startup instead
	 * ({@link ContentManifest#itemFactory}).
	 */
	static ItemDef loaderItem(String id, Consumer<Supplier<Item>> bind) {
		return new ItemDef(id, null, bind, null);
	}

	/** A block item whose registry id equals its block's ({@code alaindustrial:macerator} → the block). */
	static ItemDef blockItem(String id, Consumer<Supplier<BlockItem>> bind) {
		return blockItem(id, id, bind);
	}

	/** A block item whose id differs from the block's ({@code kok_sagyz_seeds} places {@code kok_sagyz}). */
	static ItemDef blockItem(String id, String blockId, Consumer<Supplier<BlockItem>> bind) {
		return blockItem(id, blockId, UnaryOperator.identity(), bind);
	}

	/**
	 * A block item with extra shared {@code Properties} (MOD-479: rarity + tooltip style). The extras
	 * live here rather than per loader because {@code loader_parity_check} compares the SETS of item
	 * ids, never their properties — a {@code .rarity(...)} written once per loader would drift the first
	 * time somebody edited one of them, with every gate still green.
	 */
	static ItemDef blockItem(String id, String blockId, UnaryOperator<Item.Properties> extra,
			Consumer<Supplier<BlockItem>> bind) {
		return blockItem(id, p -> new BlockItem(registeredBlock(blockId), extra.apply(p)), bind);
	}

	/** A block item under its own {@code BlockItem} subclass (pipes, the tank, the torch). */
	static ItemDef blockItem(String id, Function<Item.Properties, ? extends BlockItem> factory,
			Consumer<Supplier<BlockItem>> bind) {
		return new ItemDef(id, p -> factory.apply(p.useBlockDescriptionPrefix()), blockItemSlot(bind), null);
	}

	/**
	 * Adapts a {@code Supplier<BlockItem>} {@link ModContent} slot to the {@code Supplier<Item>} the
	 * replay hands out. The cast cannot lie: only {@link #blockItem} reaches this, and every one of its
	 * factories returns a {@code BlockItem}. Keeping the slot type in the helper signature is what makes
	 * pointing a block item at a plain-item slot (or the reverse) a compile error.
	 */
	private static Consumer<Supplier<Item>> blockItemSlot(Consumer<Supplier<BlockItem>> bind) {
		return s -> bind.accept(() -> (BlockItem) s.get());
	}

	/**
	 * Presentation for a block item a player cannot obtain (MOD-479): the light-purple name vanilla puts
	 * on the dragon egg, the barrier and the command block — technical blocks, exactly like this one —
	 * plus this mod's own tooltip frame.
	 *
	 * <p><b>{@code EPIC} is the ceiling.</b> NeoForge marks {@code Rarity} extensible and Fabric does
	 * not, so inventing a "legendary" tier would be an asymmetry by construction.
	 *
	 * <p>The tooltip style is an id, not a sprite: {@code TooltipRenderUtil} expands it into
	 * {@code alaindustrial:tooltip/creative_background} and {@code …_frame}. BOTH must exist — a custom
	 * style replaces the vanilla background instead of falling back to it, so shipping only the frame
	 * leaves the text sitting on a missing texture. Nothing in the repo checks that; see the task.
	 */
	static final UnaryOperator<Item.Properties> CREATIVE_ONLY_ITEM = props -> props
			.rarity(Rarity.EPIC)
			.component(DataComponents.TOOLTIP_STYLE, Industrialization.id("creative"));

	/**
	 * The registered block for {@code blockId}, for a block item's factory.
	 *
	 * <p>Resolved from the vanilla registry rather than from a loader handle, for the same reason
	 * {@link BlockEntityDef#blockSet()} does it: the id is the one name both loaders share. An
	 * unregistered id throws instead of quietly resolving to {@code AIR} — {@code getValue} on a
	 * defaulted registry substitutes AIR, so AIR is what a typo looks like, and a {@code BlockItem} over
	 * AIR would place nothing while looking perfectly registered.
	 */
	static Block registeredBlock(String blockId) {
		Identifier key = Industrialization.id(blockId);
		Block block = BuiltInRegistries.BLOCK.getValue(key);
		if (block == Blocks.AIR) {
			throw new IllegalStateException("ItemDef: block '" + key + "' is not registered (yet) — "
					+ "its block item cannot be built");
		}
		return block;
	}

	/** The registered still fluid for {@code fluidId}, for a bucket's factory. See {@link #registeredBlock}. */
	private static FlowingFluid registeredFluid(String fluidId) {
		Identifier key = Industrialization.id(fluidId);
		Fluid fluid = BuiltInRegistries.FLUID.getValue(key);
		if (!(fluid instanceof FlowingFluid flowing)) {
			throw new IllegalStateException("ItemDef: fluid '" + key + "' is not a registered FlowingFluid "
					+ "(got " + fluid + ") — its bucket cannot be built");
		}
		return flowing;
	}

	/**
	 * The registered item for {@code itemId} — only an entry registered EARLIER can be asked for: one declared
	 * above in the same domain, or in a domain {@link ContentManifest#DOMAINS} lists before this one.
	 */
	static Item registeredItem(String itemId) {
		Identifier key = Industrialization.id(itemId);
		Item item = BuiltInRegistries.ITEM.getValue(key);
		if (item == Items.AIR) {
			throw new IllegalStateException("ItemDef: item '" + key + "' is not registered (yet) — an "
					+ "entry may only reference an item declared EARLIER in ContentManifest.ITEMS");
		}
		return item;
	}

	/**
	 * The stock display frame's entity type (MOD-066), typed for {@code StockDisplayFrameItem}.
	 *
	 * <p>The cast is unchecked because the registry is heterogeneous, and safe because
	 * {@code alaindustrial:stock_display_frame} is registered from exactly one place on each loader with
	 * exactly this entity class. The same cast, for the same reason, is in
	 * {@code StockDisplayFrameScenarios}.
	 */
	@SuppressWarnings("unchecked")
	static EntityType<StockDisplayFrameEntity> stockDisplayFrameType() {
		Identifier key = Industrialization.id("stock_display_frame");
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(key);
		if (type == null) {
			throw new IllegalStateException("ItemDef: entity type '" + key + "' is not registered (yet) — "
					+ "the display-frame item cannot be built");
		}
		return (EntityType<StockDisplayFrameEntity>) type;
	}

	/**
	 * A chest boat (MOD-785): the vanilla {@code BoatItem} over the pair's entity type, one per stack like
	 * the vanilla boats. The id must be a pair of {@code ChestBoatVariants} — a typo throws at class-init.
	 * The entity type is resolved by id inside the factory, for the same ordering reason as the display
	 * frame's.
	 */
	static ItemDef chestBoat(String id, Consumer<Supplier<Item>> bind) {
		ChestBoatVariants.byId(id);
		return new ItemDef(id, p -> new BoatItem(chestBoatType(id), p.stacksTo(1)), bind, null);
	}

	/**
	 * A chest boat's entity type (MOD-785), typed for the vanilla {@code BoatItem}. Same unchecked-but-safe
	 * cast as {@link #stockDisplayFrameType()}: each id is registered from exactly one place per loader,
	 * always with a {@code ModChestBoat} factory.
	 */
	@SuppressWarnings("unchecked")
	static EntityType<? extends AbstractBoat> chestBoatType(String path) {
		Identifier key = Industrialization.id(path);
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(key);
		if (type == null) {
			throw new IllegalStateException("ItemDef: entity type '" + key + "' is not registered (yet) — "
					+ "the chest-boat item cannot be built");
		}
		return (EntityType<? extends AbstractBoat>) type;
	}

	/**
	 * Builds a {@link BlockEntityDef}. The valid blocks are named by their {@link BlockDef} constants
	 * (MOD-711), so a misspelt block is a compile error rather than a startup failure in
	 * {@link BlockEntityDef#blockSet()}; the definition still stores their registry ids, which is what each
	 * loader resolves at its own registration moment.
	 */
	static <T extends BlockEntity> BlockEntityDef<T> blockEntity(String id, Class<T> type,
			BlockEntityType.BlockEntitySupplier<T> factory,
			Consumer<Supplier<BlockEntityType<?>>> bind, BlockDef<?>... blocks) {
		List<String> ids = new ArrayList<>(blocks.length);
		for (BlockDef<?> block : blocks) {
			ids.add(block.id());
		}
		return new BlockEntityDef<>(id, type, factory, bind, List.copyOf(ids));
	}
}
