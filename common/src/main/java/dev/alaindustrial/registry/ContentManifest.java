package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.BatteryFed;
import dev.alaindustrial.block.entity.Overclockable;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.jetbrains.annotations.Nullable;

/**
 * Loader-neutral content manifest (MOD-190): the ordered lists of the mod's registrable content — blocks,
 * items, block-entity types and menu types — with only vanilla types and common content classes, no Fabric
 * or NeoForge import. Each loader replays these lists through a thin adapter ({@code ModBlocks},
 * {@code ModItems}, {@code ModBlockEntities}, {@code ModMenus} on Fabric with eager {@code Registry.register};
 * their {@code …NeoForge} twins with lazy {@code DeferredRegister}).
 *
 * <p><b>An aggregator of ten domain files</b> (MOD-711, batch 3; ADR-041). The declarations themselves live
 * in {@code registry/content/}, one file per content domain of {@code docs/standards/coding.md} §1 (owner
 * decision D5): {@code EnergyGenerationContent} … {@code DecorContent}, each written in the words of
 * {@code ContentDeclarations} ({@code block(...)}, {@code item(...)}, {@code blockEntity(...)},
 * {@code menu(...)}). This class holds the records a declaration builds, the one list that orders the domains
 * ({@link #DOMAINS}) and the four joined lists, plus the lookups the rest of the mod asks the manifest. A new
 * block, item, block entity or menu is declared in its domain's file and nowhere else.
 *
 * <p><b>Ordered on purpose.</b> Every list is a {@link List} (insertion order), never a map or a set, so the
 * registration order is identical on both loaders (MOD-190 gotcha #7): the domains in {@link #DOMAINS} order,
 * each domain's entries in declaration order. Order does not affect correctness — registries sync by string
 * id — but one shared order keeps the loaders consistent, and {@code ContentManifestOrderTest} holds it.
 */
public final class ContentManifest {
	private ContentManifest() {
	}

	/**
	 * The ten content domains of {@code docs/standards/coding.md} §1 (owner decision D5), in registration
	 * order (MOD-711, batch 3). Each domain is one file of {@code registry/content}; {@link #BLOCKS},
	 * {@link #ITEMS}, {@link #BLOCK_ENTITIES} and {@link #MENUS} are its lists joined in THIS order, so this
	 * field is the one place that orders the domains, and a new entry goes into its domain's file.
	 *
	 * <p>Declared first on purpose: every list below is built from it, and static fields initialise in
	 * textual order. Reading a domain's {@code DOMAIN} field initialises that domain; a domain never reads
	 * this class back ({@code ArchitectureRules.contentDomainsInitialiseOnTheirOwn}), so the
	 * initialisation has no cycle.
	 */
	public static final List<Domain> DOMAINS = List.of(
			EnergyGenerationContent.DOMAIN,
			EnergyGridContent.DOMAIN,
			ProcessingContent.DOMAIN,
			FluidContent.DOMAIN,
			StorageContent.DOMAIN,
			ReactorContent.DOMAIN,
			AgricultureContent.DOMAIN,
			MaterialsContent.DOMAIN,
			ToolsAndGearContent.DOMAIN,
			DecorContent.DOMAIN);

	/** The {@code list} of every domain, joined in {@link #DOMAINS} order. Unmodifiable. */
	private static <T> List<T> join(Function<Domain, List<T>> list) {
		List<T> joined = new ArrayList<>();
		for (Domain domain : DOMAINS) {
			joined.addAll(list.apply(domain));
		}
		return List.copyOf(joined);
	}

	/**
	 * Everything one content domain declares (MOD-711, batch 3), each list in declaration order: the domain
	 * file's {@code DOMAIN} field. {@link #DOMAINS} joins the domains in a fixed order into the four lists
	 * both loaders replay; a domain is never replayed on its own.
	 *
	 * <p>Nested here, not a file of {@code registry.content}: building it does not initialise this class, and
	 * the domain files may name the records of this class (never its static members).
	 *
	 * @param name          the domain's name in coding.md §1
	 * @param blocks        its blocks, collected by {@code block(...)} between {@code beginBlocks()} and the
	 *                      domain's {@code BLOCKS} field
	 * @param items         its items
	 * @param blockEntities its block-entity types
	 * @param menus         its menu types
	 */
	public record Domain(String name, List<BlockDef<?>> blocks, List<ItemDef> items,
			List<BlockEntityDef<?>> blockEntities, List<MenuDef<?>> menus) {

		public Domain {
			blocks = List.copyOf(blocks);
			items = List.copyOf(items);
			blockEntities = List.copyOf(blockEntities);
			menus = List.copyOf(menus);
		}
	}

	/**
	 * The client menu constructor {@code (int syncId, Inventory) -> Menu}. Our own public functional
	 * interface — the vanilla {@code MenuType.MenuSupplier} is private, so it cannot be named here; each
	 * loader adapts this to its own factory type ({@code MenuType.MenuSupplier} on Fabric,
	 * {@code IContainerFactory} on NeoForge) by passing {@code factory::create}.
	 *
	 * @param <T> the menu class
	 */
	@FunctionalInterface
	public interface MenuFactory<T extends AbstractContainerMenu> {
		T create(int syncId, Inventory playerInventory);
	}

	/**
	 * One {@code MenuType} to register, built by {@code ContentDeclarations.menu}, which captures {@code T}
	 * from the factory so that pointing an entry at another menu's {@code ModContent} slot
	 * ({@code menu("sawmill", SawmillMenu::new, s -> ModContent.MACERATOR_MENU = s)}) is a compile error
	 * (MOD-198): {@code Supplier<MenuType<SawmillMenu>>} is not a {@code Supplier<MenuType<MaceratorMenu>>}.
	 *
	 * @param <T>     the menu class
	 * @param id      registry path ({@code alaindustrial:<id>})
	 * @param factory the client menu constructor, shared by both loaders
	 * @param bind    publishes the registered {@code MenuType} into its {@link ModContent} slot
	 */
	public record MenuDef<T extends AbstractContainerMenu>(String id, MenuFactory<T> factory,
			Consumer<Supplier<MenuType<T>>> bind) {
	}

	/**
	 * Every machine/chest menu, in one shared order: the domains' {@code MENUS} joined in {@link #DOMAINS}
	 * order. Adding a menu = one {@code menu(...)} entry in its domain's file, plus its screen in
	 * {@code dev.alaindustrial.client.screen.MenuScreenManifest}; both loaders pick it up automatically.
	 */
	public static final List<MenuDef<?>> MENUS = join(Domain::menus);

	/**
	 * Why a manifest entry is registered but shown in no creative tab (MOD-711, batch 5).
	 *
	 * <p>Everything else the mod registers must be shown. {@code CreativeTabCoverageScenarios} (both gametest
	 * lanes) walks every item and block of the mod against what the tab entry points hand the loaders, and
	 * fails on an entry that is neither shown nor hidden — and on a hidden one that a tab shows after all. A
	 * block counts as shown through the item that places it ({@code block.asItem()}), so a block placed by an
	 * item of another id (seeds, a standing torch that also places the wall variant) needs no flag.
	 *
	 * <p>The flag replaced a hand list in a Python gate ({@code registry_check.CREATIVE_EXEMPT}) that matched
	 * ids by text: the fact now lives on the entry it is about, and the check reads the live tabs.
	 *
	 * @param reason how the player meets the entry instead — never blank
	 */
	public record HiddenFromPlayers(String reason) {
		public HiddenFromPlayers {
			if (reason == null || reason.isBlank()) {
				throw new IllegalArgumentException("an entry hidden from players states why");
			}
		}
	}

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Blocks — the COMPOSITION, not just the definition (MOD-403)
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * One {@code Block} to register: id, class, {@code Properties} chain and {@link ModContent} slot, in one
	 * declaration. MOD-190 moved the per-block chain out of the two loader files into a map keyed by id;
	 * MOD-711 moved each chain into its own entry, so a block cannot be declared without properties, nor
	 * properties without a block, and no two lists are joined by a string. MOD-403 moved the <b>list
	 * itself</b>, which until then was kept by hand in two files ({@code ModBlocks} on Fabric,
	 * {@code ModBlocksNeoForge}) and guarded only by a Python set-comparison after the fact. A block
	 * declared in a domain file registers on BOTH loaders or on neither.
	 *
	 * <p><b>What each loader still does.</b> Only the registration <i>mechanism</i> stays loader-side,
	 * because the two genuinely differ: Fabric constructs the block eagerly and stamps the id itself
	 * ({@code Properties.of().setId(key)} → {@code Registry.register}), NeoForge hands the same factory to
	 * {@code DeferredRegister.Blocks#registerBlock}, which calls it later with a {@code Properties} whose id
	 * it derived from the deferred key. Both apply the entry's own {@link #props()} to that base — a block can
	 * no longer be given another block's properties, because neither side looks them up any more.
	 *
	 * <p><b>Order is load-bearing.</b> Both loaders replay {@link #BLOCKS} in list order, and two entries
	 * depend on an earlier one having registered: {@code enriched_uranium_wall_torch} reads the standing
	 * torch for its loot table / description (see {@link ModBlockProperties#applyWallTorch}; both are in the
	 * Decor domain, the wall variant declared right after the standing one), and the liquid blocks read their
	 * fluid from {@link ModContent}. A new block goes at the end of its domain's block constants.
	 *
	 * @param <T>     the concrete block class, captured at the declaration so a loader's typed handle
	 *                ({@code DeferredBlock<GeneratorBlock>}) cannot be wired to the wrong entry — a
	 *                {@code DeferredBlock<SolarPanelBlock>} field on the generator's entry does not compile
	 * @param id      registry path ({@code alaindustrial:<id>})
	 * @param factory builds the block from the loader-supplied {@code Properties}
	 * @param props   the block's {@code Properties} chain, applied by each loader to its own base (Fabric adds
	 *                {@code setId}, NeoForge's {@code registerBlock} does); holds only loader-neutral
	 *                behaviour, and already carries the line's wash-away marking ({@code ContentDeclarations.block})
	 * @param bind    publishes the registered block into its {@link ModContent} slot
	 * @param hidden  why no creative tab shows the block, or {@code null} when one does
	 */
	public record BlockDef<T extends Block>(String id, Function<BlockBehaviour.Properties, T> factory,
			UnaryOperator<BlockBehaviour.Properties> props, Consumer<Supplier<Block>> bind,
			@Nullable HiddenFromPlayers hidden) {
	}

	/**
	 * Every block, in one shared registration order — the single source of the mod's block composition
	 * (MOD-403). Both loaders replay this list; see {@link BlockDef}.
	 *
	 * <p>Self-collected (MOD-711): a domain's blocks are its {@code block(...)} constants in declaration
	 * order, so a new block is declared once, where it belongs, and never listed a second time; this list
	 * joins the domains in {@link #DOMAINS} order.
	 */
	public static final List<BlockDef<?>> BLOCKS = join(Domain::blocks);

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// Items — the COMPOSITION, not just the construction (MOD-305 / MOD-306 / MOD-554)
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * One {@code Item} to register. MOD-306 moved the per-item CONSTRUCTION here; MOD-554 moves the
	 * <b>list itself</b>, which until then was kept by hand in two files ({@code ModItems} on Fabric,
	 * {@code ModItemsNeoForge}) — 273 registrations plus 273 {@code ModContent} bindings in each,
	 * guarded only by a Python set-comparison after the fact. An item declared in a domain file registers on
	 * BOTH loaders or on neither.
	 *
	 * <p><b>What each loader still does.</b> Only the registration <i>mechanism</i>, because the two
	 * genuinely differ: Fabric constructs the item eagerly and stamps the id itself
	 * ({@code new Item.Properties().setId(key)} → {@code Registry.register}), NeoForge hands the same
	 * factory to {@code DeferredRegister.Items#registerItem}, which calls it later with a
	 * {@code Properties} whose id it derived from the deferred key. Neither side chooses the id any more.
	 *
	 * <p><b>Why the factory closes over registry IDS, not handles.</b> A block item needs its block, a
	 * bucket its fluid, the display frame its entity type — all of which are per-loader objects
	 * ({@code Block} vs {@code DeferredBlock}). Resolving them by id from the vanilla registry INSIDE the
	 * factory works on both loaders for the same reason {@link BlockEntityDef#blockSet()} does: the
	 * factory runs after those registries are populated (Fabric registers blocks/fluids/entities before
	 * items in its entrypoint; on NeoForge the {@code RegisterEvent} order does it). The comment that
	 * used to sit here — "a shared factory would have to close over a loader type" — was disproved by
	 * this manifest's own liquid blocks, which have closed over {@code ModContent} inside their factory
	 * since MOD-403.
	 *
	 * <p><b>Order is load-bearing.</b> Both loaders replay {@link #ITEMS} in list order, and one entry
	 * depends on an earlier one: {@code filled_vacuum_capsule} takes the empty capsule as its
	 * craft-remainder (both in the Fluid domain, the filled one declared after the empty one). A new item
	 * goes at the end of its domain's {@code ITEMS}.
	 *
	 * @param id      registry path ({@code alaindustrial:<id>})
	 * @param factory builds the item from the loader-supplied {@code Properties}, or {@code null} for an
	 *                entry whose CLASS differs per loader ({@code ContentDeclarations.loaderItem})
	 * @param bind    publishes the registered item into its {@link ModContent} slot
	 * @param hidden  why no creative tab shows the item, or {@code null} when one does
	 */
	public record ItemDef(String id, @Nullable Function<Item.Properties, ? extends Item> factory,
			Consumer<Supplier<Item>> bind, @Nullable HiddenFromPlayers hidden) {
	}

	/**
	 * The factory a loader must call for {@code def}: its own override when the entry is loader-specific
	 * (declared with {@code ContentDeclarations.loaderItem}, no shared factory), the manifest's own otherwise.
	 * Both mismatches throw rather than picking a side — an override for a shared entry would shadow the shared
	 * definition on one loader only, which is the drift this manifest exists to remove.
	 *
	 * @param def      the manifest entry being replayed
	 * @param override this loader's entry from its {@code LOADER_ITEMS} map, or {@code null}
	 */
	public static Function<Item.Properties, ? extends Item> itemFactory(ItemDef def,
			@Nullable Function<Item.Properties, ? extends Item> override) {
		if (def.factory() == null) {
			if (override == null) {
				throw new IllegalStateException("ItemDef '" + def.id() + "' is declared loader-specific "
						+ "(no shared factory), but this loader supplied no override for it");
			}
			return override;
		}
		if (override != null) {
			throw new IllegalStateException("ItemDef '" + def.id() + "' has a shared factory, so this "
					+ "loader's override for it would silently shadow the shared definition");
		}
		return def.factory();
	}

	/**
	 * Every item, in one shared registration order — the single source of the mod's item composition
	 * (MOD-554): the domains' {@code ITEMS} joined in {@link #DOMAINS} order. Both loaders replay this list;
	 * see {@link ItemDef}.
	 */
	public static final List<ItemDef> ITEMS = join(Domain::items);

	// ─────────────────────────────────────────────────────────────────────────────────────────
	// BlockEntity types (MOD-307)
	// ─────────────────────────────────────────────────────────────────────────────────────────

	/**
	 * One {@code BlockEntityType} to register: its id, the {@code BlockEntity} class it produces, the
	 * factory, and the set of blocks it is valid for — <b>by registry id</b>.
	 *
	 * <p><b>Why the blocks are ids and not {@code Block} handles.</b> A handle would have to come from a
	 * loader registry ({@code ModBlocks.X} is an eager {@code Block}; {@code ModBlocksNeoForge.X} a lazy
	 * holder), so the valid-block set had to be written out twice — and the two copies were held together
	 * by nothing but a Python parity script. That is exactly the defect class MOD-191 filed: a block
	 * missing from one loader's set is not a type error, it is a silent "this block entity does not exist
	 * on that loader". With ids, the set is written once and each loader resolves it against the vanilla
	 * registry at its own registration moment. The manifest itself names those ids through the
	 * {@link BlockDef} constants (MOD-711), so the one copy is checked by the compiler as well.
	 *
	 * @param <T>     the block entity class
	 * @param id      registry path ({@code alaindustrial:<id>})
	 * @param type    the block entity class, so a lookup can verify the caller's expected type
	 * @param factory the {@code BlockEntity} constructor, shared by both loaders
	 * @param bind    publishes the registered {@code BlockEntityType} into its {@link ModContent} slot
	 *                (MOD-403 — before that, each loader wrote out all 40 assignments by hand and a
	 *                forgotten line surfaced only as a {@code verifyAllBound()} crash at startup)
	 * @param blocks  registry ids of the blocks this type is valid for
	 */
	public record BlockEntityDef<T extends BlockEntity>(String id, Class<T> type,
			BlockEntityType.BlockEntitySupplier<T> factory,
			Consumer<Supplier<BlockEntityType<?>>> bind, List<String> blocks) {

		/**
		 * Resolves {@link #blocks} against the vanilla block registry, as an UNMODIFIABLE set in the order of
		 * {@link #blocks} (MOD-417: the vanilla type keeps this very instance). An empty list, an unknown id and
		 * a duplicate throw. Called by each loader when it builds the {@code BlockEntityType}, after its blocks
		 * registered; the reasons are on {@code BlockEntityDefResolver.blockSet}.
		 */
		public Set<Block> blockSet() {
			return BlockEntityDefResolver.blockSet(id, blocks);
		}

		/**
		 * The {@code BlockEntityType} this definition produced, typed on {@code T} — resolved from the
		 * vanilla registry rather than from a loader handle, so client code shared by both loaders can name
		 * it (MOD-403: the {@code BlockEntityRenderer} manifest). Callable only after the loader registered
		 * its block-entity types.
		 *
		 * <p><b>Why the cast is safe.</b> {@link ModContent} keeps its block-entity slots as
		 * {@code Supplier<BlockEntityType<?>>}, so it cannot hand out a typed handle; the registry cannot
		 * either. What pins {@code T} is the call site: a {@code BlockEntityDef<T>} is only obtainable
		 * through {@link ContentManifest#blockEntity(String, Class)}, which throws unless this id's
		 * definition really produces {@code T} — and the loader built the registered type from THAT
		 * definition's factory. So the type parameter is checked, just one step earlier than the cast.
		 */
		@SuppressWarnings("unchecked")
		public BlockEntityType<T> registeredType() {
			return (BlockEntityType<T>) BlockEntityDefResolver.registeredType(id);
		}
	}

	/**
	 * Every {@code BlockEntityType}, declared once for both loaders: the domains' {@code BLOCK_ENTITIES}
	 * joined in {@link #DOMAINS} order. See {@link BlockEntityDef}.
	 */
	public static final List<BlockEntityDef<?>> BLOCK_ENTITIES = join(Domain::blockEntities);

	/**
	 * The definition for block-entity {@code id}, checked against the type the caller expects.
	 *
	 * <p>The {@code Class} argument is what keeps the loader's typed field honest: asking for
	 * {@code blockEntity("macerator", SawmillBlockEntity.class)} fails loudly here instead of producing a
	 * {@code BlockEntityType} whose generic parameter lies about what it creates.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends BlockEntity> BlockEntityDef<T> blockEntity(String id, Class<T> type) {
		for (BlockEntityDef<?> def : BLOCK_ENTITIES) {
			if (def.id().equals(id)) {
				if (def.type() != type) {
					throw new IllegalArgumentException("BlockEntityDef '" + id + "' produces "
							+ def.type().getSimpleName() + ", not " + type.getSimpleName());
				}
				return (BlockEntityDef<T>) def;
			}
		}
		throw new IllegalArgumentException("No BLOCK_ENTITIES entry for block-entity id '" + id + "'");
	}

	/**
	 * Does an overclocker chip do anything in {@code block}? Answered from the block alone, so the
	 * upgrade panel can refuse the chip on the CLIENT, where the menu is backed by a dummy container
	 * with no block entity to ask (MOD-392: a generator used to accept the chip and then ignore it).
	 *
	 * <p>Derived from {@link Overclockable} on the block entity class rather than from a second,
	 * hand-written list of blocks: the manifest already maps blocks to their BE class, so there is
	 * exactly one place to declare that a machine overclocks, and the slot cannot drift from the effect.
	 * Blocks with no block entity at all — every plain building block — answer {@code false}.
	 */
	public static boolean isOverclockable(Block block) {
		return machineIs(block, Overclockable.class);
	}

	/**
	 * Whether {@code block}'s machine has a battery drawer (MOD-679) — the {@link BatteryFed} twin of
	 * {@link #isOverclockable}, and for the same reason: the menu lays out the drawer slot on the client,
	 * where no block entity exists, so the block alone has to answer.
	 */
	public static boolean isBatteryFed(Block block) {
		return machineIs(block, BatteryFed.class);
	}

	/** Whether the block entity of {@code block}'s first {@link #BLOCK_ENTITIES} entry is a {@code trait}. */
	private static boolean machineIs(Block block, Class<?> trait) {
		Identifier key = BuiltInRegistries.BLOCK.getKey(block);
		if (!Industrialization.MOD_ID.equals(key.getNamespace())) {
			return false;
		}
		String path = key.getPath();
		for (BlockEntityDef<?> def : BLOCK_ENTITIES) {
			if (def.blocks().contains(path)) {
				return trait.isAssignableFrom(def.type());
			}
		}
		return false;
	}
}
