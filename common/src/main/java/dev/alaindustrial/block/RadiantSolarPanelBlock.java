package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.RadiantSolarPanelBlockEntity;
import dev.alaindustrial.core.environment.SolarSky;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Mirror Concentrator — the third rung of the day branch, grown from {@link DaylightSolarPanelBlock}.
 * A raised collector on a mast with two mirror wings that fold over it when the block cannot work
 * (MOD-602).
 *
 * <p><b>It is a machine on its own and the heart of a bigger one (MOD-603).</b> Standing alone it
 * works exactly as it always has. Surrounded by seven Concentrator Sections in any of the four boxes
 * it can be the bottom corner of, it becomes {@link ConcentratorPart#CORE}: the same block entity,
 * the same buffer and the same energy face, driving a machine eight times the volume. Nothing about
 * the growth from the daylight panel changed — evolution still hands the player a working
 * concentrator, and building it out is a second, optional step.
 *
 * <p><b>Standing alone it is a slab, like the two panels it grew from.</b> It used to carry the whole
 * three-dimensional machine at one-block scale, and that was wrong twice over: an installation drawn
 * for a big machine reads as broken at the size of a doorstep, and its folding mirrors swept through
 * a volume no honest hitbox could follow. The machine — model, mirrors and all — belongs to the
 * assembled structure.
 */
public class RadiantSolarPanelBlock extends AbstractSolarPanelBlock {
	public static final MapCodec<RadiantSolarPanelBlock> CODEC = simpleCodec(RadiantSolarPanelBlock::new);

	/** True once seven sections have closed around this block and it drives the whole structure. */
	public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

	/**
	 * Which way the assembled structure faces; ignored while standing alone.
	 *
	 * <p>The rest of the solar family is deliberately facing-inert, and so is this block until it is
	 * assembled — the value exists to say which of the four boxes around the core the structure grew
	 * into, not to point the collector anywhere. A lone panel keeps the default and looks identical
	 * from every side, which is why {@code getStateForPlacement} is left alone.
	 */
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	/**
	 * The one-block form is a slab, exactly like the two panels it grew from.
	 *
	 * <p>It used to carry the whole three-dimensional machine at one-block scale, and that was wrong
	 * twice over: a big installation the size of a doorstep reads as broken, and the folding mirrors
	 * swept through a volume no hitbox could honestly describe. The machine belongs to the assembled
	 * structure; until the player builds it, this is a panel and looks like one.
	 */
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);

	public RadiantSolarPanelBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(ASSEMBLED, Boolean.FALSE)
				.setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(ASSEMBLED, FACING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.getValue(ASSEMBLED)) {
			return ConcentratorPart.CORE.shape(state.getValue(FACING));
		}
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RadiantSolarPanelBlockEntity(pos, state);
	}

	/**
	 * Pattern C, with the concentrator's own sky rule: snow counts as "not working" here, unlike on
	 * the two panels below. The wings read the same verdict, so the hum and the optics never disagree.
	 *
	 * <p><b>Assembled, the machine stands under four columns of sky, not one.</b> A roof over any one
	 * of them stops it — the collector spans the whole footprint, so shading a quarter of it and
	 * calling the machine unaffected would be a lie the player can see. Reading only the core's own
	 * column, which is what this did at first, meant a block laid over three of the four corners did
	 * nothing at all.
	 */
	@Override
	public boolean isWorking(Level level, BlockPos pos, BlockState state) {
		return SolarSky.isConcentratorActive(level, skyProbe(pos, state))
				&& coveredColumnsAreClear(level, pos, state);
	}

	/**
	 * Where this machine should be asked about the sky.
	 *
	 * <p>Assembled, that is one block UP — its own top cell stands directly over the core, and a
	 * machine that counted its own roof as shade would run at half power forever, under a clear sky,
	 * with nothing above it at all.
	 */
	public static BlockPos skyProbe(BlockPos pos, BlockState state) {
		return state.hasProperty(ASSEMBLED) && state.getValue(ASSEMBLED) ? pos.above() : pos;
	}

	/**
	 * True when every column the assembled machine stands under still sees the sky.
	 *
	 * <p>The collector spans the whole footprint, so a roof over any one of the four corners stops
	 * the machine. Reading only the core's own column — which is what this did at first — meant a
	 * block laid over three corners out of four did nothing whatsoever, and that is exactly what a
	 * player found. Always true for the one-block form, which stands under exactly one column.
	 *
	 * <p>Only the four TOP cells are asked: the bottom four stand directly under them and share their
	 * columns, so asking again would cost four lookups and change no answer.
	 */
	public static boolean coveredColumnsAreClear(Level level, BlockPos pos, BlockState state) {
		if (!state.hasProperty(ASSEMBLED) || !state.getValue(ASSEMBLED)) {
			return true;
		}
		Direction facing = state.getValue(FACING);
		for (ConcentratorPart part : ConcentratorPart.CELLS) {
			Vec3i cell = part.canonicalOffset();
			if (cell == null || cell.getY() != 1) {
				continue;
			}
			if (!SolarSky.isConcentratorActive(level, pos.offset(part.worldOffset(facing)))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A panel placed by hand may land inside a box of sections a player prepared in advance.
	 *
	 * <p>Evolution does not come through here — it writes the block straight into the world — so
	 * {@link #neighborChanged} is what catches the far more common order of events: the player grows
	 * the panel first, then builds around it.
	 */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer,
			ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		ConcentratorStructure.tryAssemble(level, pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		ConcentratorStructure.tryAssemble(level, pos);
	}

	/**
	 * Losing a section takes the structure apart and leaves this block standing as the one-block
	 * machine it was before. It keeps its block entity, its buffer and its charge throughout: the
	 * player who mines one section has downgraded the machine, not destroyed it.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
			BlockState neighbourState, RandomSource random) {
		BlockState answer = ConcentratorStructure.afterNeighbourChange(state, directionToNeighbour,
				neighbourState);
		if (answer != state) {
			return answer;
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos,
				neighbourState, random);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
