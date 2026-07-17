package dev.alaindustrial;

import dev.alaindustrial.command.AlaCommand;
import dev.alaindustrial.core.EnergyLookup;
import dev.alaindustrial.core.EnergyTransactions;
import dev.alaindustrial.core.FluidLookup;
import dev.alaindustrial.core.ItemLookup;
import dev.alaindustrial.core.NetworkManager;
import dev.alaindustrial.core.ItemNetworkManager;
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
import dev.alaindustrial.registry.ModItems;
import dev.alaindustrial.registry.ModMenus;
import dev.alaindustrial.registry.ModRecipes;
import dev.alaindustrial.registry.ModWorldGen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Fabric {@code ModInitializer} entrypoint. Loader-neutral constants/helpers live in
 * {@link Industrialization} (common); this class only wires the Fabric side: the balance config
 * loader, the {@code Mod*Registry} classes, the S2C payload type, and the server-tick / lifecycle
 * hooks that drive per-level energy networks.
 *
 * <p>MOD-022 Phase 3: networking payload-type registration and the server-lifecycle wiring below
 * are Fabric-only seams; NeoForge registers the same payload via {@code RegisterPayloadHandlersEvent}
 * and drives ticking through its own event bus.
 */
public class IndustrializationFabric implements ModInitializer {
	@Override
	public void onInitialize() {
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

		FabricConfigLoader.register();
		// Data components: Fabric keeps the DATA_COMPONENT_TYPE registry writable during init, so bind the
		// neutral handles with eager registration (NeoForge uses a DeferredRegister — see
		// ModDataComponentsNeoForge). Must run before ModItems/ModBlocks so item/loot code sees them.
		net.minecraft.core.component.DataComponentType<Long> storedEnergy = net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE,
				dev.alaindustrial.registry.ModDataComponents.STORED_ENERGY_ID,
				dev.alaindustrial.registry.ModDataComponents.createStoredEnergy());
		dev.alaindustrial.registry.ModDataComponents.STORED_ENERGY = () -> storedEnergy;
		net.minecraft.core.component.DataComponentType<dev.alaindustrial.item.NetworkScanData> networkScan =
				net.minecraft.core.Registry.register(
						net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE,
						dev.alaindustrial.registry.ModDataComponents.NETWORK_SCAN_ID,
						dev.alaindustrial.registry.ModDataComponents.createNetworkScan());
		dev.alaindustrial.registry.ModDataComponents.NETWORK_SCAN = () -> networkScan;
		net.minecraft.core.component.DataComponentType<dev.alaindustrial.item.AnalyzerMode> networkAnalyzerMode =
				net.minecraft.core.Registry.register(
						net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE,
						dev.alaindustrial.registry.ModDataComponents.NETWORK_ANALYZER_MODE_ID,
						dev.alaindustrial.registry.ModDataComponents.createNetworkAnalyzerMode());
		dev.alaindustrial.registry.ModDataComponents.NETWORK_ANALYZER_MODE = () -> networkAnalyzerMode;
		net.minecraft.core.component.DataComponentType<Long> pouchEnergy = net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE,
				dev.alaindustrial.registry.ModDataComponents.POUCH_ENERGY_ID,
				dev.alaindustrial.registry.ModDataComponents.createPouchEnergy());
		dev.alaindustrial.registry.ModDataComponents.POUCH_ENERGY = () -> pouchEnergy;
		net.minecraft.core.component.DataComponentType<dev.alaindustrial.item.PouchContents> pouchContents =
				net.minecraft.core.Registry.register(
						net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE,
						dev.alaindustrial.registry.ModDataComponents.POUCH_CONTENTS_ID,
						dev.alaindustrial.registry.ModDataComponents.createPouchContents());
		dev.alaindustrial.registry.ModDataComponents.POUCH_CONTENTS = () -> pouchContents;
		net.minecraft.core.component.DataComponentType<net.minecraft.core.Holder<net.minecraft.world.level.material.Fluid>> capsuleFluid =
				net.minecraft.core.Registry.register(
						net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE,
						dev.alaindustrial.registry.ModDataComponents.CAPSULE_FLUID_ID,
						dev.alaindustrial.registry.ModDataComponents.createCapsuleFluid());
		dev.alaindustrial.registry.ModDataComponents.CAPSULE_FLUID = () -> capsuleFluid;
		net.minecraft.core.component.DataComponentType<dev.alaindustrial.item.FluidTankContents> fluidTankContents =
				net.minecraft.core.Registry.register(
						net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE,
						dev.alaindustrial.registry.ModDataComponents.FLUID_TANK_CONTENTS_ID,
						dev.alaindustrial.registry.ModDataComponents.createFluidTankContents());
		dev.alaindustrial.registry.ModDataComponents.FLUID_TANK_CONTENTS = () -> fluidTankContents;
		net.minecraft.core.component.DataComponentType<Boolean> teleporterPrivate = net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE,
				dev.alaindustrial.registry.ModDataComponents.TELEPORTER_PRIVATE_ID,
				dev.alaindustrial.registry.ModDataComponents.createTeleporterPrivate());
		dev.alaindustrial.registry.ModDataComponents.TELEPORTER_PRIVATE = () -> teleporterPrivate;
		net.minecraft.core.component.DataComponentType<java.util.UUID> teleporterOwner = net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE,
				dev.alaindustrial.registry.ModDataComponents.TELEPORTER_OWNER_ID,
				dev.alaindustrial.registry.ModDataComponents.createTeleporterOwner());
		dev.alaindustrial.registry.ModDataComponents.TELEPORTER_OWNER = () -> teleporterOwner;
		net.minecraft.core.component.DataComponentType<dev.alaindustrial.item.TeleportPoints> teleporterPoints =
				net.minecraft.core.Registry.register(
						net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE,
						dev.alaindustrial.registry.ModDataComponents.TELEPORTER_POINTS_ID,
						dev.alaindustrial.registry.ModDataComponents.createTeleporterPoints());
		dev.alaindustrial.registry.ModDataComponents.TELEPORTER_POINTS = () -> teleporterPoints;
		// MOD-085: publish the Enriched Uranium Torch's green flame (an eager object in the neutral
		// ModParticles) into the PARTICLE_TYPE registry for networking/spawning. Fabric keeps the registry
		// writable during init, so register eagerly; the torch block already reads the object directly.
		net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE,
				dev.alaindustrial.registry.ModParticles.ENRICHED_URANIUM_FLAME_ID,
				dev.alaindustrial.registry.ModParticles.ENRICHED_URANIUM_FLAME);
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
		// Sound: Fabric keeps the SOUND_EVENT registry writable during init, so bind the neutral handle
		// with an eager registration (NeoForge uses a DeferredRegister instead — see ModSoundsNeoForge).
		net.minecraft.sounds.SoundEvent maceratorGrind = net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT,
				dev.alaindustrial.registry.ModSounds.MACERATOR_GRIND_ID,
				dev.alaindustrial.registry.ModSounds.createMaceratorGrind());
		dev.alaindustrial.registry.ModSounds.MACERATOR_GRIND = () -> maceratorGrind;
		net.minecraft.sounds.SoundEvent generatorHum = net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT,
				dev.alaindustrial.registry.ModSounds.GENERATOR_HUM_ID,
				dev.alaindustrial.registry.ModSounds.createGeneratorHum());
		dev.alaindustrial.registry.ModSounds.GENERATOR_HUM = () -> generatorHum;
		net.minecraft.sounds.SoundEvent electricFurnaceHum = net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT,
				dev.alaindustrial.registry.ModSounds.ELECTRIC_FURNACE_HUM_ID,
				dev.alaindustrial.registry.ModSounds.createElectricFurnaceHum());
		dev.alaindustrial.registry.ModSounds.ELECTRIC_FURNACE_HUM = () -> electricFurnaceHum;
		net.minecraft.sounds.SoundEvent solarPanelHum = net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT,
				dev.alaindustrial.registry.ModSounds.SOLAR_PANEL_HUM_ID,
				dev.alaindustrial.registry.ModSounds.createSolarPanelHum());
		dev.alaindustrial.registry.ModSounds.SOLAR_PANEL_HUM = () -> solarPanelHum;
		net.minecraft.sounds.SoundEvent ironChestOpen = net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT,
				dev.alaindustrial.registry.ModSounds.IRON_CHEST_OPEN_ID,
				dev.alaindustrial.registry.ModSounds.createIronChestOpen());
		dev.alaindustrial.registry.ModSounds.IRON_CHEST_OPEN = () -> ironChestOpen;
		net.minecraft.sounds.SoundEvent ironChestClose = net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT,
				dev.alaindustrial.registry.ModSounds.IRON_CHEST_CLOSE_ID,
				dev.alaindustrial.registry.ModSounds.createIronChestClose());
		dev.alaindustrial.registry.ModSounds.IRON_CHEST_CLOSE = () -> ironChestClose;
		net.minecraft.sounds.SoundEvent scytheSwing = net.minecraft.core.Registry.register(
				net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT,
				dev.alaindustrial.registry.ModSounds.SCYTHE_SWING_ID,
				dev.alaindustrial.registry.ModSounds.createScytheSwing());
		dev.alaindustrial.registry.ModSounds.SCYTHE_SWING = () -> scytheSwing;

		// Every ModContent handle must be bound by the Mod*.init() calls above (Fabric registers all content
		// eagerly). Fail loudly at init if any handle was added to ModContent but forgotten in a registry's
		// init() — instead of a silent throwing placeholder that only surfaces mid-gameplay at first .get().
		ModContent.verifyAllBound();

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
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				dev.alaindustrial.teleporter.TeleportWarmupManager.forget(handler.player.getUUID()));
		ServerLevelEvents.UNLOAD.register((server, level) -> {
			NetworkManager.clear(level);
			ItemNetworkManager.clear(level);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			NetworkManager.clearAll();
			ItemNetworkManager.clearAll();
		});

		// MOD-077: shift-right-clicking a mod fluid tank (geothermal generator, pump) with a vanilla lava
		// bucket loads the bucket into the tank instead of spilling it. UseBlockCallback fires early on both
		// sides — before vanilla's sneak-bypass runs BucketItem#useOn — so it can intercept the spill.
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				dev.alaindustrial.item.VanillaBucketDeposit.tryDeposit(level, player, hand, hit));

		// /ala build-visibility command (version + status), available to everyone.
		AlaCommand.register();

		Industrialization.LOGGER.info("Industrialization initialized.");
	}
}
