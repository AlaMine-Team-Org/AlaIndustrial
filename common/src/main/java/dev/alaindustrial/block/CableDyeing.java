package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.core.energy.CableType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Right-clicking an insulated cable with a dye (MOD-666): a plain click dyes the clicked segment,
 * Shift dyes the whole connected run of the same grade.
 *
 * <p>Wired as an early right-click hook on both loaders (Fabric {@code UseBlockCallback}, NeoForge
 * {@code RightClickBlock}) rather than from {@link CableBlock#useItemOn}: vanilla skips block
 * interaction altogether when a sneaking player holds an item, so the Shift variant would never reach
 * the block.
 *
 * <p>Cost: one dye per segment for a single click; a Shift run charges one dye per
 * {@link #SEGMENTS_PER_DYE} segments, the same eight-for-one as the crafting recipe, and dyes as far
 * as the dyes in hand reach. A segment that already has the colour is neither repainted nor charged.
 */
public final class CableDyeing {

	/** Segments one dye covers in a Shift run — the crafting recipe's ratio. */
	public static final int SEGMENTS_PER_DYE = 8;

	/** Most segments one Shift click walks, so a click on a base-spanning grid stays cheap. */
	public static final int MAX_RUN = 256;

	private CableDyeing() {
	}

	/** The early-hook entry point; {@link InteractionResult#PASS} for anything that is not a dye on a cable. */
	public static InteractionResult tryDye(Level level, @Nullable Player player, InteractionHand hand,
			@Nullable BlockHitResult hit) {
		if (player == null || hit == null || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		DyeColor dye = stack.get(DataComponents.DYE);
		if (dye == null) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof CableBlock cable)) {
			return InteractionResult.PASS;
		}
		if (!player.mayBuild() || !level.mayInteract(player, pos)) {
			return InteractionResult.PASS;
		}
		if (!cable.type().isInsulated()) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.alaindustrial.cable_dye.bare"));
			}
			return InteractionResult.SUCCESS;
		}
		List<CableBlockEntity> targets = player.isSecondaryUseActive()
				? run(level, pos, cable.type(), dye)
				: single(level, pos, dye);
		if (targets.isEmpty()) {
			return InteractionResult.SUCCESS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		boolean free = player.getAbilities().instabuild;
		int budget = free ? targets.size()
				: player.isSecondaryUseActive() ? stack.getCount() * SEGMENTS_PER_DYE : Math.min(1, stack.getCount());
		int painted = 0;
		for (CableBlockEntity target : targets) {
			if (painted >= budget) {
				break;
			}
			if (target.setColor(dye)) {
				painted++;
			}
		}
		if (painted > 0) {
			if (!free) {
				int cost = player.isSecondaryUseActive() ? (painted + SEGMENTS_PER_DYE - 1) / SEGMENTS_PER_DYE : 1;
				stack.consume(cost, player);
			}
			level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
		}
		return InteractionResult.SUCCESS;
	}

	private static List<CableBlockEntity> single(Level level, BlockPos pos, DyeColor dye) {
		return level.getBlockEntity(pos) instanceof CableBlockEntity cable && cable.color() != dye
				? List.of(cable)
				: List.of();
	}

	/**
	 * The segments a Shift click repaints, nearest first: every insulated segment of {@code grade}
	 * reachable from {@code start} through drawn connections, up to {@link #MAX_RUN}, skipping those
	 * that already carry {@code dye}. A connection into another grade, a bare cable or a machine ends
	 * the run there.
	 */
	public static List<CableBlockEntity> run(Level level, BlockPos start, CableType grade, DyeColor dye) {
		List<CableBlockEntity> out = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);
		while (!queue.isEmpty() && seen.size() <= MAX_RUN) {
			BlockPos pos = queue.poll();
			BlockState state = level.getBlockState(pos);
			if (!(level.getBlockEntity(pos) instanceof CableBlockEntity cable)) {
				continue;
			}
			if (cable.color() != dye) {
				out.add(cable);
			}
			for (Direction dir : Direction.values()) {
				if (!state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir))) {
					continue;
				}
				BlockPos next = pos.relative(dir);
				if (seen.contains(next) || !level.isLoaded(next)) {
					continue;
				}
				BlockState nextState = level.getBlockState(next);
				if (nextState.getBlock() instanceof CableBlock other && other.type() == grade) {
					seen.add(next);
					queue.add(next);
				}
			}
		}
		return out;
	}
}
