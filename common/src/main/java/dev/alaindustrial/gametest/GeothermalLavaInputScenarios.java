package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.item.ItemFluid;
import dev.alaindustrial.item.VanillaBucketDeposit;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametest bodies for MOD-077 (geothermal lava-input parity + lava-capsule furnace fuel).
 * Same pattern as {@link CapsuleScenarios}: plain {@code Consumer<GameTestHelper>} bodies wrapped by the
 * Fabric {@code GeothermalLavaInputGameTest} suite and registered on the NeoForge {@code gameTestServer}
 * lane ({@code NeoForgeGameTests}), so both loaders exercise the SAME logic — the geothermal block entity,
 * the shared {@link VanillaBucketDeposit} interception helper, and the furnace-fuel mixins.
 */
public final class GeothermalLavaInputScenarios {

	private GeothermalLavaInputScenarios() {
	}

	private static final BlockPos GEO = new BlockPos(1, 2, 1);

	private static ItemStack capsule(Fluid fluid) {
		ItemStack stack = new ItemStack(ModContent.FILLED_VACUUM_CAPSULE.get());
		ItemFluid.set(stack, fluid);
		return stack;
	}

	private static GeothermalGeneratorBlockEntity placeGeo(GameTestHelper helper) {
		helper.setBlock(GEO, ModContent.GEOTHERMAL_GENERATOR.get().defaultBlockState());
		GeothermalGeneratorBlockEntity be = helper.getBlockEntity(GEO, GeothermalGeneratorBlockEntity.class);
		if (be == null) {
			helper.fail("geothermal generator block entity missing after placement");
		}
		return be;
	}

	private static void drive(GeothermalGeneratorBlockEntity geo, GameTestHelper helper, int ticks) {
		BlockPos abs = geo.getBlockPos();
		for (int i = 0; i < ticks; i++) {
			geo.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs));
		}
	}

	private static Player sneakingPlayer(GameTestHelper helper, ItemStack held) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true); // isSecondaryUseActive() → true
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		return player;
	}

	private static BlockHitResult hitTop(GameTestHelper helper) {
		BlockPos abs = helper.absolutePos(GEO);
		return new BlockHitResult(Vec3.atCenterOf(abs), net.minecraft.core.Direction.UP, abs, false);
	}

	// ── FUN01: a lava capsule in the GUI input slot drains and returns an empty capsule ───────────────

	/** Lava capsule in the input slot is consumed for burn, empty capsule out. Traced by FUN05. */
	public static void fun05CapsuleInSlotDrains(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = placeGeo(helper);
		if (geo == null) {
			return;
		}
		geo.setItem(GeothermalGeneratorBlockEntity.INPUT_SLOT, capsule(Fluids.LAVA));
		drive(geo, helper, 1);
		ItemStack in = geo.getItem(GeothermalGeneratorBlockEntity.INPUT_SLOT);
		ItemStack out = geo.getItem(GeothermalGeneratorBlockEntity.OUTPUT_SLOT);
		if (!in.isEmpty() || !out.is(ModContent.VACUUM_CAPSULE.get()) || out.getCount() != 1) {
			helper.fail("capsule not drained to an empty capsule: in=" + in + " out=" + out);
			return;
		}
		helper.succeed();
	}

	// ── FUN06: shift-right-click with a vanilla lava bucket loads the tank + returns an empty bucket ──

	/** Shift+use a lava bucket on the block fills the tank, returns an empty bucket. Traced by FUN06. */
	public static void fun06BucketDepositViaShift(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = placeGeo(helper);
		if (geo == null) {
			return;
		}
		Player player = sneakingPlayer(helper, new ItemStack(Items.LAVA_BUCKET));
		InteractionResult result = VanillaBucketDeposit.tryDeposit(
				helper.getLevel(), player, InteractionHand.MAIN_HAND, hitTop(helper));
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean tankGotBucket = geo.fluidTank.amount == FluidAmounts.BUCKET && geo.fluidTank.fluid.is(Fluids.LAVA);
		if (!result.consumesAction() || !hand.is(Items.BUCKET) || !tankGotBucket) {
			helper.fail("bucket deposit failed: result=" + result + " hand=" + hand
					+ " tank=" + geo.fluidTank.amount + " fluid=" + geo.fluidTank.fluid);
			return;
		}
		helper.succeed();
	}

	// ── NEG01: a FULL tank is a silent no-op — nothing spills, the bucket is kept ─────────────────────

	/** Shift+use a lava bucket on a full tank consumes the click but keeps the bucket. Traced by NEG06. */
	public static void neg06BucketFullTankNoOp(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity geo = placeGeo(helper);
		if (geo == null) {
			return;
		}
		// Fill the tank to capacity so there is no room for another bucket.
		geo.fluidTank.fluid = dev.alaindustrial.core.fluid.FluidHolder.of(Fluids.LAVA);
		geo.fluidTank.amount = geo.fluidTank.capacity;
		Player player = sneakingPlayer(helper, new ItemStack(Items.LAVA_BUCKET));
		InteractionResult result = VanillaBucketDeposit.tryDeposit(
				helper.getLevel(), player, InteractionHand.MAIN_HAND, hitTop(helper));
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		// Consumed (so vanilla never spills), but the bucket stays full and the tank is unchanged.
		if (!result.consumesAction() || !hand.is(Items.LAVA_BUCKET) || geo.fluidTank.amount != geo.fluidTank.capacity) {
			helper.fail("full-tank deposit must be a silent no-op: result=" + result + " hand=" + hand
					+ " tank=" + geo.fluidTank.amount);
			return;
		}
		helper.succeed();
	}

	// ── FUN07: only a LAVA capsule is furnace fuel, at the lava-bucket burn time, remainder = empty capsule ──

	/** Lava capsule is furnace fuel (lava-bucket burn time); water capsule is not. Traced by CAPS FUN04. */
	public static void fun04LavaCapsuleIsFurnaceFuel(GameTestHelper helper) {
		FuelValues fuel = helper.getLevel().fuelValues();
		int lavaBucketTime = fuel.burnDuration(new ItemStack(Items.LAVA_BUCKET));
		ItemStack lava = capsule(Fluids.LAVA);
		ItemStack water = capsule(Fluids.WATER);
		if (!fuel.isFuel(lava) || fuel.burnDuration(lava) != lavaBucketTime) {
			helper.fail("lava capsule must burn like a lava bucket (" + lavaBucketTime + "), got "
					+ fuel.isFuel(lava) + "/" + fuel.burnDuration(lava));
			return;
		}
		if (fuel.isFuel(water) || fuel.burnDuration(water) != 0) {
			helper.fail("a water capsule must NOT be furnace fuel");
			return;
		}
		ItemStackTemplate remainder = ModContent.FILLED_VACUUM_CAPSULE.get().getCraftingRemainder();
		if (remainder == null || !remainder.create().is(ModContent.VACUUM_CAPSULE.get())) {
			helper.fail("furnace fuel remainder must be an empty capsule, was " + remainder);
			return;
		}
		helper.succeed();
	}

	// ── FUN08: a lava capsule caps to one item in a furnace fuel slot (no tare loss on a stack) ────────

	/** A lava capsule stacks to one in a furnace fuel slot (like a bucket). Traced by CAPS FUN05. */
	public static void fun05FurnaceFuelSlotCapsOne(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		FurnaceMenu menu = new FurnaceMenu(0, player.getInventory());
		Slot fuelSlot = menu.slots.get(1); // AbstractFurnaceMenu fuel slot = index 1 (a FurnaceFuelSlot)
		int max = fuelSlot.getMaxStackSize(capsule(Fluids.LAVA));
		if (max != 1) {
			helper.fail("lava capsule must cap to 1 in a furnace fuel slot (no tare loss), was " + max);
			return;
		}
		helper.succeed();
	}
}
