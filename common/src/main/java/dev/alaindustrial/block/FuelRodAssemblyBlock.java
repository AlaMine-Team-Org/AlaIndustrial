package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.FuelRodAssemblyBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The fuel assembly (MOD-468, stage 2): a transparent rack that stands on the reactor floor and is
 * filled with uranium rods by hand, one at a time.
 *
 * <p><b>Why a rack rather than a slot.</b> A machine you load through a menu is a machine you stop
 * looking at. Here the fuel is part of the room: an empty assembly and a full one are told apart from
 * across the floor, "top up the reactor" is something done in the world, and a half-loaded core is
 * visible at a glance. The blockstate carries {@link #RODS} 0…{@value #MAX_RODS} and the model draws
 * exactly that many rods inside the casing.
 *
 * <p><b>Two-step fuel is deliberate.</b> {@code refined_uranium} — which the mod has produced since
 * MOD-424 and which no recipe has ever consumed — becomes a {@code uranium_fuel_rod}, and only rods go
 * into the rack. That is what turns the long ore → dust → shavings → refined chain into something with
 * an end, instead of a fifth intermediate nobody asked for.
 *
 * <p>Interaction is the obvious one: right-click with a rod to insert, right-click empty-handed to
 * pull the last one back out. No menu at all — a container with four identical items in it would be a
 * GUI for nothing.
 */
public class FuelRodAssemblyBlock extends BaseEntityBlock {

	public static final MapCodec<FuelRodAssemblyBlock> CODEC = simpleCodec(FuelRodAssemblyBlock::new);

	/** How many rods one assembly holds. Four reads clearly at 16 px and keeps the maths in round numbers. */
	public static final int MAX_RODS = 4;

	/** Rods currently racked. Drives the model, so the fill level is visible without opening anything. */
	public static final IntegerProperty RODS = IntegerProperty.create("rods", 0, MAX_RODS);

	/**
	 * Coolant level, 0…{@value #WATER_LEVELS}, in quarters of the column's tank. Drives its own multipart
	 * model, for the same reason {@link #RODS} does: the state a player has to react to is the state they
	 * should be able to see from across the floor. A column going dry is the reactor's first warning, and
	 * it arrives long before the heat gauge does.
	 *
	 * <p>Quarters rather than millibuckets: the blockstate is replicated to every client in range, and a
	 * per-mB property would resend the whole column table several times a second for a bar four pixels
	 * tall.
	 */
	public static final int WATER_LEVELS = 4;
	public static final IntegerProperty WATER = IntegerProperty.create("water", 0, WATER_LEVELS);

	/**
	 * How many of the racked rods still carry uranium. {@link #RODS} counts everything standing in the
	 * rack, spent casings included, and the model draws the difference in a dead grey.
	 *
	 * <p><b>Both are needed, and the first version had only one.</b> With {@code RODS} counting fuel
	 * alone, a worked-out column reported zero: the casings inside became invisible AND untouchable,
	 * because the interaction code read the blockstate to decide whether there was anything to take
	 * out. A player could neither empty nor refill the column — only break it.
	 */
	public static final IntegerProperty FUELLED = IntegerProperty.create("fuelled", 0, MAX_RODS);

	/**
	 * Whether another assembly stands directly above / below. Stacked racks drop the cap and the base
	 * plate between them and become one continuous column — the same trick a cable or a pipe uses to
	 * stop looking like a row of separate boxes. Cosmetic only: a lone rack is closed on both ends.
	 */
	public static final BooleanProperty UP = BooleanProperty.create("up");
	public static final BooleanProperty DOWN = BooleanProperty.create("down");

	/**
	 * A rack, not a full cube: 12×14×12, so the room reads as machinery rather than as a filled box.
	 *
	 * <p>Three shapes, because a stacked rack is drawn taller than a lone one. The connector pieces fill
	 * the gap between racks so the column has no seam, and the outline has to follow: with one fixed
	 * 14-high shape the selection box of a stacked assembly would float two pixels below the geometry
	 * the player can see.
	 */
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 14.0, 14.0);
	private static final VoxelShape SHAPE_UP = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
	private static final VoxelShape SHAPE_DOWN = Block.box(2.0, -0.0, 2.0, 14.0, 14.0, 14.0);
	private static final VoxelShape SHAPE_BOTH = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

	public FuelRodAssemblyBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(RODS, 0).setValue(FUELLED, 0).setValue(WATER, 0)
				.setValue(UP, false).setValue(DOWN, false));
	}

	@Override
	protected MapCodec<? extends FuelRodAssemblyBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(RODS, FUELLED, WATER, UP, DOWN);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
			BlockPos pos, CollisionContext context) {
		boolean up = state.getValue(UP);
		boolean down = state.getValue(DOWN);
		if (up && down) {
			return SHAPE_BOTH;
		}
		return up ? SHAPE_UP : down ? SHAPE_DOWN : SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FuelRodAssemblyBlockEntity(pos, state);
	}

	/** Joins up with whatever is already there the moment the rack is placed. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return connect(defaultBlockState(), context.getLevel(), context.getClickedPos());
	}

	/**
	 * Re-joins when a neighbour appears or goes away. {@code updateShape} rather than
	 * {@code neighborChanged}: it is the hook vanilla calls for exactly this — a block adjusting its own
	 * shape to its surroundings — and it runs during world edits and structure placement too.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
			RandomSource random) {
		if (directionToNeighbour == Direction.UP) {
			return state.setValue(UP, neighbourState.is(this));
		}
		if (directionToNeighbour == Direction.DOWN) {
			return state.setValue(DOWN, neighbourState.is(this));
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos,
				neighbourState, random);
	}

	private BlockState connect(BlockState state, LevelReader level, BlockPos pos) {
		return state
				.setValue(UP, level.getBlockState(pos.above()).is(this))
				.setValue(DOWN, level.getBlockState(pos.below()).is(this));
	}

	/**
	 * Empty hand pulls the top rod out. Loading happens in {@code useItemOn} on the item side, so a
	 * player holding anything else — a pickaxe, a torch — is not silently consumed here.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		// Asks the rack, never the blockstate. Gating on a property that counted only fuel is exactly
		// what made a column full of spent casings impossible to empty.
		if (level.getBlockEntity(pos) instanceof FuelRodAssemblyBlockEntity assembly) {
			ItemStack removed = assembly.removeRod();
			if (!removed.isEmpty()) {
				if (!player.getInventory().add(removed)) {
					player.drop(removed, false);
				}
				level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7f, 1.2f);
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.PASS;
	}

	/**
	 * Gives everything racked back when the column is destroyed — fuelled rods with their remaining
	 * charge, and spent casings too.
	 *
	 * <p>{@code affectNeighborsAfterRemoval} rather than a player-only hook, because a creeper taking
	 * out the rack is exactly when losing the fuel would hurt most. The earlier version handed back only
	 * whole rods and destroyed the one mid-burn, which it had to: there was nowhere to record how much
	 * of it was left. Now the wear lives on the item, so nothing has to be thrown away.
	 */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		if (level.getBlockEntity(pos) instanceof FuelRodAssemblyBlockEntity assembly) {
			for (ItemStack stack : assembly.contents()) {
				Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						stack);
			}
		}
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	/** A rod in hand goes into the rack; anything else falls through to normal placement. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(ModContent.URANIUM_FUEL_ROD.get())
				&& !stack.is(ModContent.EMPTY_FUEL_ROD.get())) {
			// TRY_WITH_EMPTY_HAND, not PASS: in 26.2 a PASS here does NOT fall through to
			// useWithoutItem, so pulling a rod out with a pickaxe in hand would silently do nothing.
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof FuelRodAssemblyBlockEntity assembly
				&& assembly.insertRod(stack.copyWithCount(1))) {
			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}
			level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.8f, 1.4f);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.CONSUME;
	}
}
