package dev.alaindustrial.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The structure-template manager and a structure's biome check, whose API differs between the Minecraft lines
 * (MOD-830, MOD-845, ADR-036). Every line has a twin of this class with the same signatures; only the bodies
 * differ. The ALA town structure, the town edit bench and their game tests reach both through it.
 *
 * <p><b>This twin: Minecraft 26.2</b>, where the server and the level name the manager
 * {@code getStructureManager()} and {@code Structure.isValidBiome(GenerationStub, GenerationContext)} is private,
 * so its body is repeated here from the 26.2 sources: the noise biome at the stub's start position, tested by the
 * context's {@code validBiome}. (26.3 renames the getter {@code getStructureTemplateManager()} and makes the check
 * the public {@code GenerationContext.isValidBiome(GenerationStub)}.)
 */
public final class LineStructures {

	private LineStructures() {
	}

	/** The server's structure-template manager. */
	public static StructureTemplateManager templates(MinecraftServer server) {
		return server.getStructureManager();
	}

	/** The structure-template manager of the level's server. */
	public static StructureTemplateManager templates(ServerLevel level) {
		return level.getStructureManager();
	}

	/** Whether the biome at the start of {@code stub} is one the structure may generate in. */
	public static boolean isValidBiome(Structure.GenerationContext context, Structure.GenerationStub stub) {
		BlockPos start = stub.position();
		return context.validBiome().test(context.chunkGenerator().getBiomeSource().getNoiseBiome(
				QuartPos.fromBlock(start.getX()), QuartPos.fromBlock(start.getY()), QuartPos.fromBlock(start.getZ()),
				context.randomState().sampler()));
	}
}
