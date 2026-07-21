package dev.alaindustrial.item;

import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidLookup;
import dev.alaindustrial.core.fluid.FluidPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Deposits a <em>vanilla</em> filled bucket into a neighbouring mod {@link FluidPort} on a shift-right-click
 * (MOD-077). The mod's own Vacuum Capsule already does this from its item {@code useOn}
 * ({@link CapsuleInteractions}); a vanilla bucket cannot, because sneaking with a non-empty hand makes
 * vanilla bypass the block's interaction hooks and run {@code BucketItem#useOn} directly — spilling lava in
 * front of the machine instead of loading it. We therefore intercept the interaction <em>before</em> vanilla
 * processes it, through each loader's early block-use event (Fabric {@code UseBlockCallback}, NeoForge
 * {@code PlayerInteractEvent.RightClickBlock}); both hand this one neutral method the same arguments.
 *
 * <p><b>UX parity with the capsule.</b> Only fires on {@link Player#isSecondaryUseActive() shift} (a plain
 * right-click still opens the machine GUI, exactly as with a capsule) and only for a bucket whose fluid the
 * target tank accepts. Exchange is all-or-nothing — a whole bucket ({@link FluidAmounts#BUCKET}) or nothing —
 * mirroring {@link CapsuleInteractions#emptyIntoTank}.
 *
 * <p><b>Full-tank / rejected fluid.</b> On the client the interaction is consumed the instant a compatible
 * insertable port is found (before reading its possibly-unsynced contents), so vanilla never falls through to
 * {@code BucketItem#use} and spills. If the server then finds the tank full (or the fluid rejected), it is a
 * silent no-op: the interaction stays consumed, nothing spills, the bucket is kept full.
 */
public final class VanillaBucketDeposit {
	private VanillaBucketDeposit() {
	}

	private static final long BUCKET = FluidAmounts.BUCKET;

	/**
	 * Try to empty the held vanilla bucket into a mod tank at the clicked face. Returns
	 * {@link InteractionResult#SUCCESS} when the interaction is ours to consume (deposited, or a silent
	 * no-op on a full/incompatible tank), or {@link InteractionResult#PASS} to let vanilla handle it
	 * normally (not sneaking, not a supported bucket, or no mod tank on that face).
	 */
	public static InteractionResult tryDeposit(Level level, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (player == null || hit == null) {
			return InteractionResult.PASS;
		}
		// Shift only — a plain right-click must still open the GUI (parity with the capsule).
		if (!player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		Fluid fluid = bucketFluid(stack);
		if (fluid == Fluids.EMPTY) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		Direction side = hit.getDirection();
		FluidPort port = FluidLookup.get().find(level, pos, side);
		if (port == null || !port.supportsInsertion()) {
			return InteractionResult.PASS;
		}
		// Consume on the client as soon as a compatible insertable port is present, WITHOUT reading its
		// (possibly unsynced) contents — a consuming result stops the vanilla fall-through to BucketItem#use
		// that would otherwise spill the fluid. The server does the authoritative content check below.
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		// All-or-nothing: only proceed if the tank has room for a whole bucket. A full or wrong-fluid tank is
		// a silent no-op (still consumed, so nothing spills) — same shape as CapsuleInteractions#emptyIntoTank.
		if (port.getCapacity() - port.getAmount() < BUCKET) {
			return InteractionResult.SUCCESS;
		}
		FluidHolder holder = FluidHolder.of(fluid);
		boolean[] ok = {false};
		EnergyTransactions.get().runCommitting(txn -> ok[0] = port.insert(holder, BUCKET, txn) >= BUCKET);
		if (!ok[0]) {
			return InteractionResult.SUCCESS;
		}
		player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
		CapsuleInteractions.playEmpty(level, player, pos, fluid);
		return InteractionResult.SUCCESS;
	}

	/**
	 * The fluid a supported vanilla filled bucket carries, or {@link Fluids#EMPTY} if {@code stack} is not a
	 * bucket we deposit. Scoped to lava for now (the geothermal generator's fuel — MOD-077); the target tank
	 * still decides acceptance, so extending this map never forces an unwanted deposit.
	 */
	private static Fluid bucketFluid(ItemStack stack) {
		return stack.is(Items.LAVA_BUCKET) ? Fluids.LAVA : Fluids.EMPTY;
	}
}
