package dev.alaindustrial.registry;

import com.mojang.serialization.Codec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.NetworkScanData;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

/**
 * Custom data components. {@link #STORED_ENERGY} lets an energy-storage block carry its buffered EU
 * on its dropped item (R-BRK-07), so a charged BatteryBox keeps its charge through break → place.
 * Storage block entities emit/read it via collect/applyImplicitComponents; the block's loot table
 * copies it from the block entity onto the drop. {@link #NETWORK_SCAN} carries the Network Analyzer's
 * last reading on the tool itself, so the tooltip can show it after the actionbar message fades.
 */
public final class ModDataComponents {
	private ModDataComponents() {
	}

	/** Buffered EU carried on a storage block's item form. */
	public static final DataComponentType<Long> STORED_ENERGY = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(Industrialization.MOD_ID, "stored_energy"),
			DataComponentType.<Long>builder()
					.persistent(Codec.LONG)
					.networkSynchronized(ByteBufCodecs.VAR_LONG)
					.build());

	/** Last Network Analyzer scan, stored on the tool so its tooltip can replay the reading (MOD-016). */
	public static final DataComponentType<NetworkScanData> NETWORK_SCAN = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(Industrialization.MOD_ID, "network_scan"),
			DataComponentType.<NetworkScanData>builder()
					.persistent(NetworkScanData.CODEC)
					.networkSynchronized(NetworkScanData.STREAM_CODEC)
					.build());

	/** Force class-init so the component registers. Call from the mod initializer. */
	public static void init() {
	}
}
