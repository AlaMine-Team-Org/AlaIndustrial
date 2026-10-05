package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric entry point for the MOD-706 payload characterization. The body is loader-neutral
 * ({@link PayloadRegistrationScenarios}); the probe is the loader-specific part. Fabric's public API has
 * no read side for payload types, so the probe reads {@code PayloadTypeRegistryImpl.get(Identifier)}
 * (public on the impl class, verified by javap against fabric-networking-api-v1 6.3.8 for 26.3 and 6.3.3
 * for 26.2) and the public {@code ServerPlayNetworking.getGlobalReceivers()}. Client receivers are
 * registered by the client entrypoint, which this server lane never runs, so they are not asked about.
 * The NeoForge lane supplies its probe in {@code NeoForgeGameTests}.
 */
@SuppressWarnings("UnstableApiUsage")
public class PayloadRegistrationGameTest {

	private static final PayloadRegistrationScenarios.Probe PROBE = new PayloadRegistrationScenarios.Probe() {
		@Override
		public boolean typeRegistered(net.minecraft.resources.Identifier id,
				PayloadRegistrationScenarios.Direction direction) {
			PayloadTypeRegistryImpl<?> registry = direction == PayloadRegistrationScenarios.Direction.CLIENTBOUND
					? PayloadTypeRegistryImpl.CLIENTBOUND_PLAY
					: PayloadTypeRegistryImpl.SERVERBOUND_PLAY;
			return registry.get(id) != null;
		}

		@Override
		public boolean serverHandlerRegistered(net.minecraft.resources.Identifier id) {
			return ServerPlayNetworking.getGlobalReceivers().contains(id);
		}

		@Override
		public Boolean clientHandlerRegistered(net.minecraft.resources.Identifier id) {
			return null;
		}
	};

	/** @implements MOD-706-PAY01 — every payload registered in its direction only, serverbound ones received */
	@GameTest
	public void mod706_everyPayloadRegisteredInItsDirection(GameTestHelper helper) {
		PayloadRegistrationScenarios.everyPayloadRegisteredInItsDirection(helper, PROBE);
	}
}
