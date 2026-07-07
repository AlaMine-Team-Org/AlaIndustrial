package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.advancement.NetworkEnergizedTrigger;
import dev.alaindustrial.core.EnergyNetwork;
import dev.alaindustrial.core.NetworkManager;
import java.util.function.Supplier;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Central registration for Industrialization's custom advancement criteria (see task MOD-015).
 *
 * <p>MOD-022 facade: NeoForge freezes the vanilla {@code TRIGGER_TYPES} registry before mod construction,
 * so the trigger is bound lazily per loader — Fabric via the eager {@link #init()} below, NeoForge via a
 * {@code DeferredRegister} (see {@code ModCriteriaNeoForge}) — and read through a {@code Supplier}.
 */
public final class ModCriteria {
	private ModCriteria() {
	}

	/** The registry id, shared by both loaders' registration. */
	public static final Identifier NETWORK_ENERGIZED_ID = Industrialization.id("network_energized");

	/** Bound once per loader before first fire; unbound = loud failure, never a silent NPE. */
	public static Supplier<NetworkEnergizedTrigger> NETWORK_ENERGIZED = () -> {
		throw new IllegalStateException("ModCriteria.NETWORK_ENERGIZED read before its loader bound it");
	};

	/** Build the trigger instance both loaders register. */
	public static NetworkEnergizedTrigger createNetworkEnergized() {
		return new NetworkEnergizedTrigger();
	}

	/**
	 * Fabric registration: the {@code TRIGGER_TYPES} registry stays writable during init, so register the
	 * trigger eagerly and bind it to a constant supplier. NeoForge instead uses a {@code DeferredRegister}
	 * (see {@code ModCriteriaNeoForge}).
	 */
	public static void init() {
		ResourceKey<CriterionTrigger<?>> key = ResourceKey.create(Registries.TRIGGER_TYPE, NETWORK_ENERGIZED_ID);
		NetworkEnergizedTrigger trigger =
				Registry.register(BuiltInRegistries.TRIGGER_TYPES, key, createNetworkEnergized());
		NETWORK_ENERGIZED = () -> trigger;
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
			NETWORK_ENERGIZED.get().trigger(player);
			return;
		}
		for (Direction dir : Direction.values()) {
			if (isAwakeNetworkAt(level, pos.relative(dir))) {
				NETWORK_ENERGIZED.get().trigger(player);
				return;
			}
		}
	}

	private static boolean isAwakeNetworkAt(ServerLevel level, BlockPos pos) {
		EnergyNetwork net = NetworkManager.networkAt(level, pos);
		return net != null && net.isAwake();
	}
}
