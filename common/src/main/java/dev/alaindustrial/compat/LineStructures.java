package dev.alaindustrial.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The structure-template manager and a structure's biome check, whose API differs between the Minecraft lines
 * (MOD-830, MOD-845, ADR-036). Every line has a twin of this class with the same signatures; only the bodies
 * differ. The ALA town structure, the town edit bench and their game tests reach both through it.
 *
 * <p><b>This twin: Minecraft 26.3</b>, where the server and the level name the manager
 * {@code getStructureTemplateManager()} and the check is the public
 * {@code GenerationContext.isValidBiome(GenerationStub)}. (26.2 names the getter {@code getStructureManager()} and
 * keeps the check a private static of {@code Structure}, so its twin repeats the body.)
 */
public final class LineStructures {

	private LineStructures() {
	}

	/** The server's structure-template manager. */
	public static StructureTemplateManager templates(MinecraftServer server) {
		return server.getStructureTemplateManager();
	}

	/** The structure-template manager of the level's server. */
	public static StructureTemplateManager templates(ServerLevel level) {
		return level.getStructureTemplateManager();
	}

	/** Whether the biome at the start of {@code stub} is one the structure may generate in. */
	public static boolean isValidBiome(Structure.GenerationContext context, Structure.GenerationStub stub) {
		return context.isValidBiome(stub);
	}
}
