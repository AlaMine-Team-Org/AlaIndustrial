package dev.alaindustrial.mixin;

import dev.alaindustrial.item.tool.DrillBore;
import dev.alaindustrial.item.tool.ElectricDrillItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows the cracking animation on every block a bore module takes (MOD-482 column, MOD-821 row) to the OTHER
 * players watching, so they see what the drill is about to take.
 *
 * <h2>Why the hook is here</h2>
 * {@code ServerLevel#destroyBlockProgress} is the single funnel every crack update passes through —
 * the first frame, every increment, and the {@code -1} that clears it when mining stops or is
 * aborted. Hooking it therefore covers appearing AND disappearing in one place.
 *
 * <h2>Facts about vanilla this depends on</h2>
 * <ul>
 * <li><b>One crack per breaker id.</b> {@code ClientLevel#destroyBlockProgress} keys its entries by
 * the breaker id and REPLACES the entry when the same id names a different position, so each extra block
 * gets an id of its own ({@link DrillBore#mirrorId}).</li>
 * <li><b>The miner is not sent here.</b> The miner's own crack is drawn client-side, every tick, from the
 * client's own progress counter; a packet from this hook would reach them a tick or a ping late and from a
 * different counter, so their extras would crack out of step with the centre block. The miner's extras are
 * mirrored on their client in the very call that draws the centre ({@code ClientLevelCrackMixin}); this hook
 * serves everyone else, with a packet each (the vanilla loop: same level, within 32 blocks).</li>
 * </ul>
 *
 * <p>The mirrored ids are NEGATIVE, derived from the player's. Entity ids are positive, so a negative
 * id can never collide with a real entity's, and it doubles as a recursion guard.
 *
 * <p>Registered in the OPTIONAL mixin config, not the required one. This is a cosmetic overlay: if
 * another mod's injection into the same method conflicts, the right outcome is a drill whose extra
 * blocks crack invisibly, not a game that refuses to load.
 */
@Mixin(ServerLevel.class)
public abstract class ColumnBoreCrackMixin {

	/** Vanilla's broadcast radius for a crack: 32 blocks, squared. */
	private static final double BROADCAST_RADIUS_SQR = 32.0 * 32.0;

	@Inject(method = "destroyBlockProgress", at = @At("HEAD"))
	private void alaindustrial$mirrorColumnBoreCracks(int id, BlockPos pos, int progress, CallbackInfo ci) {
		if (id < 0) {
			return; // one of ours — never recurse
		}
		ServerLevel level = (ServerLevel) (Object) this;
		if (!(level.getEntity(id) instanceof ServerPlayer miner)) {
			return;
		}
		// Vanilla treats anything outside 0..9 as "clear this entry", and the position is ignored on
		// that path.
		boolean clearing = progress < 0 || progress >= 10;
		if (clearing) {
			// A CLEAR goes out whatever is in hand now. Gating it on the drill left the extra cracks frozen
			// on other screens when the player switched mode or slot mid-swing: vanilla cleared its own
			// entry, ours never heard about it. All four slots go, whatever is installed now.
			for (int slot = 0; slot < DrillBore.MAX_EXTRAS; slot++) {
				broadcast(level, miner, DrillBore.mirrorId(id, slot), pos, progress);
			}
			return;
		}
		ItemStack drill = miner.getMainHandItem();
		if (!(drill.getItem() instanceof ElectricDrillItem)) {
			return;
		}
		// Only the blocks the stroke will really break, and only when the drill can pay for all of them:
		// cracking a block that then survives is a lie.
		BlockState aimState = level.getBlockState(pos);
		List<BlockPos> planned = ElectricDrillItem.plannedExtras(drill, level, pos, aimState, miner);
		for (int slot = 0; slot < DrillBore.MAX_EXTRAS; slot++) {
			int mirrorId = DrillBore.mirrorId(id, slot);
			if (slot < planned.size()) {
				broadcast(level, miner, mirrorId, planned.get(slot), progress);
			} else {
				broadcast(level, miner, mirrorId, pos, -1);
			}
		}
	}

	/** What {@code ServerLevel#destroyBlockProgress} does for one id, minus the miner. */
	private static void broadcast(ServerLevel level, ServerPlayer miner, int mirrorId, BlockPos pos, int progress) {
		ClientboundBlockDestructionPacket packet = new ClientboundBlockDestructionPacket(mirrorId, pos, progress);
		for (ServerPlayer other : level.getServer().getPlayerList().getPlayers()) {
			if (other == miner || other.level() != level) {
				continue;
			}
			double dx = pos.getX() + 0.5 - other.getX();
			double dy = pos.getY() + 0.5 - other.getY();
			double dz = pos.getZ() + 0.5 - other.getZ();
			if (dx * dx + dy * dy + dz * dz < BROADCAST_RADIUS_SQR) {
				other.connection.send(packet);
			}
		}
	}
}
