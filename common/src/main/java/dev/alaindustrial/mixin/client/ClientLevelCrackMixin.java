package dev.alaindustrial.mixin.client;

import dev.alaindustrial.item.tool.DrillBore;
import dev.alaindustrial.item.tool.ElectricDrillItem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the miner's OWN extra cracks on their client (MOD-821), in the same call that draws the centre one.
 *
 * <p>The miner's centre crack is not a server packet: {@code MultiPlayerGameMode#continueDestroyBlock} ends
 * every tick with {@code level.destroyBlockProgress(localPlayer.getId(), pos, stage)} from the client's own
 * progress counter. Extras mirrored by the server would arrive a tick or a ping later and from a different
 * counter, so the sides cracked out of step with the centre. Here they are set from that very call, with the
 * very same stage, and the server's mirror skips the miner.
 *
 * <p>The extras are the blocks the stroke will really break ({@link ElectricDrillItem#plannedExtrasShown}, with the
 * client's shown charge and the prices the server synced), so a drill that cannot pay for them cracks the
 * centre alone. Slot ids are {@link DrillBore#mirrorId}; unused slots are cleared in the same pass. Optional
 * mixin config: a cosmetic overlay must never stop the game from loading.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelCrackMixin {

	@Inject(method = "destroyBlockProgress", at = @At("HEAD"))
	private void alaindustrial$mirrorOwnBoreCracks(int id, BlockPos pos, int progress, CallbackInfo ci) {
		if (id < 0) {
			return; // one of ours, or another player's mirror — never recurse
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.getId() != id) {
			return;
		}
		ClientLevel level = (ClientLevel) (Object) this;
		boolean clearing = progress < 0 || progress >= 10;
		List<BlockPos> planned = List.of();
		if (!clearing) {
			ItemStack drill = player.getMainHandItem();
			if (drill.getItem() instanceof ElectricDrillItem) {
				BlockState aimState = level.getBlockState(pos);
				planned = ElectricDrillItem.plannedExtrasShown(drill, level, pos, aimState, player);
			}
		}
		for (int slot = 0; slot < DrillBore.MAX_EXTRAS; slot++) {
			int mirrorId = DrillBore.mirrorId(id, slot);
			if (slot < planned.size()) {
				level.destroyBlockProgress(mirrorId, planned.get(slot), progress);
			} else {
				level.destroyBlockProgress(mirrorId, pos, -1);
			}
		}
	}
}
