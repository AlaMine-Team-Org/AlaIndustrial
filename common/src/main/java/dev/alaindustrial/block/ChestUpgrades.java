package dev.alaindustrial.block;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.AbstractChestBlockEntity;
import dev.alaindustrial.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/**
 * Raises a placed chest one tier in place (MOD-944), keeping every item in its slot, the custom name, the
 * lock, the facing and the water. A double chest is raised as a whole pair, one upgrade item per half.
 *
 * <p><b>The order is the safety.</b> Everything runs on the server inside one {@code useOn}: refuse first,
 * then close the windows of whoever looks into either half, then for each half take a snapshot of its block
 * entity, set the new tier's block over it and load the snapshot into the new block entity, and only then
 * spend the items. The old chest is never emptied beforehand. {@link #SWAP_FLAGS} carries
 * {@link Block#UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS}, which is what stops {@code LevelChunk.setBlockState}
 * from calling {@code preRemoveSideEffects} — the vanilla spill of a container's contents — on the old chest;
 * without it the items would land on the ground AND in the new chest. On NeoForge {@code useOn} runs inside
 * a block-snapshot capture whose snapshot is taken at {@code setBlock}: a full old chest there is what a
 * cancelled {@code EntityPlaceEvent} restores, and the restore carries the same flag, so the new chest does
 * not spill either.
 */
public final class ChestUpgrades {
	/** The flags a half is swapped with; see the class comment for why 256 is in them. */
	public static final int SWAP_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
	/**
	 * How long after the swap the lid bounces. A block event sent in the same tick can reach the client ahead
	 * of the block change and land in the old block entity there, so the bounce goes out through a later
	 * scheduled tick of the new block.
	 */
	private static final int BOUNCE_DELAY_TICKS = 2;
	private static final int SPARKS_PER_HALF = 24;
	/** The refusal file is already a quiet cue (-24 LUFS); this keeps it under the upgrade sound. */
	private static final float REFUSAL_VOLUME = 0.7F;

	private ChestUpgrades() {
	}

	/** Upgrade the chest at {@code pos} with {@code stack} held by {@code player}. */
	public static InteractionResult upgrade(ServerLevel level, BlockPos pos, Player player, ItemStack stack,
			Direction face, ChestUpgrade upgrade) {
		return upgrade(level, pos, player, stack, face, upgrade, SWAP_FLAGS);
	}

	/**
	 * {@link #upgrade(ServerLevel, BlockPos, Player, ItemStack, Direction, ChestUpgrade)} with the swap flags
	 * as a parameter. Production code always passes {@link #SWAP_FLAGS}; the flags are open only so the
	 * negative-control gametest can drop {@link Block#UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS} and watch the
	 * contents spill — proof that the test would notice the flag going missing.
	 */
	public static InteractionResult upgrade(ServerLevel level, BlockPos pos, Player player, ItemStack stack,
			Direction face, ChestUpgrade upgrade, int swapFlags) {
		BlockState clicked = level.getBlockState(pos);
		if (!upgrade.accepts(clicked)) {
			refuse(level, pos, player, clicked.getBlock() instanceof ShieldingChestBlock
					? Component.translatable("message.alaindustrial.chest_upgrade.shielding")
					: Component.translatable("message.alaindustrial.chest_upgrade.wrong_chest", upgrade.sourceName()));
			return InteractionResult.FAIL;
		}
		List<BlockPos> halves = halves(level, pos, clicked, upgrade);
		if (!player.hasInfiniteMaterials() && stack.getCount() < halves.size()) {
			refuse(level, pos, player, Component.translatable("message.alaindustrial.chest_upgrade.need_two"));
			return InteractionResult.FAIL;
		}
		List<BlockState> states = new ArrayList<>();
		List<BlockEntity> chests = new ArrayList<>();
		for (BlockPos half : halves) {
			if (!player.mayUseItemAt(half, face, stack) || !level.mayInteract(player, half)) {
				return InteractionResult.FAIL;
			}
			BlockEntity be = level.getBlockEntity(half);
			if (!(be instanceof Container)) {
				return InteractionResult.FAIL;
			}
			states.add(level.getBlockState(half));
			chests.add(be);
		}
		// An active interaction by the player, like opening it: a vanilla copper chest from a structure may
		// still hold an unrolled loot table, and it is rolled for this player now (ADR-010 allows it).
		for (BlockEntity be : chests) {
			if (be instanceof RandomizableContainer pending) {
				pending.unpackLootTable(player);
			}
		}
		closeViewers(chests);
		boolean pair = halves.size() == 2;
		for (int i = 0; i < halves.size(); i++) {
			BlockState old = states.get(i);
			ChestType type = pair ? old.getValue(BlockStateProperties.CHEST_TYPE) : ChestType.SINGLE;
			swapHalf(level, halves.get(i), old, chests.get(i), upgrade.target(), type, swapFlags);
		}
		stack.consume(halves.size(), player);
		celebrate(level, halves, upgrade);
		return InteractionResult.SUCCESS;
	}

	/** The clicked chest, and its partner when the two really form a pair of this rung's source. */
	private static List<BlockPos> halves(ServerLevel level, BlockPos pos, BlockState state, ChestUpgrade upgrade) {
		ChestType type = state.getValue(BlockStateProperties.CHEST_TYPE);
		if (type == ChestType.SINGLE) {
			return List.of(pos);
		}
		Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		BlockPos partner = pos.relative(type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
		BlockState partnerState = level.getBlockState(partner);
		boolean paired = upgrade.accepts(partnerState)
				&& partnerState.getValue(BlockStateProperties.CHEST_TYPE) == type.getOpposite()
				&& partnerState.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
		// A stranded half (its partner gone or foreign) is a single chest in all but its state.
		return paired ? List.of(pos, partner) : List.of(pos);
	}

	/** Close every window into either half; a closing menu hands the cursor stack back to its player. */
	private static void closeViewers(List<BlockEntity> chests) {
		for (BlockEntity be : chests) {
			for (ContainerUser user : List.copyOf(((Container) be).getEntitiesWithContainerOpen())) {
				if (user.getLivingEntity() instanceof ServerPlayer viewer) {
					viewer.closeContainer();
				}
			}
		}
	}

	private static void swapHalf(ServerLevel level, BlockPos pos, BlockState old, BlockEntity oldChest, Block target,
			ChestType type, int swapFlags) {
		CompoundTag snapshot = oldChest.saveWithoutMetadata(level.registryAccess());
		BlockState placed = target.defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, old.getValue(BlockStateProperties.HORIZONTAL_FACING))
				.setValue(BlockStateProperties.CHEST_TYPE, type)
				.setValue(BlockStateProperties.WATERLOGGED, old.getValue(BlockStateProperties.WATERLOGGED));
		level.setBlock(pos, placed, swapFlags);
		if (level.getBlockEntity(pos) instanceof AbstractChestBlockEntity chest) {
			try (ProblemReporter.ScopedCollector problems =
					new ProblemReporter.ScopedCollector(chest.problemPath(), Industrialization.LOGGER)) {
				chest.loadWithComponents(TagValueInput.create(problems, level.registryAccess(), snapshot));
			}
			chest.setChanged();
			chest.queueUpgradeBounce();
			level.scheduleTick(pos, target, BOUNCE_DELAY_TICKS);
		}
	}

	private static void celebrate(ServerLevel level, List<BlockPos> halves, ChestUpgrade upgrade) {
		ItemParticleOption sparks = new ItemParticleOption(ParticleTypes.ITEM, upgrade.metal());
		Vec3 centre = Vec3.ZERO;
		for (BlockPos half : halves) {
			Vec3 at = Vec3.atCenterOf(half);
			centre = centre.add(at);
			level.sendParticles(sparks, at.x, at.y + 0.3, at.z, SPARKS_PER_HALF, 0.35, 0.25, 0.35, 0.12);
		}
		centre = centre.scale(1.0 / halves.size());
		level.playSound(null, centre.x, centre.y, centre.z, ModSounds.CHEST_UPGRADE.get(), SoundSource.BLOCKS,
				1.0F, 1.0F);
	}

	private static void refuse(ServerLevel level, BlockPos pos, Player player, Component why) {
		player.sendOverlayMessage(why);
		level.playSound(null, pos, ModSounds.CHEST_UPGRADE_REFUSED.get(), SoundSource.BLOCKS, REFUSAL_VOLUME, 1.0F);
	}
}
