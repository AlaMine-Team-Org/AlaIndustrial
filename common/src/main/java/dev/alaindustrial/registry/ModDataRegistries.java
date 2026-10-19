package dev.alaindustrial.registry;

import com.mojang.serialization.Codec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.settlement.SettlementStyle;
import dev.alaindustrial.settlement.shift.ShiftWorkerLook;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * The mod's datapack registries, declared once for both loaders (ADR-050 §3): each entry is a registry key, the
 * codec of its files and — for a registry the client needs — the codec it is sent with. Fabric replays the list
 * into {@code DynamicRegistries}, NeoForge into {@code DataPackRegistryEvent.NewRegistry}; neither has a line of
 * its own per registry.
 *
 * <p>Files of a registry of this mod live under {@code data/<ns>/alaindustrial/<registry>/}: both loaders put the
 * registry's namespace in front of its path. They are loaded with the world, so a changed file takes effect after
 * a restart, not on {@code /reload}.
 */
public final class ModDataRegistries {

	/**
	 * One datapack registry.
	 *
	 * @param networkCodec how its entries reach the client, or {@code null} for a registry only the server reads
	 */
	public record DataRegistry<T>(ResourceKey<Registry<T>> key, Codec<T> codec, @Nullable Codec<T> networkCodec) {
		public DataRegistry {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(codec, "codec");
		}
	}

	/** {@code alaindustrial:settlement_style} — settlement styles (MOD-830), server only. */
	public static final ResourceKey<Registry<SettlementStyle>> SETTLEMENT_STYLE =
			ResourceKey.createRegistryKey(Industrialization.id("settlement_style"));

	/**
	 * {@code alaindustrial:shift_worker_look} — the looks of the shift workers (MOD-832, ADR-050 point 6), synced:
	 * the client draws a worker from its look's texture, body, helmet and hair.
	 */
	public static final ResourceKey<Registry<ShiftWorkerLook>> SHIFT_WORKER_LOOK =
			ResourceKey.createRegistryKey(Industrialization.id("shift_worker_look"));

	/** Every datapack registry of the mod, in the order the loaders add them. */
	public static final List<DataRegistry<?>> REGISTRIES = List.of(
			new DataRegistry<>(SETTLEMENT_STYLE, SettlementStyle.CODEC, null),
			new DataRegistry<>(SHIFT_WORKER_LOOK, ShiftWorkerLook.CODEC, ShiftWorkerLook.NETWORK_CODEC));

	private ModDataRegistries() {
	}
}
