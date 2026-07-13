package dev.alaindustrial;

import dev.alaindustrial.core.EnergyLookup;
import dev.alaindustrial.core.EnergyTransactions;
import dev.alaindustrial.core.FluidLookup;
import dev.alaindustrial.core.neoforge.BufferAsEnergyHandler;
import dev.alaindustrial.core.neoforge.NeoForgeEnergyLookup;
import dev.alaindustrial.core.neoforge.NeoForgeEnergyTransactions;
import dev.alaindustrial.core.neoforge.NeoForgeFluidLookup;
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
import dev.alaindustrial.command.AlaCommandCommon;
import dev.alaindustrial.core.NetworkManager;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

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

	public IndustrializationNeoForge(IEventBus modBus) {
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

		// MOD-022 Phase 3: install the NeoForge packet-send seam so content code dispatches through the
		// neutral NetworkDispatcher instead of PacketDistributor directly.
		NetworkDispatcher.install(new NeoForgeNetworkDispatcher());

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
		dev.alaindustrial.registry.neoforge.ModDataComponentsNeoForge.DATA_COMPONENTS.register(modBus);
		dev.alaindustrial.registry.neoforge.ModRecipesNeoForge.TYPES.register(modBus);
		dev.alaindustrial.registry.neoforge.ModRecipesNeoForge.SERIALIZERS.register(modBus);
		dev.alaindustrial.registry.neoforge.ModCriteriaNeoForge.TRIGGERS.register(modBus);
		// Register the alaindustrial:code gametest-instance type so the TEST_INSTANCE registry encodes cleanly
		// during the client known-packs handshake (fixes a ClassCastException that broke NeoForge world load).
		dev.alaindustrial.gametest.neoforge.NeoForgeGameTests.INSTANCE_TYPES.register(modBus);

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
		dev.alaindustrial.registry.neoforge.ModRecipesNeoForge.init();
		dev.alaindustrial.registry.neoforge.ModCriteriaNeoForge.init();
		dev.alaindustrial.registry.neoforge.ModCreativeTabEventsNeoForge.register(modBus);

		// Verify the facade is bound. NeoForge is still mid-migration (Phase 4 populates every registry), so
		// most handles remain on their ModContent placeholder — report the gap loudly instead of aborting load,
		// unlike the Fabric side (which binds everything and calls the strict ModContent.verifyAllBound()). Flip
		// this to ModContent.verifyAllBound() once Phase 4 binds all handles on NeoForge.
		List<String> unbound = ModContent.unboundHandles();
		if (!unbound.isEmpty()) {
			Industrialization.LOGGER.warn(
					"ModContent has {} handle(s) not yet bound on NeoForge (Phase 4 per-machine migration "
							+ "pending): {}", unbound.size(), unbound);
		}

		// Mod-bus events. Capability + payload registration both fire on the mod bus.
		modBus.addListener(this::registerCapabilities);
		// MOD-022 Phase 3: NeoForge payload registration (S2C Network Analyzer) — counterpart to the
		// Fabric PayloadTypeRegistry call + client receiver.
		modBus.addListener(NeoForgeNetwork::register);
		// MOD-022 — NeoForge world gametest lane. RegisterGameTestsEvent fires only when
		// GameTestHooks.isGametestEnabled() (dev/gameTestServer), so this is inert in production.
		modBus.addListener(dev.alaindustrial.gametest.neoforge.NeoForgeGameTests::register);

		// Balance config: load config/alaindustrial.json at startup (counterpart to FabricConfigLoader).
		// Without this, NeoForge ignores the config file and every balance number falls back to defaults.
		NeoForgeConfigLoader.register();

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
			}
		});
		NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
			if (event.getLevel() instanceof ServerLevel lvl) {
				NetworkManager.clear(lvl);
			}
		});
		NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> NetworkManager.clearAll());
		// /ala build-visibility command (version + status + net), available to everyone; the hidden
		// /ala demo subtree (MOD-058) registers only outside production (or with -Dalaindustrial.demo=true).
		NeoForge.EVENT_BUS.addListener(
				(RegisterCommandsEvent event) -> AlaCommandCommon.register(event.getDispatcher(),
						!net.neoforged.fml.loading.FMLEnvironment.isProduction()));

		Industrialization.LOGGER.info("Industrialization (NeoForge) initialized.");
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
	private void registerCapabilities(RegisterCapabilitiesEvent event) {
		BlockCapability<EnergyHandler, Direction> cap = Capabilities.Energy.BLOCK;
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.GENERATOR.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.SOLAR_PANEL.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.MOONLIT_SOLAR_PANEL.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.DAYLIGHT_SOLAR_PANEL.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.COPPER_CABLE.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.MACERATOR.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.BATTERY_BOX.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.ELECTRIC_FURNACE.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.EXTRACTOR.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.COMPRESSOR.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.GEOTHERMAL_GENERATOR.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.PUMP.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.WATER_MILL.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.WIND_MILL.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.HIGH_ALTITUDE_WIND_MILL.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));
		event.registerBlockEntity(cap, ModBlockEntitiesNeoForge.STORM_WIND_MILL.get(),
				(be, side) -> BufferAsEnergyHandler.of(be.energyPort(side)));

		BlockCapability<ResourceHandler<FluidResource>, Direction> fluidCap = Capabilities.Fluid.BLOCK;
		event.registerBlockEntity(fluidCap, ModBlockEntitiesNeoForge.GEOTHERMAL_GENERATOR.get(),
				(be, side) -> TankAsResourceHandler.of(be.fluidPort(side)));
		event.registerBlockEntity(fluidCap, ModBlockEntitiesNeoForge.PUMP.get(),
				(be, side) -> TankAsResourceHandler.of(be.fluidPort(side)));

		// MOD-063: item-side fluid capability for the Vacuum Capsule, so other mods' pipes/tanks can fill
		// or drain a capsule sitting in a slot. One CapsuleResourceHandler per stack access, both items.
		event.registerItem(Capabilities.Fluid.ITEM,
				(stack, access) -> new dev.alaindustrial.core.neoforge.CapsuleResourceHandler(access),
				ModItemsNeoForge.VACUUM_CAPSULE.get(), ModItemsNeoForge.FILLED_VACUUM_CAPSULE.get());
	}
}
