package dev.alaindustrial.client.neoforge;

import dev.alaindustrial.client.render.RootSoilAppearance;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.model.data.ModelProperty;

/** Ordinary rooted terrain is baked once into the chunk, from immutable model data. */
public final class NeoForgeRootSoilModels {
	public static final ModelProperty<BlockState> SOIL = new ModelProperty<>();
	private NeoForgeRootSoilModels() { }
	public static void init() { RootSoilAppearance.reader = NeoForgeRootSoilModels::soil; }
	private static BlockState soil(BlockAndTintGetter level, BlockPos pos) {
		BlockState state = level.getModelData(pos).get(SOIL);
		return state == null ? Blocks.DIRT.defaultBlockState() : state;
	}
	public static void onBake(ModelEvent.ModifyBakingResult event) {
		Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
		Map<BlockState, BlockStateModel> originals = Map.copyOf(models);
		models.replaceAll((state, model) -> state.is(ModContent.KOK_SAGYZ_ROOT.get())
				? new SoilModel(model, originals) : model);
	}
	private record SoilModel(BlockStateModel fallback, Map<BlockState, BlockStateModel> models) implements DynamicBlockStateModel {
		private BlockStateModel model(BlockState state) { return models.getOrDefault(state, fallback); }
		@Override public Material.Baked particleMaterial() { return fallback.particleMaterial(); }
		@Override public int materialFlags() { return fallback.materialFlags(); }
		@Override public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
				RandomSource random, List<BlockStateModelPart> parts) {
			BlockState source = soil(level, pos);
			model(source).collectParts(level, pos, source, random, parts);
		}
		@Override public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
			return new GeometryKey(soil(level, pos), pos.asLong());
		}
		@Override public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
			BlockState source = soil(level, pos);
			return model(source).particleMaterial(level, pos, source);
		}
	}
	private record GeometryKey(BlockState soil, long position) { }
}
