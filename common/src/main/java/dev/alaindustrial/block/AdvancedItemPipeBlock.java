package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.core.item.PipeFaceRender;
import dev.alaindustrial.core.item.PipeTier;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The advanced item pipe (MOD-581): twice the throughput of the basic grade, and visibly thicker.
 *
 * <p><b>A separate class rather than a field</b> because {@link ItemPipeBlock} is built by
 * {@code simpleCodec}, which reconstructs a block from its {@code Properties} alone — a tier field
 * would come back as the basic grade on every decode. The type carries it instead, and the two
 * grades are separate blocks anyway, exactly as the cable grades are.
 *
 * <p><b>The thickness is not decoration.</b> A pipe network runs at its WEAKEST grade, so one basic
 * segment left in an upgraded line caps the whole line. A rule that punishes a single forgotten
 * segment has to let the player find it by looking.
 */
public final class AdvancedItemPipeBlock extends ItemPipeBlock {

	public static final MapCodec<AdvancedItemPipeBlock> CODEC = simpleCodec(AdvancedItemPipeBlock::new);

	public AdvancedItemPipeBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public PipeTier tier() {
		return PipeTier.ADVANCED;
	}

	@Override
	protected VoxelShape shapeFor(PipeFaceRender down, PipeFaceRender up, PipeFaceRender north,
			PipeFaceRender south, PipeFaceRender west, PipeFaceRender east) {
		return PipeShapes.ofThick(down, up, north, south, west, east);
	}
}
