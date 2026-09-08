package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.UpgradeTableBlockEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * The Upgrade Table (MOD-482): two casings stacked become the bench that fits permanent upgrades onto
 * a powered tool.
 *
 * <p><b>Built to the Workstation's pattern, deliberately.</b> One block with three parts — a loose
 * casing, a lower half and an upper half — rather than two separate blocks the way the distillation
 * column does its segments. That choice buys a single specification, a single loot table and a single
 * literal in the parity gate, and it makes "an upper half with nothing under it" a state the world
 * cannot write down. The part vocabulary is shared with the Workstation ({@link WorkstationPart}): it
 * describes a stacked machine, not that particular machine, and a second copy of the same three
 * constants would only be a second thing to keep in step.
 *
 * <p><b>Only the lower half is a machine.</b> It alone gets a block entity, so the slots, the buffer
 * and the menu exist in exactly one place and a hopper aimed at the top of the table cannot find a
 * second, empty inventory. The upper half is scenery that mirrors the lit state.
 *
 * <p><b>No block entity renderer.</b> The shape is static, so it lives in ordinary block models —
 * 26.2 allows arbitrary rotations there, which is what lets a model be more than a box. A renderer
 * would cost two client classes, an entry in the client manifest and a literal in the parity gate,
 * and buy nothing: nothing on this bench moves.
 */
public class UpgradeTableBlock extends HorizontalMachineBlock {

	public static final MapCodec<UpgradeTableBlock> CODEC = simpleCodec(UpgradeTableBlock::new);

	/** Loose casing, lower half or upper half. See {@link WorkstationPart}. */
	public static final EnumProperty<WorkstationPart> PART =
			EnumProperty.create("part", WorkstationPart.class);

	/** Lamps alight: the table is assembled and has energy. */
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/**
	 * Silhouettes, built once per geometry at class-init and read by lookup — assembling a shape inside
	 * {@code getShape} is paid twenty times per state while vanilla fills its state cache (ADR-023).
	 *
	 * <p>The lower half is the cabinet: a full-width box, because that is what it is. The upper half is
	 * ONE box hugging the worktop and the rack above it rather than a union of their pieces: a union
	 * draws the player a separate wireframe around every piece, which reads as a pile of parts instead
	 * of one bench.
	 */
	private static final Map<Direction, VoxelShape> LOWER_SHAPES =
			Shapes.rotateHorizontal(Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0));

	private static final Map<Direction, VoxelShape> UPPER_SHAPES =
			Shapes.rotateHorizontal(Block.box(0.0, 0.0, 0.0, 16.0, 13.0, 16.0));

	public UpgradeTableBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState()
				.setValue(PART, WorkstationPart.SINGLE)
				.setValue(LIT, false));
	}

	@Override
	protected MapCodec<? extends UpgradeTableBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(PART, LIT);
	}

	/**
	 * Every state gets a block entity, including the loose casing.
	 *
	 * <p>The first cut handed one only to the assembled lower half, on the theory that an inert
	 * inventory up top is just something for a hopper to find. Two repository-wide sweeps disagreed and
	 * caught it the same day: {@code MenuDataWidthScenarios} places every menu's block in its DEFAULT
	 * state and reads its data width, and the MOD-433 capability sweep does the same for the block/type
	 * pair. A block whose default state has no block entity fails both — and both are right to insist,
	 * because a machine that cannot be probed in its default state cannot be checked at all.
	 *
	 * <p>The hopper worry is answered where it belongs instead: the block entity itself refuses every
	 * face and every slot unless it is the assembled lower half (see
	 * {@code UpgradeTableBlockEntity#getSlotsForFace}). Only the lower half ticks, and only the lower
	 * half is a machine.
	 */
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new UpgradeTableBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (state.getValue(PART) != WorkstationPart.LOWER || level.isClientSide()) {
			return null;
		}
		return machineTicker(level);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level,
			BlockPos pos, CollisionContext context) {
		return switch (state.getValue(PART)) {
			case SINGLE -> Shapes.block();
			case LOWER -> LOWER_SHAPES.get(state.getValue(FACING));
			case UPPER -> UPPER_SHAPES.get(state.getValue(FACING));
		};
	}

	/**
	 * Right-clicking an assembled table opens it — always from the lower half, whichever half was
	 * clicked, because that is where the machine is.
	 *
	 * <p><b>A loose casing passes the click through, and that is load-bearing.</b> Consuming a
	 * right-click stops vanilla from falling through to block placement (MOD-039), and placing a second
	 * casing on top of the first is the only way the player has to build this thing. A casing that
	 * answered the click would make the table unbuildable.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (!state.getValue(PART).assembled()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		BlockPos lower = lowerPos(state, pos);
		if (level.getBlockEntity(lower) instanceof UpgradeTableBlockEntity table) {
			player.openMenu(table);
		}
		// SUCCESS on both sides, like AbstractMachineBlock and therefore like every other menu in the
		// mod. The only thing this override adds is opening from the LOWER half; the answer vanilla
		// gets should not differ for that.
		return InteractionResult.SUCCESS;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer,
			ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		tryAssemble(level, pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		tryAssemble(level, pos);
	}

	/**
	 * A half that has lost its partner becomes a casing again. This is the whole disassembly story:
	 * the player's pickaxe, an explosion, a command, a piston and a lost support all end in a neighbour
	 * update and all get the same answer, and each half still drops its own casing through the ordinary
	 * loot table — so the player always gets back exactly what they put in.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
			BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
			BlockState neighbourState, RandomSource random) {
		WorkstationPart part = state.getValue(PART);
		if (part.assembled() && directionToNeighbour == part.towardPartner()
				&& !isPartner(neighbourState, part)) {
			return state.setValue(PART, WorkstationPart.SINGLE).setValue(LIT, false);
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos,
				neighbourState, random);
	}

	/**
	 * Put two stacked casings together into one table.
	 *
	 * <p>{@code public static} on purpose: a game test places blocks straight into the world, and a
	 * programmatic placement never calls {@code setPlacedBy} (MOD-015).
	 *
	 * <p>The pair prefers to grow downward, and the facing comes from the lower casing, so a stack of
	 * three resolves the same way regardless of which block the player touched last.
	 */
	public static void tryAssemble(Level level, BlockPos pos) {
		if (level.isClientSide()) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!isCasing(state)) {
			return; // already half of a table — and this is what stops neighborChanged recursing
		}
		BlockPos lower;
		if (isCasing(level.getBlockState(pos.below()))) {
			lower = pos.below();
		} else if (isCasing(level.getBlockState(pos.above()))) {
			lower = pos;
		} else {
			return;
		}
		BlockState lowerState = level.getBlockState(lower);
		Direction facing = lowerState.getValue(FACING);
		level.setBlockAndUpdate(lower, lowerState
				.setValue(PART, WorkstationPart.LOWER)
				.setValue(FACING, facing)
				.setValue(LIT, false));
		level.setBlockAndUpdate(lower.above(), lowerState
				.setValue(PART, WorkstationPart.UPPER)
				.setValue(FACING, facing)
				.setValue(LIT, false));
		level.playSound(null, lower, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
	}

	/** Both halves show the same lit state, so the table reads as one object. */
	public static void setLit(Level level, BlockPos lower, boolean lit) {
		for (BlockPos pos : new BlockPos[] {lower, lower.above()}) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof UpgradeTableBlock && state.getValue(PART).assembled()
					&& state.getValue(LIT) != lit) {
				level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
			}
		}
	}

	/**
	 * A cable draws an arm only toward the assembled lower half — the mirror of
	 * {@code UpgradeTableBlockEntity.energyRoleForFace}. The inherited rule ("every face but the front")
	 * is right for a machine and wrong for a block whose default state is a casing that accepts nothing;
	 * an arm toward a face that takes no EU reads to the player as a working joint.
	 */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		if (state.getValue(PART) != WorkstationPart.LOWER) {
			return false;
		}
		return super.isCableConnectable(state, side);
	}

	/** Where the machine lives for a block at {@code pos}; the position itself if unassembled. */
	public static BlockPos lowerPos(BlockState state, BlockPos pos) {
		return state.getBlock() instanceof UpgradeTableBlock
				&& state.getValue(PART) == WorkstationPart.UPPER ? pos.below() : pos;
	}

	private static boolean isCasing(BlockState state) {
		return state.getBlock() instanceof UpgradeTableBlock
				&& state.getValue(PART) == WorkstationPart.SINGLE;
	}

	private static boolean isPartner(BlockState state, WorkstationPart part) {
		return state.getBlock() instanceof UpgradeTableBlock && state.getValue(PART) == part.partner();
	}
}
