package dev.alaindustrial.item.misc;

import dev.alaindustrial.compat.Bonemeal;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherFungusBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Applies reinforced bone meal to one cell (MOD-906): the growth of ordinary bone meal with the luck taken
 * out. Not tied to a player — the item's {@code useOn} calls it, and so will the automation of MOD-911.
 *
 * <p>The rules, from the owner's decisions in the task:
 * <ul>
 * <li>a sapling becomes a tree: both growth steps at once, no 45 % roll; if the tree has no room, the
 * sapling is put back exactly as it was;</li>
 * <li>a crop (wheat and the other {@link CropBlock}s, stems, cocoa, sweet berries, the pitcher) ripens at once;</li>
 * <li>everything else gets one ordinary bone-meal step, the roll retried until it passes
 * ({@link #ROLL_TRIES}) — a random roll passes, a hard condition (soil, light, room) never does. That is
 * how the mod's own plants keep their gates (the trellis refuses in {@code isBonemealSuccess}) and how a
 * foreign plant gets the guarantee without this class knowing it;</li>
 * <li>if nothing grew, nothing is spent and the world is unchanged ({@link Outcome#NOTHING}).</li>
 * </ul>
 * Every call into {@link BonemealableBlock} goes through the version facade {@link Bonemeal} (ADR-036).
 */
public final class ReinforcedBonemeal {

	/** How many times the success roll is retried before a plant is taken to refuse for a real reason. */
	public static final int ROLL_TRIES = 64;
	/** Upper bound on bone-meal steps that ripen one crop; vanilla needs at most four. */
	public static final int MAX_CROP_STEPS = 32;
	/** Bone-meal steps a sapling gets: stage 0 to 1, then up to three tries of the tree itself. */
	public static final int MAX_SAPLING_STEPS = 4;

	/** What one application did. */
	public enum Outcome {
		/** Something grew: spend one meal, show the growth particles. */
		GREW,
		/** Nothing grew and nothing changed: the meal stays. */
		NOTHING,
		/**
		 * Another mod took the application over through the loader's bone-meal event and answered "success":
		 * the growth particles show, but that mod owns the stack — the meal is NOT spent here, exactly as
		 * NeoForge's patched {@code BoneMealItem.applyBonemeal} returns {@code isSuccessful()} without a shrink.
		 */
		HANDLED_ELSEWHERE
	}

	/** The loader's answer before any growth. */
	public enum Verdict {
		PROCEED,
		DENIED,
		HANDLED
	}

	/** Loader seam for the bone-meal event (pattern of {@code ChestPairHooks}). */
	public interface LoaderGate {
		Verdict check(@Nullable Player player, Level level, BlockPos pos, BlockState state, ItemStack stack);
	}

	/**
	 * Installed by the NeoForge entry point: fires NeoForge's {@code BonemealEvent}, which protection and
	 * moderation mods cancel. Fabric has no such event and leaves this {@code null}.
	 */
	public static volatile @Nullable LoaderGate LOADER_GATE;

	private ReinforcedBonemeal() {
	}

	/**
	 * Applies the meal at {@code pos}. {@code face} is the clicked face (for growth on the sea floor), or
	 * {@code null} when there is none. Spending the item is the caller's job, by the returned outcome.
	 */
	public static Outcome apply(ServerLevel level, BlockPos pos, @Nullable Direction face, @Nullable Player player,
			ItemStack stack) {
		BlockState state = level.getBlockState(pos);
		if (state.is(ModTags.Blocks.REINFORCED_BONE_MEAL_BLACKLIST)) {
			return Outcome.NOTHING;
		}
		LoaderGate gate = LOADER_GATE;
		if (gate != null) {
			Verdict verdict = gate.check(player, level, pos, state, stack);
			if (verdict == Verdict.DENIED) {
				return Outcome.NOTHING;
			}
			if (verdict == Verdict.HANDLED) {
				level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
				return Outcome.HANDLED_ELSEWHERE;
			}
		}
		if (state.getBlock() instanceof BonemealableBlock bonemealable
				&& Bonemeal.isValidTarget(bonemealable, level, pos, state)) {
			if (!grow(bonemealable, level, pos, state)) {
				return Outcome.NOTHING;
			}
			level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
			return Outcome.GREW;
		}
		if (face != null && state.isFaceSturdy(level, pos, face)) {
			BlockPos front = pos.relative(face);
			// Seagrass and coral spread the way ordinary meal spreads them; a copy, so this class never
			// spends the caller's stack itself.
			if (BoneMealItem.growWaterPlant(stack.copyWithCount(1), level, front, face)) {
				level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, front, 15);
				return Outcome.GREW;
			}
		}
		return Outcome.NOTHING;
	}

	/**
	 * Whether a click with the meal at {@code pos} could grow anything — the client's prediction, which
	 * runs no growth and rolls nothing.
	 */
	public static boolean looksApplicable(Level level, BlockPos pos, @Nullable Direction face) {
		BlockState state = level.getBlockState(pos);
		if (state.is(ModTags.Blocks.REINFORCED_BONE_MEAL_BLACKLIST)) {
			return false;
		}
		if (state.getBlock() instanceof BonemealableBlock bonemealable) {
			return Bonemeal.isValidTarget(bonemealable, level, pos, state);
		}
		return face != null && state.isFaceSturdy(level, pos, face)
				&& level.getFluidState(pos.relative(face)).isSource();
	}

	/**
	 * Grows {@code state} at {@code pos}, already known to be a valid target of {@code block}; returns
	 * whether anything grew. Public for the gametest, which hands it a stand-in plant.
	 */
	public static boolean grow(BonemealableBlock block, ServerLevel level, BlockPos pos, BlockState state) {
		Block type = state.getBlock();
		if (type instanceof SaplingBlock && !isHangingPropagule(state)) {
			return growSapling(block, level, pos, state);
		}
		if (type instanceof CropBlock || type instanceof StemBlock || type instanceof CocoaBlock
				|| type instanceof SweetBerryBushBlock || type instanceof PitcherCropBlock) {
			return ripen(block, level, pos, state);
		}
		if (!rolls(block, level, pos, state)) {
			return false;
		}
		Bonemeal.perform(block, level, level.getRandom(), pos, state);
		if (type instanceof MushroomBlock || type instanceof NetherFungusBlock || type instanceof AzaleaBlock) {
			// These grow a feature that may not fit; a failed one leaves the plant where it was.
			return level.getBlockState(pos) != state;
		}
		return true;
	}

	/** A sapling skips the roll: step it until it is no longer a sapling, or put it back. */
	private static boolean growSapling(BonemealableBlock block, ServerLevel level, BlockPos pos, BlockState state) {
		Block sapling = state.getBlock();
		for (int i = 0; i < MAX_SAPLING_STEPS; i++) {
			BlockState now = level.getBlockState(pos);
			if (!now.is(sapling)) {
				return true;
			}
			Bonemeal.perform(block, level, level.getRandom(), pos, now);
		}
		if (!level.getBlockState(pos).is(sapling)) {
			return true;
		}
		// No room for the tree: the first step already wrote stage 1, which would be a free half-growth.
		level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		return false;
	}

	/**
	 * Steps a crop until it stops being a valid target (ripe), up to {@link #MAX_CROP_STEPS}. A step that
	 * changed nothing does not end the loop: beetroots add {@code 2..5 / 3} stages, a zero one time in four.
	 */
	private static boolean ripen(BonemealableBlock block, ServerLevel level, BlockPos pos, BlockState state) {
		boolean grew = false;
		Block crop = state.getBlock();
		for (int i = 0; i < MAX_CROP_STEPS; i++) {
			BlockState now = level.getBlockState(pos);
			if (!now.is(crop) || !Bonemeal.isValidTarget(block, level, pos, now) || !rolls(block, level, pos, now)) {
				break;
			}
			Bonemeal.perform(block, level, level.getRandom(), pos, now);
			grew |= level.getBlockState(pos) != now;
		}
		return grew;
	}

	private static boolean rolls(BonemealableBlock block, ServerLevel level, BlockPos pos, BlockState state) {
		for (int i = 0; i < ROLL_TRIES; i++) {
			if (Bonemeal.isSuccess(block, level, level.getRandom(), pos, state)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isHangingPropagule(BlockState state) {
		return state.hasProperty(MangrovePropaguleBlock.HANGING) && state.getValue(MangrovePropaguleBlock.HANGING);
	}
}
