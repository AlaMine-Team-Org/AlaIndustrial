package dev.alaindustrial.client.neoforge;

import dev.alaindustrial.block.PiezoPlateBlock;
import dev.alaindustrial.client.render.PlateCamouflage;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;

/**
 * NeoForge's hook for the piezo plate's camouflage (MOD-764): the twin of Fabric's {@code PiezoPlateModels}.
 * The plate's baked model becomes a dynamic model that collects the squashed floor block
 * ({@link PlateCamouflage}) whenever the block below qualifies, and its own parts otherwise.
 */
public final class NeoForgePiezoPlateModels {
	private NeoForgePiezoPlateModels() {
	}

	public static void onBake(ModelEvent.ModifyBakingResult event) {
		Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
		Map<BlockState, BlockStateModel> originals = Map.copyOf(models);
		PlateCamouflage camouflage = new PlateCamouflage();
		models.replaceAll((state, model) -> state.getBlock() instanceof PiezoPlateBlock
				? new CamouflagedPlate(model, originals, camouflage) : model);
	}

	private record CamouflagedPlate(BlockStateModel own, Map<BlockState, BlockStateModel> models,
			PlateCamouflage camouflage) implements DynamicBlockStateModel {
		private BlockStateModel source(BlockState below) {
			return PlateCamouflage.camouflages(below) ? models.get(below) : null;
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
		public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
				List<BlockStateModelPart> parts) {
			BlockStateModel source = source(level.getBlockState(pos.below()));
			if (source == null) {
				own.collectParts(level, pos, state, random, parts);
				return;
			}
			parts.addAll(camouflage.parts(source, random, state.getValue(PiezoPlateBlock.POWERED)));
		}

		@Override
		public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
			return new GeometryKey(state, level.getBlockState(pos.below()), pos.asLong());
		}

		@Override
		public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
			BlockState below = level.getBlockState(pos.below());
			BlockStateModel source = source(below);
			return source == null ? own.particleMaterial(level, pos, state)
					: source.particleMaterial(level, pos.below(), below);
		}
	}

	private record GeometryKey(BlockState plate, BlockState floor, long position) {
	}
}
