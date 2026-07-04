package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.advancement.NetworkEnergizedTrigger;
import dev.alaindustrial.core.EnergyNetwork;
import dev.alaindustrial.core.NetworkManager;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Central registration for Industrialization's custom advancement criteria (see task MOD-015). */
public final class ModCriteria {
	private ModCriteria() {
	}

	public static final NetworkEnergizedTrigger NETWORK_ENERGIZED =
			register("network_energized", new NetworkEnergizedTrigger());

	private static <T extends CriterionTrigger<?>> T register(String path, T trigger) {
		ResourceKey<CriterionTrigger<?>> key = ResourceKey.create(Registries.TRIGGER_TYPE, Industrialization.id(path));
		return Registry.register(BuiltInRegistries.TRIGGER_TYPES, key, trigger);
	}

	/** Forces this class to load so its static registration runs; call once from mod init. */
	public static void init() {
	}

	/**
	 * Fires {@link #NETWORK_ENERGIZED} for {@code player} if {@code pos} or any of its neighbours
	 * belong to an awake {@link EnergyNetwork}. Covers both ways a network can complete: the player
	 * placing the cable that connects the last producer/consumer pair (checked at {@code pos} itself,
	 * since a cable is a network member) and the player placing the producer/consumer machine that
	 * completes an existing cable run (checked via the neighbours, since a machine is never itself a
	 * network member — see MOD-015, MOD-016 code review).
	 */
	public static void tryFireNetworkEnergized(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (isAwakeNetworkAt(level, pos)) {
			NETWORK_ENERGIZED.trigger(player);
			return;
		}
		for (Direction dir : Direction.values()) {
			if (isAwakeNetworkAt(level, pos.relative(dir))) {
				NETWORK_ENERGIZED.trigger(player);
				return;
			}
		}
	}

	private static boolean isAwakeNetworkAt(ServerLevel level, BlockPos pos) {
		EnergyNetwork net = NetworkManager.networkAt(level, pos);
		return net != null && net.isAwake();
	}
}
