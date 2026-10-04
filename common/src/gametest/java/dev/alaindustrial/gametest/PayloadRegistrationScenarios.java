package dev.alaindustrial.gametest;

import dev.alaindustrial.network.ArchiveRecordPayload;
import dev.alaindustrial.network.ConfigSyncPayload;
import dev.alaindustrial.network.DrillColumnTogglePayload;
import dev.alaindustrial.network.FluxweaveStepAssistPayload;
import dev.alaindustrial.network.MachineStatsPayload;
import dev.alaindustrial.network.MagnetFilterSamplePayload;
import dev.alaindustrial.network.NetworkAnalyzerPayload;
import dev.alaindustrial.network.ReactorLogPayload;
import dev.alaindustrial.network.ReactorZonePayload;
import dev.alaindustrial.network.RepellerDomePayload;
import dev.alaindustrial.network.SkillActionPayload;
import dev.alaindustrial.network.TeleportFadePayload;
import dev.alaindustrial.network.TeleportNoticePayload;
import dev.alaindustrial.network.TeleportRenamePayload;
import dev.alaindustrial.network.TeleportStationsPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * MOD-706 batch 0 — characterization of the payload wiring both loaders do by hand today: every payload
 * of the mod is registered with THIS loader's networking, in the right direction and only in that one,
 * and every serverbound payload has a server-side receiver.
 *
 * <p><b>Why it exists before the refactoring.</b> MOD-706 moves the fifteen hand-written registrations of
 * each loader into one shared list. The static gate ({@code payload_parity_check.py}) compares source
 * text; this scenario asks the loader itself, at runtime, the way a packet would find out — so it has to
 * be green on the unchanged code first, and stay green after every batch.
 *
 * <p><b>The expected table is restated here by hand, on purpose.</b> It names each payload class and its
 * direction once, independently of any registration code or manifest, so it can never degenerate into
 * comparing a list with itself.
 *
 * <p><b>What this cannot see.</b> Delivery: a mock player's connection never negotiated the mod's
 * channels, and NeoForge refuses a payload on such a connection (see {@code ArchiveRecordScenarios}).
 * And on Fabric the client receivers: they are registered by the client entrypoint, which the server
 * lane never runs — the probe answers {@code null} there, and only NeoForge (whose client handlers are
 * part of the common registration) is asked about them.
 *
 * <p>The body is loader-neutral; the {@link Probe} is the loader-specific part, supplied by each lane's
 * entry point (Fabric: {@code PayloadTypeRegistryImpl} + {@code ServerPlayNetworking}; NeoForge:
 * {@code NetworkRegistry}).
 */
public final class PayloadRegistrationScenarios {

	private PayloadRegistrationScenarios() {
	}

	/** Which way a payload travels. */
	public enum Direction {
		CLIENTBOUND, SERVERBOUND
	}

	/** What one loader can say about its own payload registrations. */
	public interface Probe {

		/** The payload type {@code id} is registered for the play phase in {@code direction}. */
		boolean typeRegistered(Identifier id, Direction direction);

		/** A server-side receiver/handler is registered for {@code id}. */
		boolean serverHandlerRegistered(Identifier id);

		/**
		 * A client-side handler is registered for {@code id}, or {@code null} when this lane cannot know
		 * (Fabric registers client receivers from its client entrypoint only).
		 */
		Boolean clientHandlerRegistered(Identifier id);
	}

	/** Every payload of the mod and the way it travels — restated here, never derived from the wiring. */
	static Map<CustomPacketPayload.Type<?>, Direction> expected() {
		Map<CustomPacketPayload.Type<?>, Direction> table = new LinkedHashMap<>();
		table.put(NetworkAnalyzerPayload.TYPE, Direction.CLIENTBOUND);
		table.put(RepellerDomePayload.TYPE, Direction.CLIENTBOUND);
		table.put(MachineStatsPayload.TYPE, Direction.CLIENTBOUND);
		table.put(ReactorZonePayload.TYPE, Direction.CLIENTBOUND);
		table.put(ReactorLogPayload.TYPE, Direction.CLIENTBOUND);
		table.put(TeleportStationsPayload.TYPE, Direction.CLIENTBOUND);
		table.put(TeleportFadePayload.TYPE, Direction.CLIENTBOUND);
		table.put(TeleportNoticePayload.TYPE, Direction.CLIENTBOUND);
		table.put(ArchiveRecordPayload.TYPE, Direction.CLIENTBOUND);
		table.put(ConfigSyncPayload.TYPE, Direction.CLIENTBOUND);
		table.put(TeleportRenamePayload.TYPE, Direction.SERVERBOUND);
		table.put(MagnetFilterSamplePayload.TYPE, Direction.SERVERBOUND);
		table.put(FluxweaveStepAssistPayload.TYPE, Direction.SERVERBOUND);
		table.put(DrillColumnTogglePayload.TYPE, Direction.SERVERBOUND);
		table.put(SkillActionPayload.TYPE, Direction.SERVERBOUND);
		return table;
	}

	/**
	 * MOD-706-PAY01 — every payload is registered in its own direction and not in the other one; a
	 * serverbound payload has a server receiver and a clientbound one has none; where the lane can tell,
	 * a clientbound payload has a client handler.
	 */
	public static void everyPayloadRegisteredInItsDirection(GameTestHelper helper, Probe probe) {
		List<String> problems = new ArrayList<>();
		for (Map.Entry<CustomPacketPayload.Type<?>, Direction> entry : expected().entrySet()) {
			Identifier id = entry.getKey().id();
			Direction direction = entry.getValue();
			Direction other = direction == Direction.CLIENTBOUND ? Direction.SERVERBOUND : Direction.CLIENTBOUND;
			if (!probe.typeRegistered(id, direction)) {
				problems.add(id + " is not registered " + direction);
			}
			if (probe.typeRegistered(id, other)) {
				problems.add(id + " is registered " + other + " as well — it only ever travels " + direction);
			}
			boolean serverHandler = probe.serverHandlerRegistered(id);
			if (direction == Direction.SERVERBOUND && !serverHandler) {
				problems.add(id + " has no server receiver: the packet would arrive and be dropped");
			}
			if (direction == Direction.CLIENTBOUND && serverHandler) {
				problems.add(id + " is clientbound but has a server receiver");
			}
			Boolean clientHandler = probe.clientHandlerRegistered(id);
			if (direction == Direction.CLIENTBOUND && Boolean.FALSE.equals(clientHandler)) {
				problems.add(id + " has no client handler");
			}
			if (direction == Direction.SERVERBOUND && Boolean.TRUE.equals(clientHandler)) {
				problems.add(id + " is serverbound but has a client handler");
			}
		}
		if (!problems.isEmpty()) {
			helper.fail(problems.size() + " payload registration problem(s): " + String.join("; ", problems));
			return;
		}
		helper.succeed();
	}
}
