package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.GeothermalGeneratorBlockEntity;
import dev.alaindustrial.core.FurnaceFuel;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.item.fluid.ItemFluid;
import dev.alaindustrial.item.fluid.VanillaBucketDeposit;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametest bodies for MOD-077 (geothermal lava-input parity + lava-capsule furnace fuel).
 * Same pattern as {@link CapsuleScenarios}: plain {@code Consumer<GameTestHelper>} bodies wrapped by the
 * Fabric {@code GeothermalLavaInputGameTest} suite and registered on the NeoForge {@code gameTestServer}
 * lane ({@code NeoForgeGameTests}), so both loaders exercise the SAME logic — the geothermal block entity,
 * the shared {@link VanillaBucketDeposit} interception helper, and the furnace-fuel rules. Since 26.3 the
 * latter are the {@code minecraft:cooking_fuel} component the capsule carries ({@code CapsuleFuel}) plus the
 * one remaining mixin, {@code FurnaceFuelSlotMixin}, which caps a lava capsule to one per fuel slot.
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
	// MOD-498 — Item#getCraftingRemainder() is deprecated by NeoForge only; vanilla does not mark it.
	// The replacement it names (the getCraftingRemainder(ItemStack) overload) is NeoForge-only API, and
	// this scenario is shared code compiled against vanilla for the Fabric lane too.
	//
	// MOD-226 — 26.3 deleted the per-level FuelValues table: fuel is now the stack's own
	// minecraft:cooking_fuel component and its burn time is a ResolvableInt that may point into the
	// context_int_provider registry (every vanilla fuel's does), so resolving it needs a loot context.
	// core/FurnaceFuel builds that context, and it is what BOTH of the mod's fuel-burning machines call,
	// so asking it here still asks exactly what the game will ask. That is also why the geothermal
	// generator is placed: the context wants the container the fuel sits in, and this scenario's rig
	// already has one.
	@SuppressWarnings("deprecation")
	public static void fun04LavaCapsuleIsFurnaceFuel(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity machine = placeGeo(helper);
		if (machine == null) {
			return;
		}
		ServerLevel level = helper.getLevel();
		int lavaBucketTime = FurnaceFuel.burnDuration(level, machine, new ItemStack(Items.LAVA_BUCKET));
		if (lavaBucketTime <= 0) {
			// Floor: if the lava bucket itself resolved to nothing, every comparison below would pass
			// by both sides being zero — the vacuous-green failure mode this repository has been bitten
			// by before.
			helper.fail("a vanilla lava bucket resolved to " + lavaBucketTime
					+ " ticks of burn time — the fuel lookup itself is broken, so this test proves nothing");
			return;
		}
		ItemStack lava = capsule(Fluids.LAVA);
		ItemStack water = capsule(Fluids.WATER);
		if (!FurnaceFuel.isFuel(lava) || FurnaceFuel.burnDuration(level, machine, lava) != lavaBucketTime) {
			helper.fail("lava capsule must burn like a lava bucket (" + lavaBucketTime + "), got "
					+ FurnaceFuel.isFuel(lava) + "/" + FurnaceFuel.burnDuration(level, machine, lava));
			return;
		}
		if (FurnaceFuel.isFuel(water) || FurnaceFuel.burnDuration(level, machine, water) != 0) {
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

	// ── FUN14 (MOD-226): a capsule saved by 26.2 heals its fuel component the moment it loads ────────

	/**
	 * A lava capsule the way 26.2 saved it — fluid component, no {@code cooking_fuel} — must burn the
	 * moment it materialises (owner decision, 2026-09-22). The stack is built exactly the way the disk
	 * codec builds one: the public {@code ItemStack(Holder, int, DataComponentPatch)} constructor with a
	 * patch that carries the fluid and nothing else, which is the byte-level shape of a 26.2 world file.
	 * That constructor is where {@code ItemStackLavaCapsuleHealMixin} lives, so constructing the stack IS
	 * loading it — asserting right after construction asks what any furnace would ask the same tick.
	 *
	 * <p>The water twin must stay inert: the heal answers a component 26.2 capsules cannot carry, it must
	 * not invent fuel for capsules that never had it.
	 */
	public static void fun14CapsuleSavedBy262HealsFuelOnLoad(GameTestHelper helper) {
		GeothermalGeneratorBlockEntity machine = placeGeo(helper);
		if (machine == null) {
			return;
		}
		ServerLevel level = helper.getLevel();
		int lavaBucketTime = FurnaceFuel.burnDuration(level, machine, new ItemStack(Items.LAVA_BUCKET));
		if (lavaBucketTime <= 0) {
			// Same floor as FUN04: with the fuel lookup itself broken, both sides of every comparison
			// below would be zero and the test would pass vacuously.
			helper.fail("a vanilla lava bucket resolved to " + lavaBucketTime
					+ " ticks of burn time — the fuel lookup itself is broken, so this test proves nothing");
			return;
		}
		ItemStack lava262 = new ItemStack(
				BuiltInRegistries.ITEM.wrapAsHolder(ModContent.FILLED_VACUUM_CAPSULE.get()),
				1,
				DataComponentPatch.builder()
						.set(ModDataComponents.CAPSULE_FLUID.get(),
								BuiltInRegistries.FLUID.wrapAsHolder(Fluids.LAVA))
						.build());
		if (!FurnaceFuel.isFuel(lava262) || FurnaceFuel.burnDuration(level, machine, lava262) != lavaBucketTime) {
			helper.fail("a lava capsule saved by 26.2 must burn like a lava bucket (" + lavaBucketTime
					+ ") the moment it loads, got " + FurnaceFuel.isFuel(lava262) + "/"
					+ FurnaceFuel.burnDuration(level, machine, lava262));
			return;
		}
		ItemStack water262 = new ItemStack(
				BuiltInRegistries.ITEM.wrapAsHolder(ModContent.FILLED_VACUUM_CAPSULE.get()),
				1,
				DataComponentPatch.builder()
						.set(ModDataComponents.CAPSULE_FLUID.get(),
								BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER))
						.build());
		if (FurnaceFuel.isFuel(water262) || FurnaceFuel.burnDuration(level, machine, water262) != 0) {
			helper.fail("a water capsule saved by 26.2 must NOT become fuel by healing ("
					+ FurnaceFuel.isFuel(water262) + "/"
					+ FurnaceFuel.burnDuration(level, machine, water262) + ")");
			return;
		}
		helper.succeed();
	}
}
