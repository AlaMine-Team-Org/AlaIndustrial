package dev.alaindustrial;

import dev.alaindustrial.command.AlaCommand;
import dev.alaindustrial.loot.BonusChest;
import dev.alaindustrial.core.energy.EnergyLookup;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.item.ItemLookup;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.item.ItemNetworkManager;
import dev.alaindustrial.core.fabric.FabricEnergyLookup;
import dev.alaindustrial.core.fabric.FabricEnergyTransactions;
import dev.alaindustrial.core.fabric.FabricFluidLookup;
import dev.alaindustrial.core.fabric.FabricItemEnergyBridge;
import dev.alaindustrial.core.fabric.FabricItemFluidBridge;
import dev.alaindustrial.core.fabric.FabricItemLookup;
import dev.alaindustrial.item.ItemEnergyBridge;
import dev.alaindustrial.item.ItemFluidBridge;
import dev.alaindustrial.network.NetworkAnalyzerPayload;
import dev.alaindustrial.network.NetworkDispatcher;
import dev.alaindustrial.network.fabric.FabricNetworkDispatcher;
import dev.alaindustrial.registry.ModBlockEntities;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModCriteria;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.registry.ModItems;
import dev.alaindustrial.registry.ModMenus;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.ModSounds;
import dev.alaindustrial.registry.ModWorldGen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import java.util.function.Supplier;

/**
 * Fabric {@code ModInitializer} entrypoint. Loader-neutral constants/helpers live in
 * {@link Industrialization} (common); this class only wires the Fabric side: the balance config
 * loader, the {@code Mod*Registry} classes, the S2C payload type, and the server-tick / lifecycle
 * hooks that drive per-level energy networks.
 *
 * <p>MOD-022 Phase 3: networking payload-type registration and the server-lifecycle wiring below
 * are Fabric-only seams; NeoForge registers the same payload via {@code RegisterPayloadHandlersEvent}
 * and drives ticking through its own event bus.
 *
 * <p>MOD-137: {@code onInitialize()} is a table of contents — each step is a named private method,
 * called in the same order the statements ran before. The ordering is load-bearing (data components
 * before content, item capabilities after {@code ModItems}, {@code verifyAllBound} after every
 * {@code Mod*.init()}); do not reorder the calls below.
 */
public class IndustrializationFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		installLoaderSeams();
		loadConfig();
		registerDataComponents();
		registerParticles();
		registerContent();
		registerSounds();
		verifyContentBound();
		dev.alaindustrial.stats.fabric.FabricPlayerStats.init(); // MOD-133 player-stats attachment + store seam
		registerNetworkPayloads();
		registerServerLifecycle();
		registerGameplayHooks();
		registerCommands();

		Industrialization.LOGGER.info("Industrialization initialized.");
	}

	/**
	 * Installs the Fabric loader seams (energy/fluid/item lookups, item-energy/item-fluid bridges,
	 * packet dispatcher) so common transport/content code stays loader-neutral.
	 */
	private void installLoaderSeams() {
		// MOD-022 Phase 2: install the Fabric energy seams (transaction opener + capability lookup) so
		// the common transport code can open transactions and resolve per-face ports without importing
		// Team Reborn / Fabric Transfer types.
		EnergyTransactions.install(new FabricEnergyTransactions());
		EnergyLookup.install(new FabricEnergyLookup());
		// MOD-028: install the Fabric fluid lookup seam (same seam shape as energy) so common fluid
		// content (the pump) can resolve a neighbour's FluidPort without importing Fabric Transfer types.
		FluidLookup.install(new FabricFluidLookup());
		// MOD-104: item-pipe endpoint lookup. The common pipe graph stays loader-neutral; this
		// adapter resolves the neighbouring inventory through Fabric Transfer API.
		ItemLookup.install(new FabricItemLookup());
		// MOD-084: install the item-energy bridge seam, so the worn Energy Pack can charge other mods'
		// powered items through EnergyStorage.ITEM without common code importing Team Reborn types.
		ItemEnergyBridge.install(new FabricItemEnergyBridge());
		// MOD-107: install the item-fluid bridge seam, so a machine's own slots can exchange a bucket with
		// whatever fluid container sits in them — vanilla bucket, our capsule, or another mod's cell — via
		// FluidStorage.ITEM, without common code importing Fabric Transfer types.
		ItemFluidBridge.install(new FabricItemFluidBridge());
		// MOD-063: item-side fluid capability for the Vacuum Capsule, so other mods' pipes/tanks can fill
		// or drain a capsule sitting in a slot. Registered after ModItems.init() below (needs the items).

		// MOD-022 Phase 3: install the Fabric packet-send seam so content code (e.g. NetworkAnalyzerItem)
		// dispatches through the neutral NetworkDispatcher instead of ServerPlayNetworking directly.
		NetworkDispatcher.install(new FabricNetworkDispatcher());
	}

	/** Loads {@code config/alaindustrial.json} (balance numbers) at startup. */
	private void loadConfig() {
		FabricConfigLoader.register();
	}

	/**
	 * Binds the neutral data-component handles via eager registration (Fabric keeps the
	 * DATA_COMPONENT_TYPE registry writable during init). Must run before {@link #registerContent()}
	 * so item/loot code sees them.
	 */
	private void registerDataComponents() {
		// Data components: Fabric keeps the DATA_COMPONENT_TYPE registry writable during init, so bind the
		// neutral handles with eager registration (NeoForge uses a DeferredRegister — see
		// ModDataComponentsNeoForge). Must run before ModItems/ModBlocks so item/loot code sees them.
		// registerDataComponent eagerly registers the type and returns the Supplier the handle expects.
		ModDataComponents.STORED_ENERGY = registerDataComponent(
				ModDataComponents.STORED_ENERGY_ID, ModDataComponents.createStoredEnergy());
		ModDataComponents.NETWORK_SCAN = registerDataComponent(
				ModDataComponents.NETWORK_SCAN_ID, ModDataComponents.createNetworkScan());
		ModDataComponents.NETWORK_ANALYZER_MODE = registerDataComponent(
				ModDataComponents.NETWORK_ANALYZER_MODE_ID, ModDataComponents.createNetworkAnalyzerMode());
		ModDataComponents.POUCH_ENERGY = registerDataComponent(
				ModDataComponents.POUCH_ENERGY_ID, ModDataComponents.createPouchEnergy());
		ModDataComponents.POUCH_CONTENTS = registerDataComponent(
				ModDataComponents.POUCH_CONTENTS_ID, ModDataComponents.createPouchContents());
		ModDataComponents.CAPSULE_FLUID = registerDataComponent(
				ModDataComponents.CAPSULE_FLUID_ID, ModDataComponents.createCapsuleFluid());
		ModDataComponents.FLUID_TANK_CONTENTS = registerDataComponent(
				ModDataComponents.FLUID_TANK_CONTENTS_ID, ModDataComponents.createFluidTankContents());
		ModDataComponents.TELEPORTER_PRIVATE = registerDataComponent(
				ModDataComponents.TELEPORTER_PRIVATE_ID, ModDataComponents.createTeleporterPrivate());
		ModDataComponents.MAGNET_ENABLED = registerDataComponent(
				ModDataComponents.MAGNET_ENABLED_ID, ModDataComponents.createMagnetEnabled());
		ModDataComponents.TELEPORTER_OWNER = registerDataComponent(
				ModDataComponents.TELEPORTER_OWNER_ID, ModDataComponents.createTeleporterOwner());
		ModDataComponents.TELEPORTER_POINTS = registerDataComponent(
				ModDataComponents.TELEPORTER_POINTS_ID, ModDataComponents.createTeleporterPoints());
	}

	/**
	 * Eagerly registers a data-component {@code type} under {@code id} in the vanilla
	 * DATA_COMPONENT_TYPE registry (writable during Fabric init) and returns the {@link Supplier} the
	 * neutral {@code ModDataComponents} handle expects (MOD-141). The generic {@code T} is inferred from
	 * the {@code create*()} factory's return type, so each call is a one-liner.
	 */
	private static <T> Supplier<DataComponentType<T>> registerDataComponent(Identifier id, DataComponentType<T> type) {
		DataComponentType<T> registered = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id, type);
		return () -> registered;
	}

	/** Publishes the Enriched Uranium Torch's green flame particle type (MOD-085). */
	private void registerParticles() {
		// MOD-085: publish the Enriched Uranium Torch's green flame (an eager object in the neutral
		// ModParticles) into the PARTICLE_TYPE registry for networking/spawning. Fabric keeps the registry
		// writable during init, so register eagerly; the torch block already reads the object directly.
		net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE,
				dev.alaindustrial.registry.ModParticles.ENRICHED_URANIUM_FLAME_ID,
				dev.alaindustrial.registry.ModParticles.ENRICHED_URANIUM_FLAME);
	}

	/**
	 * Eagerly registers all content (blocks, block entities, menus, entities, items, recipes,
	 * criteria, worldgen) plus the item-side fluid/energy capabilities that need the items to exist.
	 */
	private void registerContent() {
		ModBlocks.init();
		ModBlockEntities.init();
		ModMenus.init();
		// Entity types before items: the stock-display-frame item's constructor takes the resolved
		// EntityType (MOD-066).
		dev.alaindustrial.registry.ModEntities.init();
		ModItems.init();
		// MOD-063: capsule item fluid capability (needs the items registered just above).
		dev.alaindustrial.core.fabric.CapsuleItemFluidStorage.register();
		// MOD-084: item energy capability on the mod's powered items, so other mods' chargers can fill
		// them (same ordering reason as the capsule — the items must exist first).
		dev.alaindustrial.core.fabric.StackAsEnergyStorage.register();
		ModRecipes.init();
		ModCriteria.init();
		ModWorldGen.init();
		// MOD-062: villager profession + its POI (needs the workbench block registered by ModBlocks
		// above). Fabric keeps both registries writable during init, so this is eager like the rest.
		registerVillagerProfession();
	}

	/**
	 * Registers the Industrialist POI + profession (MOD-062). PoiHelper both registers the PoiType
	 * and fills the internal blockstate→POI map the acquisition system queries (a bare
	 * Registry.register would leave that map empty — verified 26.2). The profession record itself is
	 * loader-neutral ({@link dev.alaindustrial.registry.ModProfessions#createIndustrialist()}).
	 */
	private void registerVillagerProfession() {
		net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper.register(
				dev.alaindustrial.registry.ModProfessions.INDUSTRIALIST_POI.identifier(),
				dev.alaindustrial.registry.ModProfessions.POI_MAX_TICKETS,
				dev.alaindustrial.registry.ModProfessions.POI_VALID_RANGE,
				ModBlocks.INDUSTRIAL_WORKBENCH);
		Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION,
				dev.alaindustrial.registry.ModProfessions.INDUSTRIALIST,
				dev.alaindustrial.registry.ModProfessions.createIndustrialist());
	}

	/** Eagerly registers the mod's sound events (Fabric keeps the SOUND_EVENT registry writable). */
	private void registerSounds() {
		// Sound: Fabric keeps the SOUND_EVENT registry writable during init, so bind the neutral handle
		// with an eager registration (NeoForge uses a DeferredRegister instead — see ModSoundsNeoForge).
		// registerSound eagerly registers the event and returns the Supplier the handle expects.
		ModSounds.MACERATOR_GRIND = registerSound(
				ModSounds.MACERATOR_GRIND_ID, ModSounds.createMaceratorGrind());
		ModSounds.GENERATOR_HUM = registerSound(
				ModSounds.GENERATOR_HUM_ID, ModSounds.createGeneratorHum());
		ModSounds.ELECTRIC_FURNACE_HUM = registerSound(
				ModSounds.ELECTRIC_FURNACE_HUM_ID, ModSounds.createElectricFurnaceHum());
		ModSounds.SOLAR_PANEL_HUM = registerSound(
				ModSounds.SOLAR_PANEL_HUM_ID, ModSounds.createSolarPanelHum());
		ModSounds.IRON_CHEST_OPEN = registerSound(
				ModSounds.IRON_CHEST_OPEN_ID, ModSounds.createIronChestOpen());
		ModSounds.IRON_CHEST_CLOSE = registerSound(
				ModSounds.IRON_CHEST_CLOSE_ID, ModSounds.createIronChestClose());
		ModSounds.SCYTHE_SWING = registerSound(
				ModSounds.SCYTHE_SWING_ID, ModSounds.createScytheSwing());
	}

	/**
	 * Eagerly registers a sound {@code event} under {@code id} in the vanilla SOUND_EVENT registry
	 * (writable during Fabric init) and returns the {@link Supplier} the neutral {@code ModSounds}
	 * handle expects (MOD-141).
	 */
	private static Supplier<SoundEvent> registerSound(Identifier id, SoundEvent event) {
		SoundEvent registered = Registry.register(BuiltInRegistries.SOUND_EVENT, id, event);
		return () -> registered;
	}

	/** Fails loudly at init if any {@code ModContent} handle was declared but never bound. */
	private void verifyContentBound() {
		// Every ModContent handle must be bound by the Mod*.init() calls above (Fabric registers all content
		// eagerly). Fail loudly at init if any handle was added to ModContent but forgotten in a registry's
		// init() — instead of a silent throwing placeholder that only surfaces mid-gameplay at first .get().
		ModContent.verifyAllBound();
	}

	/** Registers the S2C/C2S payload types (and the one C2S receiver) used by the mod. */
	private void registerNetworkPayloads() {
		// S2C payload for the Network Analyzer item (MOD-016). MOD-022 Phase 3: the payload record + codec
		// live in common (dev.alaindustrial.network.NetworkAnalyzerPayload); only the type registration and
		// receiver are loader-side (no neutral form). Fabric registers the type here via PayloadTypeRegistry
		// and the client receiver in NetworkVisualizationClient; NeoForge does both through
		// RegisterPayloadHandlersEvent. Sending is neutral via NetworkDispatcher (installed above).
		PayloadTypeRegistry.clientboundPlay().register(NetworkAnalyzerPayload.TYPE, NetworkAnalyzerPayload.CODEC);
		// Teleport screen-fade level (MOD-106) — sent every tick of a jump's last second; the client
		// clears itself when the levels stop, so a cancel needs no packet of its own.
		PayloadTypeRegistry.clientboundPlay().register(
				dev.alaindustrial.network.TeleportFadePayload.TYPE,
				dev.alaindustrial.network.TeleportFadePayload.CODEC);
		// Why a jump was refused (MOD-093) — shown inside the remote's screen, which covers the action
		// bar the refusal would otherwise land on.
		PayloadTypeRegistry.clientboundPlay().register(
				dev.alaindustrial.network.TeleportNoticePayload.TYPE,
				dev.alaindustrial.network.TeleportNoticePayload.CODEC);
		// The mod's first C2S payload (MOD-093): renaming a teleport point. Every other button on that
		// screen rides vanilla's container-button packet, which needs no registration — only a name,
		// being a string, needs a payload of our own.
		PayloadTypeRegistry.serverboundPlay().register(
				dev.alaindustrial.network.TeleportRenamePayload.TYPE,
				dev.alaindustrial.network.TeleportRenamePayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
				dev.alaindustrial.network.TeleportRenamePayload.TYPE,
				(payload, context) -> context.server().execute(
						() -> dev.alaindustrial.network.TeleportRenamePayload.handle(payload, context.player())));
	}

	/**
	 * Wires the server-lifecycle hooks that drive per-level energy networks and per-player teleport
	 * warmups (tick / level-unload / server-stop), plus the teleport-warmup cancellation listeners and
	 * the guide-book first-join grant.
	 */
	private void registerServerLifecycle() {
		// Energy networks: tick every per-level NetworkManager once per server tick; drop a level's
		// transient state when that level unloads and all of it on server stop, so per-level networks
		// never leak across dimension or world reloads.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (net.minecraft.server.level.ServerLevel lvl : server.getAllLevels()) {
				NetworkManager.tickAll(lvl);
				ItemNetworkManager.tickAll(lvl);
			}
			// Teleport warmups are per-player, not per-level, so they tick once per server tick
			// (MOD-092) rather than once per level.
			dev.alaindustrial.teleporter.TeleportWarmupManager.tickAll(server);
			// MOD-133: fold pending per-player stat deltas into attachments on the configured cadence.
			dev.alaindustrial.stats.PlayerStatsTracker.get().onServerTick(server);
			// MOD-148: clear any jetpack flight-glow light block whose flight ended (land, logout,
			// death, unequip) — the one cleanup path for every exit (see JetpackLight).
			dev.alaindustrial.item.JetpackLight.sweep(server, server.getTickCount());
		});
		// Teleport warmup cancellation (MOD-092) — the mod's first player-event listeners. Three
		// separate hooks are needed, not two: AFTER_DAMAGE does NOT fire for a killing blow, and
		// death does not disconnect the player, so damage/death/disconnect each need their own.
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register(
				(entity, source, baseDamage, damageTaken, blocked) -> {
					if (entity instanceof net.minecraft.server.level.ServerPlayer player && damageTaken > 0.0f) {
						dev.alaindustrial.teleporter.TeleportWarmupManager.cancel(player,
								net.minecraft.network.chat.Component.translatable(
										"alaindustrial.teleporter.cancelled_hurt"));
					}
				});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
				dev.alaindustrial.teleporter.TeleportWarmupManager.cancel(player);
			}
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			// MOD-133: flush this player's pending stats while they are still online (their tail would
			// otherwise be dropped on the next server-tick flush, which runs after they have left).
			dev.alaindustrial.stats.PlayerStatsTracker.get().flushPlayer(handler.player);
			dev.alaindustrial.teleporter.TeleportWarmupManager.forget(handler.player.getUUID());
		});
		// MOD-067: auto-give the Guide Book on first join (once per player; SavedData ledger).
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				dev.alaindustrial.core.guide.GuideBookGiver.giveIfNeeded(handler.player));
		ServerLevelEvents.UNLOAD.register((server, level) -> {
			NetworkManager.clear(level);
			ItemNetworkManager.clear(level);
		});
		// MOD-062: inject the Industrialist house into the vanilla village pools. SERVER_STARTING
		// fires before any level/worldgen exists — required (pool maxSize memoizes on first use).
		ServerLifecycleEvents.SERVER_STARTING.register(
				dev.alaindustrial.worldgen.VillagePoolInjector::inject);
		// MOD-133: fold every pending delta before the world saves players. STOPPING runs before
		// PlayerList#saveAll; STOPPED would be too late and lose the last flush window's tail.
		ServerLifecycleEvents.SERVER_STOPPING.register(server ->
				dev.alaindustrial.stats.PlayerStatsTracker.get().flush(server));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			NetworkManager.clearAll();
			ItemNetworkManager.clearAll();
			dev.alaindustrial.stats.PlayerStatsTracker.get().clear();
		});
	}

	/**
	 * Registers gameplay-interaction hooks: vanilla-bucket-into-mod-tank deposit (MOD-077) and the
	 * bonus-chest starter-item loot injection (MOD-119).
	 */
	private void registerGameplayHooks() {
		// MOD-077: shift-right-clicking a mod fluid tank (geothermal generator, pump) with a vanilla lava
		// bucket loads the bucket into the tank instead of spilling it. UseBlockCallback fires early on both
		// sides — before vanilla's sneak-bypass runs BucketItem#useOn — so it can intercept the spill.
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				dev.alaindustrial.item.VanillaBucketDeposit.tryDeposit(level, player, hand, hit));

		// MOD-119: inject the mod's starter items into the vanilla bonus chest. Adds one pool that
		// references the shared sub-table alaindustrial:inject/bonus_chest (item list + balance live there);
		// vanilla pools are untouched. Gated on Config.bonusChestEnabled here (NeoForge gates the same flag
		// via the alaindustrial:bonus_chest_enabled loot condition on its Global Loot Modifier). source.isBuiltin()
		// skips user datapacks that replace the bonus chest, respecting their override.
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (source.isBuiltin()
					&& key == BuiltInLootTables.SPAWN_BONUS_CHEST
					&& Config.bonusChestEnabled) {
				tableBuilder.withPool(LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1.0f))
						.add(NestedLootTable.lootTableReference(BonusChest.INJECT_TABLE)));
			}
		});
	}

	/** Registers the {@code /ala} command (version + status), available to everyone. */
	private void registerCommands() {
		// /ala build-visibility command (version + status), available to everyone.
		AlaCommand.register();
	}
}
