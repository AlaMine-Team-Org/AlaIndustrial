package dev.alaindustrial.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Filled Vacuum Capsule (MOD-063) — carries exactly one bucket of a single fluid in the
 * {@code alaindustrial:capsule_fluid} component ({@link ItemFluid}). Right-clicking places that fluid
 * into the world (or into a mod tank face) and swaps back to an empty {@link VacuumCapsuleItem}. World
 * placement ports the vanilla {@code BucketItem} logic verbatim ({@link #emptyContents}): only
 * {@link FlowingFluid}s can be placed, water evaporates in ultra-warm dimensions, waterloggable blocks
 * are filled for water. The mod-tank path lives in {@link CapsuleInteractions}.
 *
 * <p>Stacks to {@link #STACK_SIZE}; capsules of the same fluid share one component value and merge,
 * different fluids never do.
 */
public class FilledCapsuleItem extends Item {

	/** Max stack size: sixteen buckets carried in one slot. Fixed at the type level (26.2 has no per-stack size). */
	public static final int STACK_SIZE = 16;

	public FilledCapsuleItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return CapsuleInteractions.emptyIntoTank(context, ItemFluid.get(context.getItemInHand()));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Fluid fluid = ItemFluid.get(stack);
		if (fluid == Fluids.EMPTY) {
			return InteractionResult.PASS;
		}
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockPos offset = pos.relative(hit.getDirection());
		if (!level.mayInteract(player, pos) || !player.mayUseItemAt(offset, hit.getDirection(), stack)) {
			return InteractionResult.PASS;
		}
		BlockState clicked = level.getBlockState(pos);
		BlockPos placePos = clicked.getBlock() instanceof LiquidBlockContainer && fluid == Fluids.WATER ? pos : offset;
		if (!emptyContents(fluid, player, level, placePos, hit)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, placePos, stack);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		ItemStack empty = new ItemStack(dev.alaindustrial.registry.ModContent.VACUUM_CAPSULE.get());
		ItemStack result = ItemUtils.createFilledResult(stack, player, empty);
		return InteractionResult.SUCCESS.heldItemTransformedTo(result);
	}

	/**
	 * Place {@code fluid} into the world at {@code pos} — a faithful port of {@code BucketItem.emptyContents}
	 * (26.2). Only {@link FlowingFluid}s can be placed (exotic non-placeable fluids fail here and can only be
	 * emptied into mod tanks); water evaporates via {@link EnvironmentAttributes#WATER_EVAPORATES} in ultra-warm
	 * dimensions; waterloggable blocks are filled for water; shift falls through to the adjacent position.
	 */
	private boolean emptyContents(Fluid fluid, Player player, Level level, BlockPos pos, BlockHitResult hit) {
		if (!(fluid instanceof FlowingFluid flowingFluid)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		boolean mayReplace = state.canBeReplaced(fluid);
		boolean shiftKeyDown = player != null && player.isShiftKeyDown();
		boolean placeLiquid = mayReplace
				|| block instanceof LiquidBlockContainer container && container.canPlaceLiquid(player, level, pos, state, fluid);
		boolean canPlaceFluidInsideBlock = state.isAir() || placeLiquid && (!shiftKeyDown || hit == null);
		if (!canPlaceFluidInsideBlock) {
			return hit != null && emptyContents(fluid, player, level, hit.getBlockPos().relative(hit.getDirection()), null);
		}
		if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos) && fluid.is(FluidTags.WATER)) {
			int x = pos.getX();
			int y = pos.getY();
			int z = pos.getZ();
			RandomSource random = level.getRandom();
			level.playSound(player, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
					0.5F, 2.6F + (random.nextFloat() - random.nextFloat()) * 0.8F);
			for (int i = 0; i < 8; i++) {
				level.addParticle(ParticleTypes.LARGE_SMOKE, x + random.nextFloat(), y + random.nextFloat(),
						z + random.nextFloat(), 0.0, 0.0, 0.0);
			}
			return true;
		}
		if (block instanceof LiquidBlockContainer container && fluid == Fluids.WATER) {
			container.placeLiquid(level, pos, state, flowingFluid.getSource(false));
			CapsuleInteractions.playEmpty(level, player, pos, fluid);
			return true;
		}
		if (!level.isClientSide() && mayReplace && !state.liquid()) {
			level.destroyBlock(pos, true);
		}
		if (!level.setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), 11) && !state.getFluidState().isSource()) {
			return false;
		}
		CapsuleInteractions.playEmpty(level, player, pos, fluid);
		return true;
	}

	@Override
	public Component getName(ItemStack stack) {
		Fluid fluid = ItemFluid.get(stack);
		if (fluid == Fluids.EMPTY) {
			return super.getName(stack);
		}
		return Component.translatable("item.alaindustrial.filled_vacuum_capsule.named",
				CapsuleInteractions.fluidDisplayName(fluid));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> adder, TooltipFlag flag) {
		adder.accept(Component.translatable("item.alaindustrial.filled_vacuum_capsule.amount")
				.withStyle(ChatFormatting.GRAY));
	}
}
