package dev.alaindustrial.registry;

import com.mojang.serialization.Codec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.NetworkScanData;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

/**
 * Custom data components (MOD-022 facade). {@link #STORED_ENERGY} lets an energy-storage block carry
 * its buffered EU on its dropped item (R-BRK-07), so a charged BatteryBox keeps its charge through
 * break → place. Storage block entities emit/read it via collect/applyImplicitComponents; the block's
 * loot table copies it from the block entity onto the drop. {@link #NETWORK_SCAN} carries the Network
 * Analyzer's last reading on the tool itself, so the tooltip can show it after the actionbar fades.
 *
 * <p>NeoForge freezes the vanilla {@code DATA_COMPONENT_TYPE} registry before mod construction, so a
 * direct {@code Registry.register} (fine on Fabric) throws {@code Registry is already frozen} there.
 * Each loader binds the handles below during its own registration — Fabric via an eager
 * {@code Registry.register}, NeoForge via a {@code DeferredRegister} holder (itself a {@link Supplier}) —
 * and content reads them lazily through {@code .get()}.
 */
public final class ModDataComponents {
	private ModDataComponents() {
	}

	/** Registry ids, shared by both loaders' registration. */
	public static final Identifier STORED_ENERGY_ID = Industrialization.id("stored_energy");
	public static final Identifier NETWORK_SCAN_ID = Industrialization.id("network_scan");

	/** Buffered EU carried on a storage block's item form. Bound once per loader before first access. */
	public static Supplier<DataComponentType<Long>> STORED_ENERGY = () -> {
		throw new IllegalStateException("ModDataComponents.STORED_ENERGY read before its loader bound it");
	};

	/** Last Network Analyzer scan, stored on the tool so its tooltip can replay the reading (MOD-016). */
	public static Supplier<DataComponentType<NetworkScanData>> NETWORK_SCAN = () -> {
		throw new IllegalStateException("ModDataComponents.NETWORK_SCAN read before its loader bound it");
	};

	/** Build the {@code stored_energy} type both loaders register. */
	public static DataComponentType<Long> createStoredEnergy() {
		return DataComponentType.<Long>builder()
				.persistent(Codec.LONG)
				.networkSynchronized(ByteBufCodecs.VAR_LONG)
				.build();
	}

	/** Build the {@code network_scan} type both loaders register. */
	public static DataComponentType<NetworkScanData> createNetworkScan() {
		return DataComponentType.<NetworkScanData>builder()
				.persistent(NetworkScanData.CODEC)
				.networkSynchronized(NetworkScanData.STREAM_CODEC)
				.build();
	}
}
