package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.StorageModuleBlockEntity;
import dev.alaindustrial.storage.StorageCluster;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Storage module — one block of the modular warehouse (MOD-287). A plain full cube with no facing:
 * modules are stacked and butted together in whatever shape the player likes, and every face-adjacent
 * neighbour joins the same warehouse.
 *
 * <p>Right-clicking any module opens the window of the whole cluster it belongs to, not just its own
 * 27 slots. That falls out of the base class for free: {@link AbstractMachineBlock#useWithoutItem}
 * opens whatever {@code MenuProvider} sits at the clicked position, and
 * {@link StorageModuleBlockEntity#createMenu} walks the cluster from itself. Extending
 * {@code AbstractMachineBlock} is a naming quirk rather than a claim — the module is not a machine and
 * has no energy; the base is simply "block entity + open the menu on right-click", which is also why
 * the iron chest extends it.
 *
 * <p>Breaking a module drops <b>its own</b> contents and nothing else. That is vanilla behaviour, not
 * an override: {@code BlockEntity.preRemoveSideEffects} spills any {@code Container} block entity
 * during {@code setBlockState}. It is also the whole reason items live in the module they were put
 * in — a shared master inventory would have to decide what to drop and would open the door to the
 * negative-capacity class of bugs seen in other storage mods.
 *
 * <h2>Connected textures (MOD-287, visuals)</h2>
 *
 * <p>Six boolean properties (the {@link PipeBlock} set, same as {@link CableBlock}) say which faces
 * are joined to a neighbouring module, so a wall of modules reads as a single cabinet with drawer
 * fronts running across it and a border only on the outside.
 *
 * <p>The connection is drawn <b>into</b> the model, not over it. Every face of the block is cut into
 * non-overlapping rectangles and each rectangle is drawn once, from the framed texture or the open
 * one, by the multipart entry that owns it; nothing is offset and nothing overlaps. The first
 * version instead laid patch strips 0.1 blocks proud of the surface, which the player could see as a
 * raised band on the cabinet top and a black line where a strip ended. Blockstate and models are
 * generated — do not hand-edit them; run {@code python tools/gen_storage_module_models.py} and let
 * {@code --check} (in {@code checkAll} and the pre-commit hook) prove the joint is still seamless.
 *
 * <h3>Why a flag is not simply "there is a module on that side"</h3>
 *
 * <p>A warehouse caps at {@link StorageCluster#MAX_MODULES} modules, so two modules can touch and
 * still belong to different warehouses. Worse, in a group of five or more there is no fixed answer at
 * all: {@link StorageCluster#of} walks from the module that was clicked, so clicking one end or the
 * other of a five-long row gives two different foursomes. A seam is a claim that the two blocks it
 * joins are one warehouse, and in an over-sized group that claim cannot be made truthfully about any
 * pair.
 *
 * <p>So the flag is set only when the module's <b>whole face-connected group</b> fits inside the cap
 * ({@link #groupSize}). Then every walk, from whichever member, returns that same group, and the seam
 * is telling the truth for all of them. Add a fifth module and every seam in the group disappears at
 * once — the group is no longer one warehouse and the blocks say so. That is deliberate: it is the
 * only reading that never lies, and it doubles as the in-world signal that the cap has been passed.
 *
 * <h3>Why the state is refreshed by hand instead of by {@code updateShape}</h3>
 *
 * <p>{@code updateShape} only runs on the six blocks around the change, and this flag depends on the
 * size of a group up to four blocks across: dropping a fifth module onto a row of four has to clear
 * the seam between the two modules at the far end, three blocks away. {@link #refresh} therefore
 * walks the affected neighbourhood itself — {@value #REFRESH_RADIUS} steps through modules from the
 * changed position, which is exactly far enough (a group inside the cap is at most three steps
 * across, and the change can be one step outside it) — and rewrites every state that came out
 * different. The rewrite uses {@link Block#UPDATE_SKIP_ON_PLACE} so it cannot re-enter itself, and
 * carries no {@link Block#UPDATE_NEIGHBORS}: the block is unchanged, only its state, so the chunk
 * keeps the block entity and its items untouched.
 */
public class StorageModuleBlock extends AbstractMachineBlock {
	public static final MapCodec<StorageModuleBlock> CODEC = simpleCodec(StorageModuleBlock::new);

	/**
	 * How far a placement or a break can change another module's seams, in steps through modules.
	 * A group that fits the cap spans at most {@code MAX_MODULES - 1} steps, and the block that
	 * changed can be one step outside the group it just broke up — hence the cap itself.
	 */
	private static final int REFRESH_RADIUS = StorageCluster.MAX_MODULES;

	/**
	 * Flags for the seam rewrite: tell the clients, skip the shape/neighbour cascade (nothing about
	 * the block's shape changed) and skip {@code onPlace}, which is what would otherwise re-enter
	 * {@link #refresh} once per rewritten block.
	 */
	private static final int REFRESH_FLAGS =
			Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;

	public StorageModuleBlock(Properties properties) {
		super(properties);
		BlockState state = stateDefinition.any();
		for (BooleanProperty joined : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
			state = state.setValue(joined, false);
		}
		registerDefaultState(state);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StorageModuleBlockEntity(pos, state);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(builder::add);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// The module is not in the world yet, which is exactly what joined() assumes about its origin.
		return joined(context.getLevel(), context.getClickedPos(), defaultBlockState());
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
			boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (level instanceof ServerLevel serverLevel) {
			refresh(serverLevel, pos);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		refresh(level, pos);
	}

	/**
	 * {@code state} with every seam flag set to the truth at {@code pos}: joined to the neighbour in
	 * that direction, and only while the whole group is one warehouse. See the class comment for why
	 * the group size gates all six flags together.
	 *
	 * <p>{@code pos} counts as a module whether or not one stands there yet, so this answers both for
	 * a module being placed and for one already in the world.
	 */
	public static BlockState joined(LevelReader level, BlockPos pos, BlockState state) {
		boolean oneWarehouse = groupSize(level, pos) <= StorageCluster.MAX_MODULES;
		for (Map.Entry<Direction, BooleanProperty> entry : PipeBlock.PROPERTY_BY_DIRECTION.entrySet()) {
			boolean joined = oneWarehouse && isModule(level, pos.relative(entry.getKey()));
			state = state.setValue(entry.getValue(), joined);
		}
		return state;
	}

	/**
	 * How many modules the face-connected group at {@code pos} holds, counting no further than one
	 * past the cap — the caller only ever asks "does it still fit". {@code pos} itself is counted
	 * without a block check, so a module about to be placed is included.
	 */
	public static int groupSize(LevelReader level, BlockPos pos) {
		int limit = StorageCluster.MAX_MODULES + 1;
		Set<BlockPos> seen = new HashSet<>();
		List<BlockPos> frontier = new ArrayList<>();
		seen.add(pos);
		frontier.add(pos);
		int found = 0;
		while (!frontier.isEmpty() && found < limit) {
			BlockPos current = frontier.remove(frontier.size() - 1);
			found++;
			for (Direction dir : Direction.values()) {
				BlockPos next = current.relative(dir);
				if (seen.add(next) && isModule(level, next)) {
					frontier.add(next);
				}
			}
		}
		return found;
	}

	/**
	 * Re-derive the seams of every module a change at {@code changed} could have moved, and write back
	 * the ones that came out different. {@code changed} may be air — that is the break case.
	 */
	private static void refresh(ServerLevel level, BlockPos changed) {
		Set<BlockPos> seen = new HashSet<>();
		List<BlockPos> current = new ArrayList<>();
		seen.add(changed);
		current.add(changed);

		for (int step = 0; step <= REFRESH_RADIUS && !current.isEmpty(); step++) {
			List<BlockPos> next = new ArrayList<>();
			for (BlockPos pos : current) {
				reseat(level, pos);
				if (step == REFRESH_RADIUS) {
					continue;
				}
				for (Direction dir : Direction.values()) {
					BlockPos neighbour = pos.relative(dir);
					if (seen.add(neighbour) && isModule(level, neighbour)) {
						next.add(neighbour);
					}
				}
			}
			current = next;
		}
	}

	/** Rewrite one module's seams if they no longer match the world. A no-op on anything else. */
	private static void reseat(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof StorageModuleBlock)) {
			return;
		}
		BlockState wanted = joined(level, pos, state);
		if (wanted != state) {
			level.setBlock(pos, wanted, REFRESH_FLAGS);
		}
	}

	/**
	 * Whether a storage module stands at {@code pos}. An unloaded chunk answers "no" and is never
	 * forced — the same rule {@link StorageCluster#of} follows, so the seams describe the warehouse
	 * the player would actually get.
	 */
	private static boolean isModule(LevelReader level, BlockPos pos) {
		return level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof StorageModuleBlock;
	}
}
