package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.WorkstationBlockEntity;
import dev.alaindustrial.client.skill.SkillTreeClientAccess;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * The Workstation (MOD-483): two casings stacked become a two-block machine with screens.
 *
 * <p><b>One block, three parts.</b> The casing the player crafts, the lower half and the upper half
 * are all this block, told apart by {@link #PART}. The alternative — a separate block for the upper
 * half, the way the distillation column does its segments — costs a second specification with its
 * four translations, a second loot table and a second literal in the parity gate, and it lets the
 * world hold an upper half with nothing under it. Here that state cannot be written down.
 *
 * <p><b>Assembly and disassembly are asymmetric on purpose.</b> Putting the pair together needs an
 * explicit trigger, because "two casings happen to be stacked" is a thing the player did, and it
 * should make a sound and pick a facing. Taking it apart needs no trigger at all: {@link
 * #updateShape} turns any half that has lost its partner back into a casing, and that one rule
 * covers the player, an explosion, a command, a piston and a lost support without a single removal
 * hook. The reactor door destroys its other half instead, which is why it also needs a loot-table
 * condition to stop the second drop; degrading needs neither.
 *
 * <p><b>No {@code noOcclusion()}.</b> The default state is a loose casing — a full cube — and
 * R-PHY-05 reads exactly that state: a full cube MUST occlude. The assembled halves are not full
 * cubes and cull nothing they do not cover, the way stairs and slabs do.
 */
public class WorkstationBlock extends HorizontalMachineBlock {

	public static final MapCodec<WorkstationBlock> CODEC = simpleCodec(WorkstationBlock::new);

	/** Loose casing, lower half or upper half. See {@link WorkstationPart}. */
	public static final EnumProperty<WorkstationPart> PART =
			EnumProperty.create("part", WorkstationPart.class);

	/** Screens alight: the machine is assembled and has energy. Drives the model and, later, animation. */
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/**
	 * Silhouettes, built once per geometry at class-init and read by lookup.
	 *
	 * <p>Assembling a shape inside {@code getShape} is paid twenty times per state, not once —
	 * vanilla's state cache asks for it that often while it fills itself (ADR-023). Three parts times
	 * four facings is twelve shapes, and the {@code lit} flag is texture rather than geometry, so it
	 * does not multiply anything.
	 *
	 * <p>The numbers are measured off the generated models, not eyeballed. The lower half's mass spans
	 * z 0.5…15.5 for its full height. The upper half is ONE box on purpose: its real content is a
	 * bottom plate at z 1…8, two one-pixel posts at z 14…15 and the monitor block at z 7.6…13.9, and a
	 * shape built out of those pieces draws a separate wireframe cuboid around each — which is what the
	 * player sees when they look at the machine, not the machine itself. One box that hugs the arm
	 * (x 2…14, up to the top of the screens) reads as the single object the arm actually is.
	 *
	 * <p>The wings are deliberately outside the shape: a collision box beyond 0…16 misbehaves, so they
	 * are visible but not clickable, exactly as the energy condenser's overhang already is.
	 */
	private static final Map<Direction, VoxelShape> LOWER_SHAPES =
			Shapes.rotateHorizontal(Block.box(0.0, 0.0, 0.5, 16.0, 16.0, 15.5));

	private static final Map<Direction, VoxelShape> UPPER_SHAPES =
			Shapes.rotateHorizontal(Block.box(2.0, 0.0, 1.0, 14.0, 12.5, 15.0));

	public WorkstationBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState()
				.setValue(PART, WorkstationPart.SINGLE)
				.setValue(LIT, false));
	}

	@Override
	protected MapCodec<? extends WorkstationBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(PART, LIT);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		// Every state gets one: a state whose block is an EntityBlock has to produce a block entity,
		// and the upper half needs one of its own so it can carry a renderer. The casing's and the
		// upper half's are inert — no ticker, no energy role.
		return new WorkstationBlockEntity(pos, state);
	}

	/**
	 * Only the lower half ticks. The casing has nothing to do, and the upper half's block entity
	 * exists for the renderer rather than for logic — the machine's buffer and its lit state live in
	 * the lower half alone, the same split the reactor door uses for its animation clock.
	 */
	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (state.getValue(PART) != WorkstationPart.LOWER) {
			return null;
		}
		if (level.isClientSide()) {
			// The animation clock, and only on this half: both halves read it, so a second one would
			// only give the seam a way to tear.
			return (lvl, pos, st, be) -> {
				if (be instanceof WorkstationBlockEntity machine) {
					machine.clientTick(st, lvl.getGameTime());
				}
			};
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
	 * Right-clicking an assembled machine reports what it is doing, in one chat line.
	 *
	 * <p>A line rather than a screen while there is nothing to show: the first iteration has no
	 * upgrades to buy, and a menu obliges a screen, two manifest entries, a literal in the parity gate
	 * and a frame in the shot catalogue. The crystal farm controller makes the same trade for the same
	 * reason. The line still has to exist, because "is it powered?" is otherwise only answerable by
	 * staring at the screens.
	 *
	 * <p><b>A loose casing passes the click through, and that is load-bearing.</b> Consuming a
	 * right-click stops vanilla from falling through to block placement (MOD-039, the defect that once
	 * made a cable unplaceable against another cable) — and the whole mechanic here is placing a second
	 * casing on top of the first. A casing that answered the click would make the machine unbuildable
	 * by the only route the player has.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (!state.getValue(PART).assembled()) {
			return InteractionResult.PASS;
		}
		BlockPos lower = lowerPos(state, pos);
		// No power, no screen. LIT is written by the block entity whenever the buffer holds charge, so
		// this is the same answer the fans and monitors give: a dark station is a dead station.
		if (!state.getValue(LIT)) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendSystemMessage(
						Component.translatable("message.alaindustrial.workstation.no_power"), true);
			}
			return InteractionResult.CONSUME;
		}
		if (level.isClientSide()) {
			// MOD-483: the skill wheel is a client-only screen. It is opened from the LOWER half whichever
			// half was clicked, because that is the position every purchase packet names and the one the
			// server re-reads to check reach.
			SkillTreeClientAccess.open(lower);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer,
			ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		tryAssemble(level, pos);
	}

	/**
	 * A neighbour changed — a casing may have appeared above or below. The guard inside
	 * {@link #tryAssemble} keeps this from recursing: it does nothing unless the block is still a
	 * loose casing, and assembly writes halves, not casings.
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		tryAssemble(level, pos);
	}

	/**
	 * A half that has lost its partner becomes a casing again.
	 *
	 * <p>This is the whole disassembly story. Every way a block can leave the world ends in a
	 * neighbour update, so the player's pickaxe, an explosion, {@code /setblock}, a piston and a lost
	 * support all arrive here and get the same answer. Each half still drops its own casing through
	 * the ordinary loot table, and the survivor is left standing as a casing, so the player always
	 * gets back exactly what they put in.
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
	 * Put two stacked casings together into one machine.
	 *
	 * <p>{@code public static} on purpose: a game test places blocks straight into the world, and a
	 * programmatic placement never calls {@code setPlacedBy} (MOD-015). A scenario that could not
	 * reach this hook would have to assert on a machine it has no way to build.
	 *
	 * <p>The pair prefers to grow downward — a casing placed on top of another makes the lower one
	 * the machine — so that a stack of three resolves the same way every time instead of depending on
	 * which block the player touched last. The facing comes from the lower casing for the same
	 * reason: the base is the machine, and people build upward.
	 */
	public static void tryAssemble(Level level, BlockPos pos) {
		if (level.isClientSide()) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!isCasing(state)) {
			return; // already half of a machine — and this is what stops neighbourChanged recursing
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

	/** Both halves show the same lit state, so the machine reads as one object. */
	public static void setLit(Level level, BlockPos lower, boolean lit) {
		for (BlockPos pos : new BlockPos[] {lower, lower.above()}) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof WorkstationBlock && state.getValue(PART).assembled()
					&& state.getValue(LIT) != lit) {
				level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
			}
		}
	}

	/**
	 * A cable draws an arm only toward the assembled lower half.
	 *
	 * <p>Caught by TC-CABLE-FACE-PARITY rather than by review: the inherited rule says "every face but
	 * the front", which is right for a machine but wrong for a block whose default state is a loose
	 * casing that accepts nothing. An arm toward a face that takes no EU reads to the player as a
	 * working joint, which is exactly the defect MOD-194 and MOD-199 exist to stop. The condition here
	 * has to stay the mirror of {@code WorkstationBlockEntity.energyRoleForFace}.
	 */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		if (state.getValue(PART) != WorkstationPart.LOWER) {
			return false;
		}
		return super.isCableConnectable(state, side);
	}

	/** Where the machine's brain lives for a block at {@code pos}; the position itself if unassembled. */
	public static BlockPos lowerPos(BlockState state, BlockPos pos) {
		return state.getBlock() instanceof WorkstationBlock
				&& state.getValue(PART) == WorkstationPart.UPPER ? pos.below() : pos;
	}

	private static boolean isCasing(BlockState state) {
		return state.getBlock() instanceof WorkstationBlock
				&& state.getValue(PART) == WorkstationPart.SINGLE;
	}

	private static boolean isPartner(BlockState state, WorkstationPart part) {
		return state.getBlock() instanceof WorkstationBlock && state.getValue(PART) == part.partner();
	}
}
