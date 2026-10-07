package dev.alaindustrial.client;

import dev.alaindustrial.block.PiezoPlateBlock;
import dev.alaindustrial.client.render.PlateCamouflage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModelPart;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric's hook for the piezo plate's camouflage (MOD-764): the plate's baked model is wrapped so that, in the
 * chunk mesh, it emits the squashed floor block ({@link PlateCamouflage}) whenever the block below qualifies,
 * and its own model otherwise. The floor is read from the meshing snapshot, which covers the section's
 * neighbours, so no block-entity data is needed; a changed floor re-meshes the plate's section by itself.
 */
public final class PiezoPlateModels {
	private PiezoPlateModels() {
	}

	public static void init() {
		ModelLoadingPlugin.register(context -> {
			Map<BlockState, BlockStateModel> models = new ConcurrentHashMap<>();
			PlateCamouflage camouflage = new PlateCamouflage();
			context.modifyBlockModelAfterBake().register((model, bake) -> {
				models.put(bake.state(), model);
				return bake.state().getBlock() instanceof PiezoPlateBlock
						? new CamouflagedPlate(model, models, camouflage) : model;
			});
		});
	}

	private record CamouflagedPlate(BlockStateModel own, Map<BlockState, BlockStateModel> models,
			PlateCamouflage camouflage) implements BlockStateModel, FabricBlockStateModel {
		private BlockState floor(BlockAndTintGetter level, BlockPos pos) {
			return level.getBlockState(pos.below());
		}

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
			own.collectParts(random, parts);
		}

		@Override
		public Material.Baked particleMaterial() {
			return own.particleMaterial();
		}

		@Override
		public int materialFlags() {
			return own.materialFlags();
		}

		@Override
		public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
			return new GeometryKey(state, floor(level, pos), pos.asLong());
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
				RandomSource random, Predicate<Direction> cull) {
			BlockState below = floor(level, pos);
			BlockStateModel source = PlateCamouflage.camouflages(below) ? models.get(below) : null;
			if (source == null) {
				((FabricBlockStateModel) own).emitQuads(emitter, level, pos, state, random, cull);
				return;
			}
			for (BlockStateModelPart part : camouflage.parts(source, random, state.getValue(PiezoPlateBlock.POWERED))) {
				((FabricBlockStateModelPart) part).emitQuads(emitter, cull);
			}
		}

		@Override
		public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
			BlockState below = floor(level, pos);
			BlockStateModel source = PlateCamouflage.camouflages(below) ? models.get(below) : null;
			return source == null ? ((FabricBlockStateModel) own).particleMaterial(level, pos, state)
					: ((FabricBlockStateModel) source).particleMaterial(level, pos.below(), below);
		}
	}

	private record GeometryKey(BlockState plate, BlockState floor, long position) {
	}
}
