package dev.alaindustrial.item;

import dev.alaindustrial.core.EnergyTransactions;
import dev.alaindustrial.core.FluidAmounts;
import dev.alaindustrial.core.FluidHolder;
import dev.alaindustrial.core.FluidLookup;
import dev.alaindustrial.core.FluidPort;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Shared logic for the two Vacuum Capsule items (MOD-063): exchanging one capsule item for the other,
 * transferring a bucket to/from a neighbouring mod {@link FluidPort} (pump, geothermal generator, …)
 * via {@link FluidLookup}, and the fill/empty sounds and dynamic fluid name.
 *
 * <p>Capsules are all-or-nothing: exactly one bucket ({@link FluidAmounts#BUCKET}) moves per action,
 * mirroring the pump's own bucket handling ({@code PumpBlockEntity.emptyBucketIntoTank}). Tank
 * transfers commit only server-side; the client returns {@link InteractionResult#SUCCESS} when a
 * suitable port is present so the swing predicts, and the authoritative inventory syncs from the server.
 */
final class CapsuleInteractions {
	private CapsuleInteractions() {
	}

	static final long BUCKET = FluidAmounts.BUCKET;

	/** Empty capsule right-clicked on a block face: pull one bucket from a mod tank there, if any. */
	static InteractionResult fillFromTank(UseOnContext ctx) {
		Level level = ctx.getLevel();
		Player player = ctx.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		BlockPos pos = ctx.getClickedPos();
		Direction side = ctx.getClickedFace();
		FluidPort port = FluidLookup.get().find(level, pos, side);
		if (port == null || !port.supportsExtraction()) {
			return InteractionResult.PASS;
		}
		// Consume the interaction on the client as soon as we know this is an extractable tank, WITHOUT
		// reading its (possibly unsynced) contents — a consuming result stops the client from also sending
		// a use-item packet, which would otherwise let the server run FilledCapsuleItem#use on the
		// just-filled capsule and spill fluid into the world. The server does the authoritative content check.
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		FluidHolder held = port.fluid();
		// Only proceed if the tank has at least a full bucket to give: FluidPort#extract can be partial and
		// runCommitting commits whatever the body moved, so this pre-check keeps the exchange all-or-nothing
		// (a partial extract would be lost with no filled capsule to show for it). Same pattern as
		// PumpBlockEntity#fillBucketFromTank; the boolean guard below still rejects any residual short move.
		if (held.isEmpty() || port.getAmount() < BUCKET) {
			return InteractionResult.PASS;
		}
		boolean[] ok = {false};
		EnergyTransactions.get().runCommitting(txn -> ok[0] = port.extract(held, BUCKET, txn) >= BUCKET);
		if (!ok[0]) {
			return InteractionResult.PASS;
		}
		ItemStack filled = new ItemStack(ModContent.FILLED_VACUUM_CAPSULE.get());
		ItemFluid.set(filled, held.fluid());
		player.setItemInHand(ctx.getHand(), ItemUtils.createFilledResult(ctx.getItemInHand(), player, filled));
		playFill(level, player, pos, held.fluid());
		return InteractionResult.SUCCESS;
	}

	/** Filled capsule right-clicked on a block face: push its bucket into a mod tank there, if any. */
	static InteractionResult emptyIntoTank(UseOnContext ctx, Fluid fluid) {
		Level level = ctx.getLevel();
		Player player = ctx.getPlayer();
		if (player == null || fluid == Fluids.EMPTY) {
			return InteractionResult.PASS;
		}
		BlockPos pos = ctx.getClickedPos();
		Direction side = ctx.getClickedFace();
		FluidPort port = FluidLookup.get().find(level, pos, side);
		if (port == null || !port.supportsInsertion()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		FluidHolder holder = FluidHolder.of(fluid);
		// Only proceed if the tank has room for a full bucket: FluidPort#insert can be partial and
		// runCommitting commits whatever the body moved, so committing a partial insert while keeping the
		// capsule full would DUPLICATE fluid. The room pre-check keeps it all-or-nothing (a same-fluid tank
		// with room ≥ 1 bucket accepts the whole bucket; a wrong-fluid tank returns 0, never a partial).
		if (port.getCapacity() - port.getAmount() < BUCKET) {
			return InteractionResult.PASS;
		}
		boolean[] ok = {false};
		EnergyTransactions.get().runCommitting(txn -> ok[0] = port.insert(holder, BUCKET, txn) >= BUCKET);
		if (!ok[0]) {
			return InteractionResult.PASS;
		}
		ItemStack empty = new ItemStack(ModContent.VACUUM_CAPSULE.get());
		player.setItemInHand(ctx.getHand(), ItemUtils.createFilledResult(ctx.getItemInHand(), player, empty));
		playEmpty(level, player, pos, fluid);
		return InteractionResult.SUCCESS;
	}

	static void playFill(Level level, Player player, BlockPos pos, Fluid fluid) {
		SoundEvent sound = fluid.getPickupSound()
				.orElse(fluid.is(FluidTags.LAVA) ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL);
		level.playSound(player, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
	}

	static void playEmpty(Level level, Player player, BlockPos pos, Fluid fluid) {
		SoundEvent sound = fluid.is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
		level.playSound(player, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
	}

	/**
	 * A player-facing name for {@code fluid}: its placed block's name (e.g. "Water", "Lava"). Falls back
	 * to a generic label for fluids with no placeable block (exotic modded fluids only reachable via tanks).
	 */
	static Component fluidDisplayName(Fluid fluid) {
		BlockState legacy = fluid.defaultFluidState().createLegacyBlock();
		if (!legacy.isAir()) {
			return legacy.getBlock().getName();
		}
		return Component.translatable("item.alaindustrial.filled_vacuum_capsule.fluid_unknown");
	}
}
