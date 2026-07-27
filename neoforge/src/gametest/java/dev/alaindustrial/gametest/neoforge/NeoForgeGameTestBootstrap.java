package dev.alaindustrial.gametest.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * MOD-242 — single wiring point between the production entrypoint and the gametest source set.
 * {@code IndustrializationNeoForge#bootstrapGameTests} invokes {@link #init} reflectively during mod
 * construction: in dev runs the {@code gametest} source set is part of the mod (see
 * {@code neoforge/build.gradle}, {@code mods} block), so the class resolves and the test lane is wired
 * exactly as it was when these calls sat inline in the entrypoint; in a shipped jar the source set is
 * absent and the reflective lookup is a silent no-op.
 *
 * <p>Everything registered here is dev-only by construction:
 * <ul>
 *   <li>{@link NeoForgeGameTests#INSTANCE_TYPES} — the {@code alaindustrial:code} gametest-instance
 *       codec type. Only needed when gametest instances can exist in the {@code TEST_INSTANCE}
 *       registry, which requires the {@code RegisterGameTestsEvent} listener below (the handshake
 *       ClassCastException it fixes can only reproduce in dev, where both are present).</li>
 *   <li>{@link NeoForgeGameTests#register} — the world-scenario registrations;
 *       {@code RegisterGameTestsEvent} fires only when {@code GameTestHooks.isGametestEnabled()}.</li>
 *   <li>{@link ForeignEnergyItemStandIn#register} — the MOD-084 fake foreign-mod energy item; it
 *       additionally guards itself with {@code FMLEnvironment.isProduction()}.</li>
 * </ul>
 */
public final class NeoForgeGameTestBootstrap {
	private NeoForgeGameTestBootstrap() {
	}

	/** Entry point, invoked reflectively from {@code IndustrializationNeoForge} on the mod bus. */
	public static void init(IEventBus modBus) {
		NeoForgeGameTests.INSTANCE_TYPES.register(modBus);
		modBus.addListener(NeoForgeGameTests::register);
		modBus.addListener((RegisterCapabilitiesEvent event) -> ForeignEnergyItemStandIn.register(event));
	}
}
