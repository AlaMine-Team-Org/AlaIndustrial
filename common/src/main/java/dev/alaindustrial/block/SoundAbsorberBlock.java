package dev.alaindustrial.block;

import dev.alaindustrial.core.acoustic.AbsorberTier;
import dev.alaindustrial.core.tooltip.HasMachineTooltip;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec.Line;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec.Shown;
import dev.alaindustrial.core.tooltip.MachineTooltipSpec.Tone;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Sound absorber (MOD-804): a full cube of tempered iron frame, perforated grille and padding. Absorbers that
 * touch by a face form a group of any shape; the group's size sets its {@link AbsorberTier}, and the client
 * muffles block and ambient sounds inside the group's zone. The block itself does nothing on the server but
 * keep its state true — work, energy and redstone of everything around it are untouched.
 *
 * <h2>State</h2>
 *
 * <p>Six booleans (the {@link PipeBlock} set, as the storage module) say which faces touch another absorber —
 * the model drops the frame on those edges, so a group reads as one panel — and {@link #TIER} is the group's
 * tier, which lights that many of the five lamps on the outer frame and is what the client reads to build the
 * zones: no packet carries anything, the block states are the whole protocol.
 *
 * <p>The model is a generated multipart partition — run {@code python tools/gen_sound_absorber_assets.py};
 * {@code --check} (checkAll, pre-commit) proves every face of every state is drawn exactly once and matches the
 * approved picture.
 *
 * <h2>Why the refresh walks sixteen steps</h2>
 *
 * <p>A seam only changes on the six neighbours of a placed or broken block, but the tier is a property of the
 * whole group. It only changes for a block whose group (before or after the change) was smaller than
 * {@link AbsorberTier#SATURATION}: past sixteen blocks every group is tier V and stays V. Such a group touches
 * the changed position and holds fewer than sixteen blocks, so every block whose tier can change lies within
 * {@value #REFRESH_RADIUS} steps through absorbers of the change — that is the walk {@link #refresh} does,
 * rewriting only the states that came out different. Counting a group never goes past the saturation either.
 * The rewrite skips {@code onPlace} so it cannot re-enter itself.
 */
public class SoundAbsorberBlock extends Block implements HasMachineTooltip {

	/** The group's tier, 1..5 — the number of lit lamps. */
	public static final IntegerProperty TIER = IntegerProperty.create("tier", 1, AbsorberTier.values().length);

	private static final int REFRESH_RADIUS = AbsorberTier.SATURATION;

	private static final int REFRESH_FLAGS =
			Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;

	public SoundAbsorberBlock(Properties properties) {
		super(properties);
		BlockState state = stateDefinition.any().setValue(TIER, 1);
		for (BooleanProperty joined : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
			state = state.setValue(joined, false);
		}
		registerDefaultState(state);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(builder::add);
		builder.add(TIER);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return truthAt(context.getLevel(), context.getClickedPos(), defaultBlockState());
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (level instanceof ServerLevel serverLevel && !oldState.is(this)) {
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
	 * {@code state} with its seams and tier set to the truth at {@code pos}. {@code pos} counts as an absorber
	 * whether or not one stands there yet, so this answers for one being placed too.
	 */
	public static BlockState truthAt(Level level, BlockPos pos, BlockState state) {
		for (Map.Entry<Direction, BooleanProperty> entry : PipeBlock.PROPERTY_BY_DIRECTION.entrySet()) {
			state = state.setValue(entry.getValue(), isAbsorber(level, pos.relative(entry.getKey())));
		}
		return state.setValue(TIER, AbsorberTier.forGroupSize(groupSize(level, pos, AbsorberTier.SATURATION)).level());
	}

	/**
	 * How many absorbers the face-connected group at {@code pos} holds, counting no further than
	 * {@code limit}. {@code pos} itself counts without a block check.
	 */
	public static int groupSize(Level level, BlockPos pos, int limit) {
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
				if (seen.add(next) && isAbsorber(level, next)) {
					frontier.add(next);
				}
			}
		}
		return found;
	}

	/** Re-derive every absorber a change at {@code changed} could have moved (see the class comment). */
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
					if (seen.add(neighbour) && isAbsorber(level, neighbour)) {
						next.add(neighbour);
					}
				}
			}
			current = next;
		}
	}

	private static void reseat(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof SoundAbsorberBlock)) {
			return;
		}
		BlockState wanted = truthAt(level, pos, state);
		if (wanted != state) {
			level.setBlock(pos, wanted, REFRESH_FLAGS);
		}
	}

	/** Whether an absorber stands at {@code pos}; an unloaded chunk answers no and is never forced. */
	private static boolean isAbsorber(Level level, BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof SoundAbsorberBlock;
	}

	/** The item tooltip: what it does, and the scale under [SHIFT]. */
	@Override
	public MachineTooltipSpec machineTooltip() {
		List<Line> ladder = new ArrayList<>();
		for (AbsorberTier tier : AbsorberTier.values()) {
			String blocks = tier == AbsorberTier.V ? tier.minBlocks() + "+"
					: tier.minBlocks() == tier.maxBlocks() ? Integer.toString(tier.minBlocks())
					: tier.minBlocks() + "–" + tier.maxBlocks();
			ladder.add(new Line("tooltip.alaindustrial.sound_absorber.tier",
					List.of(tier::name, () -> blocks, tier::radius, tier::dampeningPercent),
					Tone.GRAY, Shown.ALWAYS, null));
		}
		ladder.add(MachineTooltipSpec.note("tooltip.alaindustrial.sound_absorber.exempt", Tone.DARK_GRAY));
		ladder.add(MachineTooltipSpec.note("tooltip.alaindustrial.sound_absorber.exempt2", Tone.DARK_GRAY));
		return new MachineTooltipSpec(null,
				List.of(MachineTooltipSpec.note("tooltip.alaindustrial.sound_absorber.desc", Tone.GRAY),
						MachineTooltipSpec.note("tooltip.alaindustrial.sound_absorber.desc2", Tone.GRAY)),
				ladder);
	}
}
