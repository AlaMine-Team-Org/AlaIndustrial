package dev.alaindustrial.gametest.neoforge;

import dev.alaindustrial.gametest.PayloadRegistrationScenarios;
import java.util.Map;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * The NeoForge probe for {@link PayloadRegistrationScenarios} (MOD-706 batch 0).
 *
 * <p>A type is registered in a direction when {@code NetworkRegistry.getCodec(id, PLAY, flow)} answers:
 * it returns {@code null} both for an unknown id and for a registration whose flow is the other one
 * (verified in the neoforge 26.3.0.7-beta sources; the same public method exists in 26.2.0.67). The
 * handler maps are {@code protected static} on {@link NetworkRegistry}, which is why this class extends
 * it: a subclass is the one place that may read them without reflection. Both maps are filled by the
 * common registration on every dist — a {@code playToClient} handler lands in
 * {@code CLIENTBOUND_HANDLERS} on a dedicated server too — so this server lane can answer for the
 * client handlers that the Fabric lane cannot see.
 */
final class NeoForgePayloadProbe extends NetworkRegistry implements PayloadRegistrationScenarios.Probe {

	static final NeoForgePayloadProbe INSTANCE = new NeoForgePayloadProbe();

	private NeoForgePayloadProbe() {
	}

	@Override
	public boolean typeRegistered(Identifier id, PayloadRegistrationScenarios.Direction direction) {
		PacketFlow flow = direction == PayloadRegistrationScenarios.Direction.CLIENTBOUND
				? PacketFlow.CLIENTBOUND
				: PacketFlow.SERVERBOUND;
		return NetworkRegistry.getCodec(id, ConnectionProtocol.PLAY, flow) != null;
	}

	@Override
	public boolean serverHandlerRegistered(Identifier id) {
		return playHandlers(SERVERBOUND_HANDLERS).containsKey(id);
	}

	@Override
	public Boolean clientHandlerRegistered(Identifier id) {
		return playHandlers(CLIENTBOUND_HANDLERS).containsKey(id);
	}

	private static Map<Identifier, IPayloadHandler<?>> playHandlers(
			Map<ConnectionProtocol, Map<Identifier, IPayloadHandler<?>>> byProtocol) {
		Map<Identifier, IPayloadHandler<?>> play = byProtocol.get(ConnectionProtocol.PLAY);
		return play == null ? Map.of() : play;
	}
}
