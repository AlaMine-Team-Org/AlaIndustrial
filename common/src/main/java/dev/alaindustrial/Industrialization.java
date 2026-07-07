package dev.alaindustrial;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-neutral mod constants and helpers shared by every subproject: the mod id, the SLF4J
 * logger, and the {@link Identifier} factory. The Fabric {@code ModInitializer} entrypoint lives
 * in {@code dev.alaindustrial.IndustrializationFabric}; the NeoForge {@code @Mod} entrypoint will
 * live on its own side (MOD-022 Phase 3).
 */
public final class Industrialization {
	public static final String MOD_ID = "alaindustrial";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private Industrialization() {
	}

	/** Build an {@link Identifier} in the {@code alaindustrial} namespace. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
