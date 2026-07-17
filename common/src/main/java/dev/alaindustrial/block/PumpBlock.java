package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Electric pump — faces the player, lights up while moving fluid.
 *
 * <p>Inherits the {@link HorizontalMachineBlock} "front (FACING) face is inert" cable rule: a cable
 * draws an arm toward the five working faces but not toward FACING. Energy intake follows the same
 * shape — see {@link PumpBlockEntity#energyRoleForFace} ({@code IN} on every face <b>but</b>
 * FACING). Fluid intake is a separate subsystem and still reads FACING directly ({@code acquireFluid}
 * starts its BFS from the block in front of the pump), so this does <b>not</b> change which way the
 * pump draws fluid from — only the energy/cable contract on that one face.
 *
 * <p><b>MOD-099: capsule right-click routing.</b> When a player holds a Vacuum Capsule and right-clicks
 * the pump block, the capsule must exchange one bucket with the pump's tank (via
 * {@code CapsuleInteractions}), not just open the GUI. Vanilla's {@code ServerPlayerGameMode.useItemOn}
 * (26.2, verified against bytecode) runs {@code BlockState.useItemOn} → {@code Block.useItemOn} FIRST;
 * the default {@link AbstractMachineBlock} path returns {@link InteractionResult#TRY_WITH_EMPTY_HAND},
 * which sends vanilla into {@code useWithoutItem} — and that opens the pump's GUI with {@code SUCCESS},
 * an early return that <em>prevents</em> the capsule's {@code Item.useOn} from ever running. So we
 * override {@code useItemOn} here to give the capsule priority: when the held stack is one of our
 * capsule items, run its {@code useOn} now and return the result; only if the capsule does not consume
 * the action do we hand off to {@code useWithoutItem} (the GUI). Any other held item falls through to
 * the default (GUI on empty hand).
 */
public class PumpBlock extends LitMachineBlock {
	public static final MapCodec<PumpBlock> CODEC = simpleCodec(PumpBlock::new);

	public PumpBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PumpBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}

	/**
	 * MOD-099: give the Vacuum Capsule first claim on the right-click so it can pull/push a bucket
	 * from/to the pump's tank instead of the GUI silently eating the click. Mirrors the exact call
	 * vanilla would have made ({@code ItemStack.useOn}) but runs it inside the block's
	 * {@code useItemOn} slot, which vanilla reaches <em>before</em> {@code useWithoutItem}. If the
	 * capsule does not consume the action (no compatible fluid / tank full or empty), open the GUI here
	 * via {@code useWithoutItem} and return its result.
	 *
	 * <p><b>Why not return {@code TRY_WITH_EMPTY_HAND} on a no-op capsule?</b> Vanilla's
	 * {@code ServerPlayerGameMode.useItemOn} only routes {@code TryEmptyHandInteraction} into
	 * {@code useWithoutItem} for the MAIN hand and when that call consumes the action; for the OFF hand
	 * (or a non-consuming {@code useWithoutItem}) it falls through to {@code Item.useOn} — which would
	 * run the capsule's exchange a <em>second</em> time (we already ran it above), double-applying the
	 * bucket swap. So we open the GUI ourselves and return a consuming result, which short-circuits
	 * vanilla before it ever reaches {@code Item.useOn}. This is order-faithful with the vanilla path
	 * (capsule first, then GUI) while removing the double-call window.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (stack.is(ModContent.VACUUM_CAPSULE.get()) || stack.is(ModContent.FILLED_VACUUM_CAPSULE.get())) {
			UseOnContext ctx = new UseOnContext(player, hand, hit);
			InteractionResult result = stack.useOn(ctx);
			if (result.consumesAction()) {
				return result;
			}
		}
		// No capsule exchange happened — open the GUI exactly as a plain right-click would, and return
		// that (consuming) result. Returning a consuming result here is what prevents vanilla from
		// proceeding to Item.useOn (no second capsule call) and from re-entering useWithoutItem.
		return state.useWithoutItem(level, player, hit);
	}
}
