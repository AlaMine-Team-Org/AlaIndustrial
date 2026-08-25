package dev.alaindustrial;

import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyPortHost;
import dev.alaindustrial.item.energy.ItemEnergyBridge;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.fluid.FluidPortHost;
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
import dev.alaindustrial.registry.BlockCapabilityRoster;
import dev.alaindustrial.registry.ContentManifest;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.neoforge.ModBlockEntitiesNeoForge;
import dev.alaindustrial.registry.neoforge.ModBlocksNeoForge;
import dev.alaindustrial.registry.neoforge.ModItemsNeoForge;
import dev.alaindustrial.registry.neoforge.ModMenusNeoForge;
import dev.alaindustrial.command.AlaCommandCommon;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.item.ItemNetworkManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * NeoForge {@code @Mod} entrypoint (MOD-022 Phase 3 scaffold). Mirrors the Fabric
 * {@code dev.alaindustrial.IndustrializationFabric} entrypoint; loader-neutral constants live in
 * {@link Industrialization} (common).
 *
 * <p><b>Verified 26.2 wiring (against neoforge-26.2.0.67):</b>
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
		dev.alaindustrial.item.fluid.ItemFluidBridge.install(new dev.alaindustrial.core.neoforge.NeoForgeItemFluidBridge());

		// MOD-022 Phase 3: install the NeoForge packet-send seam so content code dispatches through the
		// neutral NetworkDispatcher instead of PacketDistributor directly.
		NetworkDispatcher.install(new NeoForgeNetworkDispatcher());
		// MOD-391: a chest pairing up / falling back to single is a property-only state change, which
		// does NOT auto-invalidate the capability caches (only a block change does) — mirror the
		// vanilla NeoForge ChestBlockEntity.setBlockState patch through the common hook.
		dev.alaindustrial.block.entity.ChestPairHooks.CAP_INVALIDATOR = BlockEntity::invalidateCapabilities;
	}

	/** Registers every {@code DeferredRegister} on the mod bus. Must run before {@link #bindContentFacade}. */
	private void registerDeferredRegisters(IEventBus modBus) {
		// DeferredRegister objects must register on the mod bus here (verified split constraint). The
		// Blocks register drives BlockItems/BlockEntityTypes/MenuTypes that depend on the blocks existing,
		// so it goes first.
		// MOD-238: fluids + fluid types. Declared before blocks for readability; the actual safety is
		// the RegisterEvent order (FLUID before BLOCK), which lets the oil block factory resolve its
		// still fluid eagerly.
		dev.alaindustrial.registry.neoforge.ModFluidsNeoForge.FLUID_TYPES.register(modBus);
		dev.alaindustrial.registry.neoforge.ModFluidsNeoForge.FLUIDS.register(modBus);
		ModBlocksNeoForge.BLOCKS.register(modBus);
		// Entity types before items only for readability — the frame item resolves its EntityType
		// lazily inside the item RegisterEvent lambda, so no call-order dependency exists (MOD-066).
		dev.alaindustrial.registry.neoforge.ModEntitiesNeoForge.ENTITY_TYPES.register(modBus);
		ModItemsNeoForge.ITEMS.register(modBus);
		ModBlockEntitiesNeoForge.BLOCK_ENTITIES.register(modBus);
		ModMenusNeoForge.MENUS.register(modBus);
		dev.alaindustrial.registry.neoforge.ModCreativeTabNeoForge.TABS.register(modBus);
		dev.alaindustrial.registry.neoforge.ModSoundsNeoForge.SOUNDS.register(modBus);
		dev.alaindustrial.registry.neoforge.ModEffectsNeoForge.EFFECTS.register(modBus);
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
		// MOD-062: Industrialist POI + profession (frozen registries → DeferredRegister only).
		dev.alaindustrial.registry.neoforge.ModProfessionsNeoForge.POI_TYPES.register(modBus);
		dev.alaindustrial.registry.neoforge.ModProfessionsNeoForge.PROFESSIONS.register(modBus);
		// MOD-238 audit: alaindustrial:oil_lake_filter. Must be registered before datapack load —
		// the oil-lake placed features name it, and an unknown placement modifier type fails parsing.
		dev.alaindustrial.registry.neoforge.ModWorldGenNeoForge.PLACEMENT_MODIFIER_TYPES.register(modBus);
		// MOD-248: alaindustrial:oil_lake + alaindustrial:oil_geyser. Same deadline for the same
		// reason — an unknown "type" in a configured feature fails the whole file.
		dev.alaindustrial.registry.neoforge.ModWorldGenNeoForge.FEATURES.register(modBus);
		// MOD-242: the world-gametest lane (instance-type DeferredRegister, RegisterGameTestsEvent listener,
		// foreign-energy stand-in capability) lives in the `gametest` source set now — wired reflectively so
		// production, which ships without those classes, has nothing to load. See bootstrapGameTests.
		bootstrapGameTests(modBus);
	}

	/**
	 * MOD-242 — reflective hook into the gametest source set. The gametest classes
	 * ({@code dev.alaindustrial.gametest.neoforge.*}) are compiled into a separate {@code gametest}
	 * source set that dev runs load as part of the mod (see {@code neoforge/build.gradle},
	 * {@code mods} block) but the shipped jar does not contain. Production must therefore not
	 * reference them directly — {@code Class.forName} keeps the dependency one-way.
	 *
	 * <p>In a dev run the bootstrap registers the {@code alaindustrial:code} gametest-instance type
	 * (the fix for the ClassCastException in the client known-packs handshake — needed whenever
	 * gametest instances can exist, i.e. only in dev where the listener below also runs), the
	 * {@code RegisterGameTestsEvent} listener, and the MOD-084 foreign-energy stand-in capability.
	 * In production the class is absent, no gametest instance is ever registered, so the codec type
	 * is not needed either — the silent no-op is the correct behaviour, not a swallowed error.
	 */
	private static void bootstrapGameTests(IEventBus modBus) {
		try {
			Class.forName("dev.alaindustrial.gametest.neoforge.NeoForgeGameTestBootstrap")
					.getMethod("init", IEventBus.class)
					.invoke(null, modBus);
		} catch (ClassNotFoundException e) {
			// Shipped jar: the gametest source set is not packed — nothing to register.
		} catch (ReflectiveOperationException e) {
			// The class IS present (dev run) but the hook broke — that is a real wiring defect, not
			// an expected environment difference. Fail loudly instead of silently losing the test lane.
			throw new IllegalStateException("NeoForgeGameTestBootstrap present but failed to initialize", e);
		}
	}

	/** Binds the registered {@code DeferredHolder}s into the loader-neutral {@code ModContent} facade. */
	private void bindContentFacade(IEventBus modBus) {
		// Bind the registered DeferredHolders into the loader-neutral ModContent facade (mirrors the Fabric
		// ModBlocks/ModItems/ModBlockEntities/ModMenus.init() calls). A DeferredHolder is a Supplier, so it is
		// assigned directly and resolves lazily after each RegisterEvent — assigning it here, before the events
		// fire, is intentional (see ModContent). Must run after .register(modBus) above.
		dev.alaindustrial.registry.neoforge.ModFluidsNeoForge.init();
		ModBlocksNeoForge.init();
		dev.alaindustrial.registry.neoforge.ModEntitiesNeoForge.init();
		ModItemsNeoForge.init();
		ModBlockEntitiesNeoForge.init();
		ModMenusNeoForge.init();
		dev.alaindustrial.registry.neoforge.ModSoundsNeoForge.init();
		dev.alaindustrial.registry.neoforge.ModEffectsNeoForge.init(); // MOD-470 radiation effect
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

	/** Registers the mod-bus listeners (capabilities, S2C payload). */
	private void registerModBusEvents(IEventBus modBus) {
		// Mod-bus events. Capability + payload registration both fire on the mod bus.
		modBus.addListener(this::registerCapabilities);
		// MOD-022 Phase 3: NeoForge payload registration (S2C Network Analyzer) — counterpart to the
		// Fabric PayloadTypeRegistry call + client receiver.
		modBus.addListener(NeoForgeNetwork::register);
		// MOD-238: dispenser support for the filled oil bucket. DispenserBlock.registerBehavior writes a
		// plain (unsynchronised) map and mod setup runs in parallel on NeoForge, so it must go through
		// enqueueWork — the Fabric side registers it directly in its single-threaded mod init.
		modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
				event.enqueueWork(dev.alaindustrial.item.fluid.OilBucketDispenseBehavior::register));
		// MOD-022/MOD-242 — the world-gametest RegisterGameTestsEvent listener is added by
		// NeoForgeGameTestBootstrap (gametest source set, dev runs only; see bootstrapGameTests).
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
		// MOD-062: inject the Industrialist house into the vanilla village pools. ServerAboutToStart
		// fires before any level/worldgen exists — required (pool maxSize memoizes on first use).
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) ->
				dev.alaindustrial.worldgen.VillagePoolInjector.inject(event.getServer()));
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
			for (ServerLevel lvl : event.getServer().getAllLevels()) {
				NetworkManager.tickAll(lvl);
				ItemNetworkManager.tickAll(lvl);
				dev.alaindustrial.core.fluid.FluidNetworkManager.tickAll(lvl);
			}
			// Teleport warmups are per-player, not per-level (MOD-092).
			dev.alaindustrial.teleporter.TeleportWarmupManager.tickAll(event.getServer());
			// MOD-133: fold pending per-player stat deltas into attachments on the configured cadence.
			dev.alaindustrial.stats.PlayerStatsTracker.get().onServerTick(event.getServer());
			// MOD-470: per-player radiation exposure, on its own configurable cadence.
			dev.alaindustrial.core.radiation.RadiationTicker.tickAll(event.getServer());
			// MOD-148: clear any jetpack flight-glow light block whose flight ended (land, logout,
			// death, unequip) — the one cleanup path for every exit (see JetpackLight).
			dev.alaindustrial.item.wearable.JetpackLight.sweep(event.getServer(), event.getServer().getTickCount());
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
			// MOD-278: a hostile mob killed by a player personally banks one soul into their vessel.
			dev.alaindustrial.entity.SoulVesselKills.onDeath(event.getEntity(), event.getSource());
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
		// MOD-401: one sweep over everything that holds per-level state, instead of naming managers
		// here. The by-name list is what leaked: the fluid manager was never added to it, so every
		// unloaded ServerLevel stayed reachable as a key in its map for the life of the process.
		NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
			if (event.getLevel() instanceof ServerLevel lvl) {
				dev.alaindustrial.core.net.LevelStateRegistry.clearLevel(lvl);
			}
		});
		// MOD-133: fold every pending delta before players are saved. ServerStoppingEvent runs before the
		// player save; ServerStoppedEvent is too late and would lose the last flush window's tail.
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> {
			dev.alaindustrial.stats.PlayerStatsTracker.get().flush(event.getServer());
			// MOD-176: clear a mid-flight glow light before the level save — the per-tick sweep no
			// longer runs, and a saved minecraft:light block would survive as an invisible orphan.
			dev.alaindustrial.item.wearable.JetpackLight.shutdown(event.getServer());
		});
		NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
			// MOD-401: same sweep, whole-server scope — energy, fluid and item networks plus the
			// teleporter warmups/cooldowns, whichever of them the run actually loaded.
			dev.alaindustrial.core.net.LevelStateRegistry.clearAll();
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
			InteractionResult result = dev.alaindustrial.item.fluid.VanillaBucketDeposit.tryDeposit(
					event.getLevel(), event.getEntity(), event.getHand(), event.getHitVec());
			if (result != InteractionResult.PASS) {
				event.setCancellationResult(result);
				event.setCanceled(true);
			}
		});
		// MOD-238: flint and steel on an oil cell lights it. Same early seam and for the same reason:
		// oil has an empty outline shape, so the click always lands on the block BEHIND it and vanilla
		// FlintAndSteelItem#useOn then fails to place fire into the (non-air) oil block.
		NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
			InteractionResult result = dev.alaindustrial.block.OilLiquidBlock.tryLight(
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
	 * Publishes each block entity's per-face capabilities on the REAL {@code BlockEntityType}s. Verified
	 * pattern (neoforge-26.2.0.67):
	 * {@code event.registerBlockEntity(Capabilities.Energy.BLOCK, TYPE, (be, side) -> handler)} where the
	 * provider is an {@code ICapabilityProvider<BE, Direction, EnergyHandler>}
	 * ({@code getCapability(BE, Direction)}); fluid and item go through
	 * {@code Capabilities.Fluid.BLOCK} / {@code Capabilities.Item.BLOCK} the same way.
	 *
	 * <p><b>MOD-433 — derived from the manifest by interface, not named per block.</b> The three
	 * loops below replay {@link BlockCapabilityRoster}: every {@code EnergyPortHost} in
	 * {@code ContentManifest.BLOCK_ENTITIES} (minus the two pipes, see
	 * {@code BlockCapabilityRoster#NO_ENERGY_CAPABILITY}) exposes its face-scoped neutral
	 * {@code EnergyPort} through {@link BufferAsEnergyHandler#of} (which returns {@code null} for a
	 * {@code NONE} face — "no capability here"); every {@code FluidPortHost} exposes its
	 * {@code FluidPort} through {@link TankAsResourceHandler}; every {@code Container} exposes a
	 * transactional side-aware view through {@link ContainerAsItemResourceHandler} — the chests through
	 * their MOD-391 combined double-chest view. Fabric replays the same rosters in
	 * {@code ModBlockEntities#init()}.
	 *
	 * <p><b>Why the hand lists had to go.</b> A 35-entry {@code ENERGY_BLOCK_ENTITIES} list, eight fluid
	 * lines and ~30 item lines lived here, mirrored by 36 + 8 lines on Fabric, and they had drifted:
	 * the CESU was on the Fabric energy list and not on this one, so an FE mod (or any capability-based
	 * meter) saw a CESU on Fabric and nothing on NeoForge — invisible to the mod's own energy gametests
	 * because {@code NeoForgeEnergyLookup} takes the port straight off the block entity. The item list
	 * lacked the CESU, the distillation column and the three mob repellers, so the mod's OWN item pipe
	 * ({@code NeoForgeItemLookup} reads only the capability) was blind to them on this loader alone —
	 * the MOD-193 defect class, recurring. The both-loader sweep
	 * {@code BlockCapabilityParityScenarios} now asks {@code Capabilities.*.BLOCK} for every manifest
	 * entry and every face and goes red on the next omission.
	 *
	 * <p><b>Why {@code def.registeredType()} is safe here.</b> The earlier attempt at a shared list read
	 * {@code ModContent} handles at class-load time, before {@code Mod*.init()} had bound them, and
	 * crashed. The roster reads only the manifest's {@code Class} objects, and
	 * {@code RegisterCapabilitiesEvent} fires on the mod bus after the registries are frozen, so the
	 * vanilla block-entity registry already holds every type by the time these loops run.
	 *
	 * <p><b>Zero-slot containers are registered too</b> (moonlit/daylight panel, teleporter, charging
	 * station, electric heater): Fabric's {@code ItemStorage.SIDED} fallback wraps any {@code Container}
	 * regardless of size, and parity means answering the same. No misleading pipe arm follows — the
	 * common {@code ItemPipeBlock.hasEndpointCandidate} refuses a face with no slots (MOD-234) before it
	 * ever consults the capability, on both loaders.
	 */
	private void registerCapabilities(RegisterCapabilitiesEvent event) {
		for (ContentManifest.BlockEntityDef<?> def : BlockCapabilityRoster.energyHosts()) {
			registerEnergyPort(event, def);
		}
		for (ContentManifest.BlockEntityDef<?> def : BlockCapabilityRoster.fluidHosts()) {
			registerFluidPort(event, def);
		}
		for (ContentManifest.BlockEntityDef<?> def : BlockCapabilityRoster.itemContainers()) {
			// MOD-391: a double chest is ONE inventory to automation. The chest capability resolves the
			// pair on every query (combinedContainer walks to the partner), so the mod's item pipes and
			// other cap-based mods see the joined 72/90/108 slots from either half — the same view the
			// vanilla hopper gets through WorldlyContainerHolder. The pairing/unpairing cache
			// invalidation is the ChestPairHooks seam installed in installLoaderSeams().
			if (dev.alaindustrial.block.entity.AbstractChestBlockEntity.class.isAssignableFrom(def.type())) {
				registerChestContainer(event, def);
			} else {
				registerItemContainer(event, def);
			}
		}

		// MOD-063: item-side fluid capability for the Vacuum Capsule, so other mods' pipes/tanks can fill
		// or drain a capsule sitting in a slot. One CapsuleResourceHandler per stack access, both items.
		event.registerItem(Capabilities.Fluid.ITEM,
				(stack, access) -> new dev.alaindustrial.core.neoforge.CapsuleResourceHandler(access),
				ModItemsNeoForge.VACUUM_CAPSULE.get(), ModItemsNeoForge.FILLED_VACUUM_CAPSULE.get());

		// MOD-084: item-side energy capability on the mod's powered items, so other mods' chargers can
		// fill them. Insert-only — see StackAsEnergyHandler.
		// The list must cover EVERY powered item: it is hand-written and twice fell behind the item list
		// (MOD-372). ItemEnergyCapabilityScenarios.reg01EveryPoweredItemExposesCapability derives the
		// expected set from ItemEnergy.capacity(stack) > 0 and reddens the lane on the next omission.
		event.registerItem(Capabilities.Energy.ITEM,
				(stack, access) -> new dev.alaindustrial.core.neoforge.StackAsEnergyHandler(access),
				ModItemsNeoForge.BATTERY_POUCH.get(), ModItemsNeoForge.BATTERY.get(),
				ModItemsNeoForge.ENERGY_PACK.get(),
				ModItemsNeoForge.ELECTRIC_DRILL.get(), ModItemsNeoForge.ELECTRIC_DRILL_DIAMOND_TIP.get(),
				ModItemsNeoForge.ELECTRIC_CHAINSAW.get(),
				ModItemsNeoForge.ELECTRIC_CHAINSAW_DIAMOND_TIP.get(),
				ModItemsNeoForge.ELECTRIC_SHOVEL.get(),
				ModItemsNeoForge.ELECTRIC_SHOVEL_DIAMOND_TIP.get(), ModItemsNeoForge.ELECTRIC_HOE.get(),
				ModItemsNeoForge.ELECTRIC_HOE_DIAMOND_TIP.get(),
				ModItemsNeoForge.ELECTRIC_SABER.get(),
				ModItemsNeoForge.ELECTROMAGNET.get(),
				ModItemsNeoForge.JETPACK.get(),
				ModItemsNeoForge.FLUXWEAVE_HELMET.get(), ModItemsNeoForge.FLUXWEAVE_CHESTPLATE.get(),
				ModItemsNeoForge.FLUXWEAVE_LEGGINGS.get(), ModItemsNeoForge.FLUXWEAVE_BOOTS.get(),
				// EU crystal BLANKS (MOD-504) — only they have a buffer; the finished crystals hold no
				// energy and must not appear here.
				ModItemsNeoForge.ENERGY_CRYSTAL_BLANK.get(), ModItemsNeoForge.LAPOTRON_CRYSTAL_BLANK.get(),
				ModItemsNeoForge.RESONANT_CRYSTAL_BLANK.get());

		// MOD-084/MOD-242: the fake "other mod" energy item (ForeignEnergyItemStandIn) is registered by
		// NeoForgeGameTestBootstrap via its own RegisterCapabilitiesEvent listener — gametest source
		// set, dev runs only (see bootstrapGameTests).
	}

	/**
	 * Publishes {@code def}'s neutral energy port; the roster guarantees the {@code EnergyPortHost} cast.
	 *
	 * <p>MOD-448: {@code side} is nullable — a caller may ask without naming a face, and viewer mods do
	 * so for every block under the crosshair. That case is answered by {@code energyPortForLookup} (the
	 * shared contract); passing the null to {@code energyPort} threw inside the port implementation and
	 * the caller had to catch it.
	 */
	private static <T extends BlockEntity> void registerEnergyPort(RegisterCapabilitiesEvent event,
			ContentManifest.BlockEntityDef<T> def) {
		event.registerBlockEntity(Capabilities.Energy.BLOCK, def.registeredType(),
				(be, side) -> BufferAsEnergyHandler.of(((EnergyPortHost) be).energyPortForLookup(side)));
	}

	/**
	 * Publishes {@code def}'s neutral fluid port; the roster guarantees the {@code FluidPortHost} cast.
	 * The side-less query goes through {@code fluidPortForLookup}; see {@link #registerEnergyPort} (MOD-448).
	 */
	private static <T extends BlockEntity> void registerFluidPort(RegisterCapabilitiesEvent event,
			ContentManifest.BlockEntityDef<T> def) {
		event.registerBlockEntity(Capabilities.Fluid.BLOCK, def.registeredType(),
				(be, side) -> TankAsResourceHandler.of(((FluidPortHost) be).fluidPortForLookup(side)));
	}

	/**
	 * MOD-104: publishes a transactional, side-aware item view of {@code def}'s container — required
	 * because a vanilla {@code Container} is not automatically a 26.2 {@code ResourceHandler} on NeoForge.
	 * The roster guarantees the {@code Container} cast.
	 */
	private static <T extends BlockEntity> void registerItemContainer(RegisterCapabilitiesEvent event,
			ContentManifest.BlockEntityDef<T> def) {
		event.registerBlockEntity(Capabilities.Item.BLOCK, def.registeredType(),
				(be, side) -> ContainerAsItemResourceHandler.of((net.minecraft.world.Container) be, side));
	}

	/**
	 * MOD-391: like {@link #registerItemContainer}, but the handler wraps whatever the chest block's
	 * {@code combinedContainer} resolves — the block entity itself when single, the two halves as one
	 * {@code CompoundContainer} (right half first, vanilla FIRST/SECOND order — the same slot order
	 * the double's menu shows) when paired. Resolved per query, so it can never serve a stale pair;
	 * {@code BlockCapabilityCache} consumers are refreshed by the {@code ChestPairHooks} invalidation.
	 */
	private static <T extends BlockEntity> void registerChestContainer(RegisterCapabilitiesEvent event,
			ContentManifest.BlockEntityDef<T> def) {
		event.registerBlockEntity(Capabilities.Item.BLOCK, def.registeredType(), (be, side) -> {
			net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
			net.minecraft.world.Container joined =
					be.getLevel() != null && state.getBlock() instanceof dev.alaindustrial.block.AbstractModChestBlock chest
							? chest.combinedContainer(state, be.getLevel(), be.getBlockPos())
							: null;
			return ContainerAsItemResourceHandler.of(joined != null ? joined : (net.minecraft.world.Container) be, side);
		});
	}
}
