package dev.alaindustrial.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Shared plumbing of the save-format characterization suites (MOD-701): the legacy-tag corpus, the
 * block-entity sweep and the slot-layout snapshot.
 *
 * <p>Every block entity here is built straight from its factory with the block's default state and
 * is never placed in the world. That is deliberate: placing a block runs placement hooks, a replaced
 * container spills its items into the rig, and a multiblock part reaches for its neighbours. None of
 * that is what a save-format test is about, and each of them could make a correct format look broken.
 * Both sides of every comparison are built the same way, so the comparison is like for like.
 */
final class SaveFormatTestSupport {

	private SaveFormatTestSupport() {}

	/** Where the level-free block entities claim to stand. Only the tag's own keys are compared. */
	static final BlockPos POS = new BlockPos(1, 2, 1);

	/**
	 * Parse a hand-written SNBT tag. The corpus is SNBT on purpose: it is the text a save editor
	 * shows, so a reader can check a corpus entry against a real world by eye.
	 */
	static CompoundTag parse(String snbt) {
		try {
			return TagParser.parseCompoundFully(snbt);
		} catch (CommandSyntaxException e) {
			throw new IllegalStateException("corpus entry is not valid SNBT: " + snbt, e);
		}
	}

	/** A fresh, level-free block entity of {@code block}'s default state. */
	static <T extends BlockEntity> T fresh(BlockEntityType.BlockEntitySupplier<T> factory, Block block) {
		return factory.create(POS, block.defaultBlockState());
	}

	/** Load {@code tag} into {@code be} the way a chunk load does (components included). */
	static <T extends BlockEntity> T load(T be, HolderLookup.Provider registries, CompoundTag tag) {
		be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
		return be;
	}

	/**
	 * Every scalar key of {@code expected} (anything that is neither a compound nor a list) that does
	 * not come back with the same value in {@code actual}, as {@code key: expected -> actual}. Lists
	 * and compounds are left to the caller, which checks them by meaning (items on their indices, a
	 * block state) rather than by byte shape.
	 */
	static List<String> scalarDrift(CompoundTag expected, CompoundTag actual) {
		List<String> drift = new ArrayList<>();
		for (String key : expected.keySet()) {
			Tag want = expected.get(key);
			if (want instanceof CompoundTag || want instanceof ListTag) {
				continue;
			}
			Tag got = actual.get(key);
			if (!want.equals(got)) {
				drift.add(key + ": " + want + " -> " + got);
			}
		}
		return drift;
	}
}
