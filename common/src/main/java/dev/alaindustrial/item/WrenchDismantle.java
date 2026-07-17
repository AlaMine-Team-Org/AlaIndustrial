package dev.alaindustrial.item;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shift + right-click with the wrench unbolts one of the mod's own blocks: it drops as an item, keeping
 * whatever it stored, without mining it (MOD-108).
 *
 * <p>Scope is deliberately "blocks of this mod": a wrench that dismantled dirt, or another mod's
 * machine, would be a griefing tool rather than a service tool. The check is the registry namespace,
 * so every present and future block of ours is covered without a list to maintain.
 *
 * <p>The drop goes through the block's own loot table ({@code Block.dropResources}) rather than a
 * hand-made {@code ItemStack}, so a machine keeps the data its loot table preserves — a charged Battery
 * Box stays charged, a filled pump keeps its fluid — exactly as if it had been mined.
 */
public final class WrenchDismantle {

	/**
	 * Try to unbolt the block at {@code pos}. Returns false when it is not ours (the caller then treats the
	 * click as unhandled), true when it was taken apart.
	 */
	public static boolean dismantle(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack wrench) {
		BlockState state = level.getBlockState(pos);
		if (!isOurs(state)) {
			return false;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		// playerWillDestroy first: that is where a machine spills its inventory (AbstractMachineBlock),
		// and where a cable/pipe leaves its network. Skipping it would void whatever sat in the slots.
		state.getBlock().playerWillDestroy(level, pos, state, player);
		Block.dropResources(state, level, pos, blockEntity, player, wrench);
		level.removeBlock(pos, false);
		// Short metallic click — "a bolt came loose". Vanilla sound: the mod ships no wrench audio, and a
		// fabricated .ogg is not something this task should invent.
		level.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.BLOCKS, 0.8F, 0.9F);
		return true;
	}

	/** True for blocks registered by this mod. */
	private static boolean isOurs(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(Industrialization.MOD_ID);
	}

	private WrenchDismantle() {}
}
