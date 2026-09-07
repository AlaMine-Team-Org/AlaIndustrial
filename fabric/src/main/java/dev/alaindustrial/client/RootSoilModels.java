package dev.alaindustrial.client;

import dev.alaindustrial.client.render.RootSoilAppearance;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.blockgetter.v2.FabricBlockGetter;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Delegates the ordinary chunk model to the preserved soil using Fabric render-data snapshots. */
public final class RootSoilModels {
	private RootSoilModels() { }
	public static void init() {
		RootSoilAppearance.reader = RootSoilModels::soil;
		ModelLoadingPlugin.register(context -> {
			Map<BlockState, BlockStateModel> models = new ConcurrentHashMap<>();
			context.modifyBlockModelAfterBake().register((model, bake) -> {
				models.put(bake.state(), model);
				return bake.state().is(ModContent.KOK_SAGYZ_ROOT.get()) ? new SoilModel(model, models) : model;
			});
		});
	}
	private static BlockState soil(BlockAndTintGetter level, BlockPos pos) {
		Object data = ((FabricBlockGetter) level).getBlockEntityRenderData(pos);
		return data instanceof BlockState state ? state : Blocks.DIRT.defaultBlockState();
	}
	private record SoilModel(BlockStateModel fallback, Map<BlockState, BlockStateModel> models)
			implements BlockStateModel, FabricBlockStateModel {
		private BlockStateModel model(BlockState state) { return models.getOrDefault(state, fallback); }
		@Override public void collectParts(RandomSource random, List<BlockStateModelPart> parts) { fallback.collectParts(random, parts); }
		@Override public Material.Baked particleMaterial() { return fallback.particleMaterial(); }
		@Override public int materialFlags() { return fallback.materialFlags(); }
		@Override public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
			return new GeometryKey(soil(level, pos), pos.asLong());
		}
		@Override public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos,
				BlockState state, RandomSource random, Predicate<Direction> cull) {
			BlockState source = soil(level, pos);
			((FabricBlockStateModel) model(source)).emitQuads(emitter, level, pos, source, random, cull);
		}
		@Override public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
			return ((FabricBlockStateModel) model(soil(level, pos))).particleMaterial(level, pos, soil(level, pos));
		}
	}
	private record GeometryKey(BlockState soil, long position) { }
}
