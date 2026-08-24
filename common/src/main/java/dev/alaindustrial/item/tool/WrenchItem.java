package dev.alaindustrial.item.tool;

import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.core.item.PipeFaceMode;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/**
 * The wrench: a plain click cycles a pipe face (neutral → extract → insert → disabled), a shift-click
 * unbolts any of the mod's blocks so it drops as an item (MOD-108).
 *
 * <p>Shift is free for dismantling because vanilla routes a secondary-use click straight to
 * {@code Item.useOn}, skipping the block's own action — the same ordering the pump's capsule handling
 * had to work around (MOD-099). It previously ran the face cycle backwards, which no one asked for.
 */
public final class WrenchItem extends Item {
	public WrenchItem(Properties properties) { super(properties); }

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		// Shift-click unbolts the block — any block of ours, not just pipes.
		if (context.isSecondaryUseActive()) {
			return WrenchDismantle.dismantle(level, context.getClickedPos(), player, context.getItemInHand())
					? InteractionResult.SUCCESS
					: InteractionResult.PASS;
		}
		if (level.getBlockEntity(context.getClickedPos()) instanceof FluidPipeBlockEntity fluidPipe) {
			return cycleFluidPipeFace(context, level, player, fluidPipe);
		}
		if (!(level.getBlockEntity(context.getClickedPos()) instanceof ItemPipeBlockEntity pipe)) {
			return InteractionResult.PASS;
		}
		Direction face = configuredFace(context, pipe);
		// A pipe-to-pipe edge is an internal route, not an inventory port. It can only be joined or
		// cut; extract/insert labels and endpoint geometry would be meaningless in the middle of a run.
		if (level.getBlockState(context.getClickedPos().relative(face)).getBlock() instanceof ItemPipeBlock) {
			pipe.setFaceMode(face, pipe.faceMode(face) == PipeFaceMode.DISABLED
					? PipeFaceMode.NEUTRAL : PipeFaceMode.DISABLED);
		} else {
			pipe.cycleFaceMode(face);
		}
		PipeFaceMode mode = pipe.faceMode(face);
		player.sendOverlayMessage(Component.translatable("message.alaindustrial.item_pipe.mode",
				Component.translatable(mode.translationKey()))
				.withStyle(ChatFormatting.AQUA));
		return InteractionResult.SUCCESS;
	}

	/**
	 * The fluid pipe's faces work exactly like the item pipe's — same ladder, same pipe-to-pipe
	 * join/cut rule — so the two behave identically in the player's hands. Only the lookup used to find
	 * a hidden endpoint differs, because a fluid port is not an inventory.
	 */
	private static InteractionResult cycleFluidPipeFace(UseOnContext context, ServerLevel level,
			ServerPlayer player, FluidPipeBlockEntity pipe) {
		Direction face = configuredFluidFace(context);
		if (level.getBlockState(context.getClickedPos().relative(face)).getBlock() instanceof FluidPipeBlock) {
			pipe.setFaceMode(face, pipe.faceMode(face) == PipeFaceMode.DISABLED
					? PipeFaceMode.NEUTRAL : PipeFaceMode.DISABLED);
		} else {
			pipe.cycleFaceMode(face);
		}
		PipeFaceMode mode = pipe.faceMode(face);
		player.sendOverlayMessage(Component.translatable("message.alaindustrial.fluid_pipe.mode",
				Component.translatable(mode.translationKey()))
				.withStyle(ChatFormatting.AQUA));
		return InteractionResult.SUCCESS;
	}

	/** {@link #configuredFace} for fluid pipes: same nearest-candidate resolution, fluid-port lookup. */
	private static Direction configuredFluidFace(UseOnContext context) {
		Direction clicked = context.getClickedFace();
		if (FluidPipeBlock.hasEndpointCandidate(context.getLevel(), context.getClickedPos(), clicked)) {
			return clicked;
		}
		Vec3 hit = context.getClickLocation();
		double localX = hit.x - context.getClickedPos().getX();
		double localY = hit.y - context.getClickedPos().getY();
		double localZ = hit.z - context.getClickedPos().getZ();
		Direction nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Direction candidate : Direction.values()) {
			if (!FluidPipeBlock.hasEndpointCandidate(context.getLevel(), context.getClickedPos(), candidate)) {
				continue;
			}
			double distance = distanceToFace(candidate, localX, localY, localZ);
			if (distance < nearestDistance) {
				nearest = candidate;
				nearestDistance = distance;
			}
		}
		return nearest == null ? clicked : nearest;
	}

	/**
	 * A configured endpoint is usually hidden by the inventory it touches, so its cardinal face
	 * cannot be clicked directly. When the player clicks a free side of the pipe near an arm,
	 * resolve that hit to the nearest neighbouring inventory/pipe endpoint instead. The candidate
	 * check intentionally includes DISABLED faces, making an invisible endpoint reversible.
	 */
	private static Direction configuredFace(UseOnContext context, ItemPipeBlockEntity pipe) {
		Direction clicked = context.getClickedFace();
		if (ItemPipeBlock.hasEndpointCandidate(context.getLevel(), context.getClickedPos(), clicked)) return clicked;
		Vec3 hit = context.getClickLocation();
		int x = context.getClickedPos().getX();
		int y = context.getClickedPos().getY();
		int z = context.getClickedPos().getZ();
		double localX = hit.x - x;
		double localY = hit.y - y;
		double localZ = hit.z - z;
		Direction nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Direction candidate : Direction.values()) {
			if (!ItemPipeBlock.hasEndpointCandidate(context.getLevel(), context.getClickedPos(), candidate)) continue;
			double distance = distanceToFace(candidate, localX, localY, localZ);
			if (distance < nearestDistance) {
				nearest = candidate;
				nearestDistance = distance;
			}
		}
		return nearest == null ? clicked : nearest;
	}

	private static double distanceToFace(Direction direction, double x, double y, double z) {
		return switch (direction) {
			case DOWN -> y;
			case UP -> 1.0D - y;
			case NORTH -> z;
			case SOUTH -> 1.0D - z;
			case WEST -> x;
			case EAST -> 1.0D - x;
		};
	}

	/** One line: shift-click dismantles. The face cycle announces itself in the action bar already. */
	// MOD-498 — soft-deprecated by vanilla, but still the only hook an item has for tooltip lines it
	// computes itself: ItemStack#addDetailsToTooltip calls it, and vanilla overrides it in DiscFragmentItem,
	// HangingEntityItem and SmithingTemplateItem. Data-component TooltipProviders cover data-driven
	// components, not a fixed hint line the item declares for itself.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			java.util.function.Consumer<Component> adder, TooltipFlag flag) {
		adder.accept(Component.translatable("item.alaindustrial.wrench.hint").withStyle(ChatFormatting.GRAY));
	}
}
