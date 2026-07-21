package dev.alaindustrial;

import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.item.ItemEnergyBridge;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.item.ItemLookup;
import dev.alaindustrial.core.neoforge.BufferAsEnergyHandler;
import dev.alaindustrial.core.neoforge.NeoForgeEnergyLookup;
import dev.alaindustrial.core.neoforge.NeoForgeEnergyTransactions;
import dev.alaindustrial.core.neoforge.NeoForgeFluidLookup;
import dev.alaindustrial.core.neoforge.NeoForgeItemLookup;
import dev.alaindustrial.core.neoforge.ContainerAsItemResourceHandler;
import dev.alaindustrial.core.neoforge.TankAsResourceHandler;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.neoforge.NeoForgeNetwork;
import dev.alaindustrial.network.neoforge.NeoForgeNetworkDispatcher;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.neoforge.ModBlockEntitiesNeoForge;
import dev.alaindustrial.registry.neoforge.ModBlocksNeoForge;
import dev.alaindustrial.registry.neoforge.ModItemsNeoForge;
import dev.alaindustrial.registry.neoforge.ModMenusNeoForge;
import java.util.List;
import java.util.function.Supplier;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.command.AlaCommandCommon;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.item.ItemNetworkManager;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * NeoForge {@code @Mod} entrypoint (MOD-022 Phase 3 scaffold). Mirrors the Fabric
 * {@code dev.alaindustrial.IndustrializationFabric} entrypoint; loader-neutral constants live in
 * {@link Industrialization} (common).
 *
 * <p><b>Verified 26.2 wiring (against neoforge-26.2.0.8-beta):</b>
 * <ul>
 *   <li>{@code @Mod(MOD_ID)} + a {@code (IEventBus modBus)} constructor — NeoForge injects the mod
 *       event bus.</li>
 *   <li>{@code DeferredRegister} objects are registered on the mod bus in this constructor
 *       ({@code BLOCKS.register(modBus)}).</li>
 *   <li>Per-side energy exposure goes through {@link RegisterCapabilitiesEvent} using
 *       {@code Capabilities.Energy.BLOCK} (a {@code BlockCapability<EnergyHandler, Direction>});
 *       each machine's {@code BlockEntityType} is registered here once those types exist
 *       (Phase 3/4). The {@code EnergyHandler} returned is adapted to the common
 *       {@code EnergyPort} via {@code dev.alaindustrial.core.neoforge.NeoForgeEnergyPort}.</li>
 * </ul>
 *
 * <p><b>Phase 3 scope:</b> the neutral packet-send seam ({@link NetworkDispatcher}) is installed;
 * the S2C payload is registered via {@link NeoForgeNetwork} on {@code RegisterPayloadHandlersEvent};
 * the block/item/block-entity/menu {@code DeferredRegister} objects are created and registered on the
 * mod bus, and the client screen binding runs from {@link IndustrializationNeoForgeClient} on
 * {@code RegisterMenuScreensEvent}. The registries still hold representative stubs / Phase-4 entry
 * helpers — the machine content classes move to {@code common} in Phase 4 (per-machine migration),
 * at which point each registry's entries and the screen bindings are filled in.
 */
@Mod(Industrialization.MOD_ID)
public final class IndustrializationNeoForge {

	/**
	 * MOD-137: the constructor is a table of contents — each step is a named private method, called in
	 * the same order the statements ran before. The ordering is load-bearing (DeferredRegisters register
	 * on the mod bus before {@code .init()} binds the facade); do not reorder the calls below.
	 */
	public IndustrializationNeoForge(IEventBus modBus) {
		installLoaderSeams();
		registerDeferredRegisters(modBus);
		bindContentFacade(modBus);
		verifyContentBound();
		registerModBusEvents(modBus);
		loadConfig();
		registerGameBusEvents();

		Industrialization.LOGGER.info("Industrialization (NeoForge) initialized.");
	}

	/**
	 * Installs the NeoForge loader seams (energy/fluid/item lookups, item-energy/item-fluid bridges,
	 * packet dispatcher) so common transport/content code stays loader-neutral.
	 */
	private void installLoaderSeams() {
		// MOD-022 Phase 2: install the NeoForge energy seams (transaction opener + capability lookup) so
		// the common transport code can open transactions and resolve per-face ports through the NeoForge
		// EnergyHandler API without importing loader types. These are inert until the NeoForge tick loop
		// and machine BlockEntity types exist (Phase 3/4) — nothing calls them at runtime yet.
		EnergyTransactions.install(new NeoForgeEnergyTransactions());
		EnergyLookup.install(new NeoForgeEnergyLookup());
		// MOD-028: install the NeoForge fluid lookup seam (same seam shape as energy) so common fluid
		// content (the pump) can resolve a neighbour's FluidPort without importing NeoForge transfer types.
		// Fluid transactions reuse the already-installed NeoForgeEnergyTransactions (see FluidPort class doc).
		FluidLookup.install(new NeoForgeFluidLookup());
		// MOD-104: common item pipes resolve neighbouring inventories through the 26.2
		// Capabilities.Item.BLOCK transfer API at this loader seam.
		ItemLookup.install(new NeoForgeItemLookup());
		// MOD-084: install the item-energy bridge seam, so the worn Energy Pack can charge other mods'
		// powered items through Capabilities.Energy.ITEM without common code importing NeoForge types.
		ItemEnergyBridge.install(new dev.alaindustrial.core.neoforge.NeoForgeItemEnergyBridge());
		// MOD-107: install the item-fluid bridge seam, so a machine's own slots can exchange a bucket with
		// whatever fluid container sits in them — vanilla bucket, our capsule, or another mod's cell — via
		// Capabilities.Fluid.ITEM, without common code importing NeoForge transfer types.
		dev.alaindustrial.item.ItemFluidBridge.install(new dev.alaindustrial.core.neoforge.NeoForgeItemFluidBridge());

		// MOD-022 Phase 3: install the NeoForge packet-send seam so content code dispatches through the
		// neutral NetworkDispatcher instead of PacketDistributor directly.
		NetworkDispatcher.install(new NeoForgeNetworkDispatcher());
	}

	/** Registers every {@code DeferredRegister} on the mod bus. Must run before {@link #bindContentFacade}. */
	private void registerDeferredRegisters(IEventBus modBus) {
		// DeferredRegister objects must register on the mod bus here (verified split constraint). The
		// Blocks register drives BlockItems/BlockEntityTypes/MenuTypes that depend on the blocks existing,
		// so it goes first.
		ModBlocksNeoForge.BLOCKS.register(modBus);
		// Entity types before items only for readability — the frame item resolves its EntityType
		// lazily inside the item RegisterEvent lambda, so no call-order dependency exists (MOD-066).
		dev.alaindustrial.registry.neoforge.ModEntitiesNeoForge.ENTITY_TYPES.register(modBus);
		ModItemsNeoForge.ITEMS.register(modBus);
		ModBlockEntitiesNeoForge.BLOCK_ENTITIES.register(modBus);
		ModMenusNeoForge.MENUS.register(modBus);
		dev.alaindustrial.registry.neoforge.ModCreativeTabNeoForge.TABS.register(modBus);
		dev.alaindustrial.registry.neoforge.ModSoundsNeoForge.SOUNDS.register(modBus);
		// MOD-085: the Enriched Uranium Torch's green flame particle type.
		dev.alaindustrial.registry.neoforge.ModParticlesNeoForge.PARTICLES.register(modBus);
		dev.alaindustrial.registry.neoforge.ModDataComponentsNeoForge.DATA_COMPONENTS.register(modBus);
		dev.alaindustrial.registry.neoforge.ModAttachmentsNeoForge.ATTACHMENTS.register(modBus);
		dev.alaindustrial.registry.neoforge.ModRecipesNeoForge.TYPES.register(modBus);
		dev.alaindustrial.registry.neoforge.ModRecipesNeoForge.SERIALIZERS.register(modBus);
		dev.alaindustrial.registry.neoforge.ModCriteriaNeoForge.TRIGGERS.register(modBus);
		// MOD-119: the alaindustrial:bonus_chest_enabled loot condition gates the bonus-chest
		// Global Loot Modifier (data/alaindustrial/loot_modifiers/bonus_chest_inject.json) on the config flag.
		dev.alaindustrial.registry.neoforge.ModLootConditionsNeoForge.LOOT_CONDITION_TYPES.register(modBus);
		// Register the alaindustrial:code gametest-instance type so the TEST_INSTANCE registry encodes cleanly
		// during the client known-packs handshake (fixes a ClassCastException that broke NeoForge world load).
		dev.alaindustrial.gametest.neoforge.NeoForgeGameTests.INSTANCE_TYPES.register(modBus);
	}

	/** Binds the registered {@code DeferredHolder}s into the loader-neutral {@code ModContent} facade. */
	private void bindContentFacade(IEventBus modBus) {
		// Bind the registered DeferredHolders into the loader-neutral ModContent facade (mirrors the Fabric
		// ModBlocks/ModItems/ModBlockEntities/ModMenus.init() calls). A DeferredHolder is a Supplier, so it is
		// assigned directly and resolves lazily after each RegisterEvent — assigning it here, before the events
		// fire, is intentional (see ModContent). Must run after .register(modBus) above.
		ModBlocksNeoForge.init();
		dev.alaindustrial.registry.neoforge.ModEntitiesNeoForge.init();
		ModItemsNeoForge.init();
		ModBlockEntitiesNeoForge.init();
		ModMenusNeoForge.init();
		dev.alaindustrial.registry.neoforge.ModSoundsNeoForge.init();
		dev.alaindustrial.registry.neoforge.ModDataComponentsNeoForge.init();
		dev.alaindustrial.registry.neoforge.ModAttachmentsNeoForge.init(); // MOD-133 player-stats store seam
		dev.alaindustrial.registry.neoforge.ModRecipesNeoForge.init();
		dev.alaindustrial.registry.neoforge.ModCriteriaNeoForge.init();
		dev.alaindustrial.registry.neoforge.ModCreativeTabEventsNeoForge.register(modBus);
	}

	/**
	 * Fails loudly at init if any {@code ModContent} handle is still unbound, mirroring the Fabric
	 * side. Both loaders bind every handle, so an unbound one is a registration bug, not an expected
	 * migration gap.
	 *
	 * <p>Crashes in BOTH dev and production (matches Fabric). Previously a shipped jar only warned:
	 * "degrading to one missing block beats refusing to load a player's world" — but the cost was
	 * asymmetry with Fabric, where the same gap crashes the game at startup. A NeoForge-only regression
	 * that adds a {@code ModContent} field but forgets the {@code ModItemsNeoForge.init()} line would
	 * ship silently broken (a {@code Supplier} that throws at first {@code .get()}, mid-gameplay) —
	 * the exact failure {@code verifyAllBound()} exists to prevent. A loud crash on a known regression
	 * is a better trade than a mid-game {@code IllegalStateException} the player cannot recover from.
	 *
	 * <p>If a graceful-degradation escape hatch ever becomes necessary again (hot-fix ship), gate it
	 * on a system property rather than the dev/prod split, so dev and prod stay symmetric by default.
	 */
	private void verifyContentBound() {
		ModContent.verifyAllBound();
	}

	/** Registers the mod-bus listeners (capabilities, S2C payload, world gametests). */
	private void registerModBusEvents(IEventBus modBus) {
		// Mod-bus events. Capability + payload registration both fire on the mod bus.
		modBus.addListener(this::registerCapabilities);
		// MOD-022 Phase 3: NeoForge payload registration (S2C Network Analyzer) — counterpart to the
		// Fabric PayloadTypeRegistry call + client receiver.
		modBus.addListener(NeoForgeNetwork::register);
		// MOD-022 — NeoForge world gametest lane. RegisterGameTestsEvent fires only when
		// GameTestHooks.isGametestEnabled() (dev/gameTestServer), so this is inert in production.
		modBus.addListener(dev.alaindustrial.gametest.neoforge.NeoForgeGameTests::register);
	}

	/** Loads {@code config/alaindustrial.json} at startup (counterpart to {@code FabricConfigLoader}). */
	private void loadConfig() {
		// Balance config: load config/alaindustrial.json at startup (counterpart to FabricConfigLoader).
		// Without this, NeoForge ignores the config file and every balance number falls back to defaults.
		NeoForgeConfigLoader.register();
	}

	/**
	 * Wires the game-bus listeners: energy-network ticking, teleport-warmup cancellation, guide-book
	 * first-join grant, per-level/server-stop network teardown, config reload parity, vanilla-bucket
	 * deposit and the {@code /ala} command.
	 */
	private void registerGameBusEvents() {
		// Game-bus wiring (counterpart to the Fabric ServerTickEvents/ServerLevelEvents/ServerLifecycleEvents
		// + CommandRegistrationCallback tail of IndustrializationFabric#onInitialize). These fire on the game
		// event bus, not the mod bus.
		//
		// Energy networks: tick every per-level NetworkManager once per server tick. WITHOUT this, cables
		// never transfer EU on NeoForge (the single most important gameplay seam). Drop a level's transient
		// state on unload and all of it on server stop so per-level networks never leak across reloads.
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
			for (ServerLevel lvl : event.getServer().getAllLevels()) {
				NetworkManager.tickAll(lvl);
				ItemNetworkManager.tickAll(lvl);
			}
			// Teleport warmups are per-player, not per-level (MOD-092).
			dev.alaindustrial.teleporter.TeleportWarmupManager.tickAll(event.getServer());
			// MOD-133: fold pending per-player stat deltas into attachments on the configured cadence.
			dev.alaindustrial.stats.PlayerStatsTracker.get().onServerTick(event.getServer());
		});
		// Teleport warmup cancellation (MOD-092). Three hooks, not two: LivingDamageEvent.Post does
		// not fire for a killing blow, and death does not disconnect the player.
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) -> {
			if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
					&& event.getHealthDamage() > 0.0f) {
				dev.alaindustrial.teleporter.TeleportWarmupManager.cancel(player,
						net.minecraft.network.chat.Component.translatable("alaindustrial.teleporter.cancelled_hurt"));
			}
		});
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) -> {
			if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
				dev.alaindustrial.teleporter.TeleportWarmupManager.cancel(player);
			}
		});
		NeoForge.EVENT_BUS.addListener(
				(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
					// MOD-133: flush this player's pending stats while still online (their tail would
					// otherwise be dropped on the next server-tick flush, after they have left).
					if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
						dev.alaindustrial.stats.PlayerStatsTracker.get().flushPlayer(serverPlayer);
					}
					dev.alaindustrial.teleporter.TeleportWarmupManager.forget(event.getEntity().getUUID());
				});
		// MOD-067: auto-give the Guide Book on first join (game-bus event; once per player).
		NeoForge.EVENT_BUS.addListener(
				(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
					if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
						dev.alaindustrial.core.guide.GuideBookGiver.giveIfNeeded(serverPlayer);
					}
				});
		NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
			if (event.getLevel() instanceof ServerLevel lvl) {
				NetworkManager.clear(lvl);
				ItemNetworkManager.clear(lvl);
			}
		});
		// MOD-133: fold every pending delta before players are saved. ServerStoppingEvent runs before the
		// player save; ServerStoppedEvent is too late and would lose the last flush window's tail.
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event) ->
				dev.alaindustrial.stats.PlayerStatsTracker.get().flush(event.getServer()));
		NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
			NetworkManager.clearAll();
			ItemNetworkManager.clearAll();
			dev.alaindustrial.stats.PlayerStatsTracker.get().clear();
		});
		// Balance-config reload parity with Fabric (MOD-100, absorbs MOD-041). OnDatapackSyncEvent fires on a
		// player join AND on /reload; getPlayer()==null is the all-players sync, i.e. /reload — the exact
		// analogue of Fabric's END_DATA_PACK_RELOAD. Guarding on null avoids re-reading on every single join.
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.OnDatapackSyncEvent event) -> {
			if (event.getPlayer() == null) {
				NeoForgeConfigLoader.reload();
			}
		});
		// MOD-077: shift-right-clicking a mod fluid tank (geothermal generator, pump) with a vanilla lava
		// bucket loads the bucket into the tank instead of spilling it. RightClickBlock fires early on both
		// sides — before vanilla's sneak-bypass runs BucketItem#useOn — so it can intercept the spill. The
		// neutral helper is shared with the Fabric UseBlockCallback registration.
		NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
			InteractionResult result = dev.alaindustrial.item.VanillaBucketDeposit.tryDeposit(
					event.getLevel(), event.getEntity(), event.getHand(), event.getHitVec());
			if (result != InteractionResult.PASS) {
				event.setCancellationResult(result);
				event.setCanceled(true);
			}
		});
		// /ala build-visibility command (version + status + net), available to everyone; the hidden
		// /ala demo subtree (MOD-058) registers only outside production (or with -Dalaindustrial.demo=true).
		NeoForge.EVENT_BUS.addListener(
				(RegisterCommandsEvent event) -> AlaCommandCommon.register(event.getDispatcher(),
						!net.neoforged.fml.loading.FMLEnvironment.isProduction()));
	}

	/**
	 * Registers each machine's per-face energy capability on the REAL {@code BlockEntityType}s. Verified
	 * pattern (neoforge-26.2.0.8-beta):
	 * {@code event.registerBlockEntity(Capabilities.Energy.BLOCK, TYPE, (be, side) -> handler)} where the
	 * provider is an {@code ICapabilityProvider<BE, Direction, EnergyHandler>}
	 * ({@code getCapability(BE, Direction)}).
	 *
	 * <p>Each machine {@code BlockEntity} extends the common {@code MachineBlockEntity}
	 * ({@code implements EnergyPortHost}), so {@code be.energyPort(side)} yields the face-scoped neutral
	 * {@code EnergyPort}; {@link BufferAsEnergyHandler#of} exposes it as a NeoForge {@code EnergyHandler}
	 * (returns {@code null} for a {@code NONE} face, which the capability system treats as "no
	 * capability here"). This is the exact NeoForge counterpart to the Fabric
	 * {@code EnergyStorage.SIDED.registerForBlockEntity((be, dir) -> PortAsEnergyStorage.of(be.energyPort(dir)), TYPE)}
	 * lines in {@code ModBlockEntities#init()}.
	 *
	 * <p><b>Fluid (MOD-028).</b> The geothermal generator and pump additionally publish their neutral
	 * {@code FluidPort} (via {@code FluidPortHost#fluidPort}) through {@code Capabilities.Fluid.BLOCK} —
	 * the exact NeoForge counterpart to the Fabric {@code FluidStorage.SIDED.registerForBlockEntity((be,
	 * dir) -> TankAsFluidStorage.of(be.fluidPort(dir)), TYPE)} lines in {@code ModBlockEntities#init()}.
	 */
	/**
	 * Every block entity that exposes an energy port. All of them extend {@link MachineBlockEntity}
	 * (generators through {@code AbstractGeneratorBlockEntity}, the cable directly), which is both a
	 * {@code BlockEntity} and an {@code EnergyPortHost} — that shared supertype is what lets the
	 * registration below be a loop instead of one hand-written pair of lines per machine.
	 *
	 * <p><b>Note:</b> this list is inline rather than shared loader-neutral one. A common/ list would
	 * route through {@code ModContent} handles, but those handles are only bound by each loader's
	 * {@code Mod*.init()} — which on NeoForge runs AFTER this class's static init (the
	 * {@code ENERGY_BLOCK_ENTITIES = ...} line resolves at class load, during
	 * {@code DeferredRegister.register(modBus)} in the constructor). Reading {@code ModContent} here
	 * would hit the throwing placeholder. So the list references the local
	 * {@code ModBlockEntitiesNeoForge} {@code DeferredHolder} fields directly. (A shared list was tried
	 * and reverted for this ordering reason: the shared list's static init read ModContent handles
	 * before the loaders bound them and crashed the runtime.)
	 *
	 * <p>A new powered machine is added here, not in the registration body: forgetting it leaves the
	 * machine silently without energy, which is exactly the failure this list is meant to make obvious.
	 */
	private static final List<Supplier<? extends BlockEntityType<? extends MachineBlockEntity>>>
			ENERGY_BLOCK_ENTITIES = List.of(
					ModBlockEntitiesNeoForge.GENERATOR,
					ModBlockEntitiesNeoForge.SOLAR_PANEL,
					ModBlockEntitiesNeoForge.MOONLIT_SOLAR_PANEL,
					ModBlockEntitiesNeoForge.DAYLIGHT_SOLAR_PANEL,
					ModBlockEntitiesNeoForge.COPPER_CABLE,
					ModBlockEntitiesNeoForge.MACERATOR,
					ModBlockEntitiesNeoForge.BATTERY_BOX,
					ModBlockEntitiesNeoForge.TELEPORTER,
					ModBlockEntitiesNeoForge.ELECTRIC_FURNACE,
					ModBlockEntitiesNeoForge.EXTRACTOR,
					ModBlockEntitiesNeoForge.COMPRESSOR,
					ModBlockEntitiesNeoForge.GEOTHERMAL_GENERATOR,
					ModBlockEntitiesNeoForge.PUMP,
					ModBlockEntitiesNeoForge.WATER_MILL,
					ModBlockEntitiesNeoForge.WIND_MILL,
					ModBlockEntitiesNeoForge.HIGH_ALTITUDE_WIND_MILL,
					ModBlockEntitiesNeoForge.STORM_WIND_MILL);

	private void registerCapabilities(RegisterCapabilitiesEvent event) {
		BlockCapability<EnergyHandler, Direction> cap = Capabilities.Energy.BLOCK;
		for (Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type : ENERGY_BLOCK_ENTITIES) {
			event.registerBlockEntity(cap, type.get(),
					(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		}

		// Only three fluid hosts, and they share no BlockEntity supertype (the fluid tank is a plain
		// BlockEntity, not a MachineBlockEntity), so these stay explicit — a generic helper carries the
		// BlockEntity & FluidPortHost bound that a wildcard list cannot express.
		BlockCapability<ResourceHandler<FluidResource>, Direction> fluidCap = Capabilities.Fluid.BLOCK;
		registerFluidPort(event, fluidCap, ModBlockEntitiesNeoForge.GEOTHERMAL_GENERATOR);
		registerFluidPort(event, fluidCap, ModBlockEntitiesNeoForge.PUMP);
		registerFluidPort(event, fluidCap, ModBlockEntitiesNeoForge.FLUID_TANK);

		// MOD-104: publish transactional, side-aware item views for mod containers. This is required
		// because a vanilla Container is not automatically a 26.2 ResourceHandler on NeoForge.
		BlockCapability<ResourceHandler<ItemResource>, Direction> itemCap = Capabilities.Item.BLOCK;
		registerItemContainer(event, itemCap, ModBlockEntitiesNeoForge.GENERATOR);
		registerItemContainer(event, itemCap, ModBlockEntitiesNeoForge.MACERATOR);
		registerItemContainer(event, itemCap, ModBlockEntitiesNeoForge.BATTERY_BOX);
		registerItemContainer(event, itemCap, ModBlockEntitiesNeoForge.ELECTRIC_FURNACE);
		registerItemContainer(event, itemCap, ModBlockEntitiesNeoForge.EXTRACTOR);
		registerItemContainer(event, itemCap, ModBlockEntitiesNeoForge.COMPRESSOR);
		registerItemContainer(event, itemCap, ModBlockEntitiesNeoForge.GEOTHERMAL_GENERATOR);
		registerItemContainer(event, itemCap, ModBlockEntitiesNeoForge.PUMP);
		registerItemContainer(event, itemCap, ModBlockEntitiesNeoForge.IRON_CHEST);
		registerItemContainer(event, itemCap, ModBlockEntitiesNeoForge.SILVER_CHEST);
		registerItemContainer(event, itemCap, ModBlockEntitiesNeoForge.GOLD_CHEST);

		// MOD-063: item-side fluid capability for the Vacuum Capsule, so other mods' pipes/tanks can fill
		// or drain a capsule sitting in a slot. One CapsuleResourceHandler per stack access, both items.
		event.registerItem(Capabilities.Fluid.ITEM,
				(stack, access) -> new dev.alaindustrial.core.neoforge.CapsuleResourceHandler(access),
				ModItemsNeoForge.VACUUM_CAPSULE.get(), ModItemsNeoForge.FILLED_VACUUM_CAPSULE.get());

		// MOD-084: item-side energy capability on the mod's powered items, so other mods' chargers can
		// fill them. Insert-only — see StackAsEnergyHandler.
		event.registerItem(Capabilities.Energy.ITEM,
				(stack, access) -> new dev.alaindustrial.core.neoforge.StackAsEnergyHandler(access),
				ModItemsNeoForge.BATTERY_POUCH.get(), ModItemsNeoForge.ENERGY_PACK.get(),
				ModItemsNeoForge.ELECTRIC_DRILL.get(), ModItemsNeoForge.ELECTROMAGNET.get());

		// MOD-084: a fake "other mod" energy item, so the gametests can prove the pack charges foreign
		// items. Dev/gametest only — inert in a shipped jar (see the class doc).
		dev.alaindustrial.gametest.neoforge.ForeignEnergyItemStandIn.register(event);
	}

	/** Carries the {@code BlockEntity & FluidPortHost} bound a wildcard list cannot express. */
	private static <T extends BlockEntity & dev.alaindustrial.core.fluid.FluidPortHost> void registerFluidPort(
			RegisterCapabilitiesEvent event, BlockCapability<ResourceHandler<FluidResource>, Direction> cap,
			Supplier<? extends BlockEntityType<T>> type) {
		event.registerBlockEntity(cap, type.get(), (be, side) -> TankAsResourceHandler.of(be.fluidPort(side)));
	}

	private static <T extends BlockEntity & net.minecraft.world.Container> void registerItemContainer(
			RegisterCapabilitiesEvent event, BlockCapability<ResourceHandler<ItemResource>, Direction> capability,
			DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type) {
		event.registerBlockEntity(capability, type.get(), ContainerAsItemResourceHandler::of);
	}
}
