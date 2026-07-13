package dev.alaindustrial.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Empty Vacuum Capsule (MOD-063) — a stackable fluid container. Right-clicking a source fluid in the
 * world (or a mod tank face) picks up exactly one bucket and swaps this capsule for a
 * {@link FilledCapsuleItem} carrying that fluid. Modelled on the empty-bucket branch of the vanilla
 * {@code BucketItem} (raytrace {@code SOURCE_ONLY}, {@link BucketPickup#pickupBlock}, exchange via
 * {@link ItemUtils#createFilledResult}); the mod-tank path lives in {@link CapsuleInteractions}.
 *
 * <p>Stacks to the vanilla default (64) — an empty capsule carries no per-stack fluid component.
 */
public class VacuumCapsuleItem extends Item {

	public VacuumCapsuleItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return CapsuleInteractions.fillFromTank(context);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), stack)) {
			return InteractionResult.PASS;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof BucketPickup pickup)) {
			return InteractionResult.PASS;
		}
		// Only pick up a genuine (placeable) fluid, and check this BEFORE pickupBlock so a rejected pickup
		// never mutates the world. Guards against BucketPickup blocks whose fluid state is empty — e.g.
		// powder snow: pickupBlock would remove the block and return a powder_snow_bucket, and re-deriving
		// from the (empty) fluid state would brick a "filled" capsule holding Fluids.EMPTY. Requiring a
		// FlowingFluid also matches what FilledCapsuleItem#emptyContents can place back.
		Fluid fluid = level.getFluidState(pos).getType();
		if (!(fluid instanceof FlowingFluid)) {
			return InteractionResult.PASS;
		}
		// pickupBlock removes the source / drains a waterlogged block and returns a vanilla bucket item —
		// we discard that bucket and mint a filled capsule of the fluid we read above.
		ItemStack taken = pickup.pickupBlock(player, level, pos, state);
		if (taken.isEmpty()) {
			return InteractionResult.PASS;
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		pickup.getPickupSound().ifPresent(sound -> player.playSound(sound, 1.0F, 1.0F));
		level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
		ItemStack filled = new ItemStack(dev.alaindustrial.registry.ModContent.FILLED_VACUUM_CAPSULE.get());
		ItemFluid.set(filled, fluid);
		ItemStack result = ItemUtils.createFilledResult(stack, player, filled);
		if (player instanceof ServerPlayer serverPlayer) {
			CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, new ItemStack(filled.getItem()));
		}
		return InteractionResult.SUCCESS.heldItemTransformedTo(result);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		adder.accept(Component.translatable("item.alaindustrial.vacuum_capsule.hint")
				.withStyle(ChatFormatting.GRAY));
	}
}
