package dev.alaindustrial.mixin;

import dev.alaindustrial.item.tool.ElectricDrillItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows the cracking animation on all three blocks of a column bore (MOD-482), so a player can see
 * what the drill is about to take before it takes it.
 *
 * <h2>Why the hook is here</h2>
 * {@code ServerLevel#destroyBlockProgress} is the single funnel every crack update passes through —
 * the first frame, every increment, and the {@code -1} that clears it when mining stops or is
 * aborted. Hooking it therefore covers appearing AND disappearing in one place; the alternatives
 * ({@code incrementDestroyProgress}, the two abort branches of {@code handleBlockBreakAction}) are
 * three injection points that each cover half the story, and two of them read private fields.
 *
 * <h2>Two facts about vanilla this depends on, both read off the 26.2 sources</h2>
 * <ul>
 * <li><b>One crack per breaker id.</b> {@code ClientLevel#destroyBlockProgress} keys its entries by
 * the breaker id and REPLACES the entry when the same id names a different position. Three cracked
 * blocks therefore need three ids, not one — which is why the extra blocks get ids of their own
 * rather than the player's.</li>
 * <li><b>A player never receives their own crack.</b> The broadcast skips {@code player.getId() ==
 * id}, because the miner's own overlay is drawn client-side. The mirrored ids are not the miner's,
 * so the mirrored cracks do reach them — which is the whole point of the feature.</li>
 * </ul>
 *
 * <p>The mirrored ids are NEGATIVE, derived from the player's. Entity ids are positive, so a negative
 * id can never collide with a real entity's, and it doubles as the recursion guard: our own calls
 * re-enter this method and leave immediately.
 *
 * <p>Registered in the OPTIONAL mixin config, not the required one. This is a cosmetic overlay: if
 * another mod's injection into the same method conflicts, the right outcome is a drill whose extra
 * blocks crack invisibly, not a game that refuses to load.
 */
@Mixin(ServerLevel.class)
public abstract class ColumnBoreCrackMixin {

	@Inject(method = "destroyBlockProgress", at = @At("HEAD"))
	private void alaindustrial$mirrorColumnBoreCracks(int id, BlockPos pos, int progress, CallbackInfo ci) {
		if (id < 0) {
			return; // one of ours — never recurse
		}
		ServerLevel level = (ServerLevel) (Object) this;
		if (!(level.getEntity(id) instanceof ServerPlayer player)) {
			return;
		}
		// Vanilla treats anything outside 0..9 as "clear this entry", and the position is ignored on
		// that path.
		boolean clearing = progress < 0 || progress >= 10;
		ItemStack drill = player.getMainHandItem();
		boolean active = drill.getItem() instanceof ElectricDrillItem
				&& ElectricDrillItem.isColumnActive(drill);
		// A CLEAR is mirrored whatever is in hand now. Gating it on the drill left the two extra
		// cracks frozen on screen when the player switched the mode off or changed slot mid-swing:
		// vanilla cleared its own entry, ours never heard about it. Only the SET needs the drill —
		// and needs the block to be one the column would really take, because cracking a block that
		// then survives is a lie.
		if (!clearing && !active) {
			return;
		}
		mirror(level, pos.above(), -(id * 2 + 1), progress, clearing);
		mirror(level, pos.below(), -(id * 2 + 2), progress, clearing);
	}

	private static void mirror(ServerLevel level, BlockPos pos, int mirrorId, int progress, boolean clearing) {
		if (!clearing) {
			if (level.isOutsideBuildHeight(pos)) {
				return;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.is(BlockTags.MINEABLE_WITH_PICKAXE)
					|| state.getDestroySpeed(level, pos) < 0.0f) {
				return;
			}
		}
		level.destroyBlockProgress(mirrorId, pos, progress);
	}
}
