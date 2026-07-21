package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.FluidTankBlockEntity;
import dev.alaindustrial.core.fluid.FluidAmounts;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.item.FluidTankContents;
import dev.alaindustrial.item.ItemFluid;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * MOD-111 L2 coverage for the portable fluid tank: persistence, portable item state, stack safety,
 * and the real Fabric fluid capability exposed to other mods.
 */
public final class FluidTankGameTest {
	private static final BlockPos POS = new BlockPos(1, 2, 1);

	private static FluidTankBlockEntity place(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.FLUID_TANK);
		return helper.getBlockEntity(POS, FluidTankBlockEntity.class);
	}

	/**
	 * A click aimed at a glass wall must stop at the tank. The baked model is only the frame — the
	 * glass is drawn by the renderer — so a shape built from the frame left the wall open to a trace:
	 * players aiming at the tank hit the block behind it and emptied buckets of lava onto the floor.
	 */
	@GameTest
	public void tcFluidTank001Fun02_glassWallStopsAClick(GameTestHelper helper) {
		place(helper);
		BlockPos behind = POS.relative(Direction.NORTH);
		helper.setBlock(behind, net.minecraft.world.level.block.Blocks.STONE);

		// Stand south of the tank at wall height and aim straight through it at the block behind:
		// the trace has to stop at the tank's south glass wall.
		Vec3 target = helper.absoluteVec(Vec3.atCenterOf(behind));
		Vec3 eye = helper.absoluteVec(Vec3.atCenterOf(POS).add(0.0, 0.0, 2.0));
		var clip = helper.getLevel().clip(new net.minecraft.world.level.ClipContext(eye, target,
				net.minecraft.world.level.ClipContext.Block.OUTLINE,
				net.minecraft.world.level.ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));

		if (clip.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK
				|| !clip.getBlockPos().equals(helper.absolutePos(POS))) {
			helper.fail("a trace at the tank's glass wall must hit the tank, not pass through it");
		}
		helper.succeed();
	}

	@GameTest
	public void tcFluidTank001Dat01_contentsCodecRoundTrips(GameTestHelper helper) {
		for (var fluid : new net.minecraft.world.level.material.Fluid[] {Fluids.WATER, Fluids.LAVA}) {
			FluidTankContents original = new FluidTankContents(
					fluid.builtInRegistryHolder(), FluidAmounts.BUCKET * 3);
			Tag encoded = FluidTankContents.CODEC.encodeStart(NbtOps.INSTANCE, original)
					.getOrThrow(message -> new IllegalStateException("encode failed: " + message));
			FluidTankContents decoded = FluidTankContents.CODEC.parse(NbtOps.INSTANCE, encoded)
					.getOrThrow(message -> new IllegalStateException("decode failed: " + message));
			if (!decoded.equals(original)) {
				helper.fail("FluidTankContents codec changed " + original + " into " + decoded);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void tcFluidTank001Per01_nbtAndComponentRoundTrip(GameTestHelper helper) {
		FluidTankBlockEntity tank = place(helper);
		tank.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
		tank.fluidTank.amount = 3_750;

		CompoundTag tag = tank.saveCustomOnly(helper.getLevel().registryAccess());
		FluidTankBlockEntity nbtCopy = new FluidTankBlockEntity(tank.getBlockPos(), tank.getBlockState());
		nbtCopy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
				helper.getLevel().registryAccess(), tag));
		if (!nbtCopy.fluidTank.fluid.is(Fluids.WATER) || nbtCopy.fluidTank.amount != 3_750) {
			helper.fail("water contents did not survive NBT round-trip");
		}

		DataComponentMap components = tank.collectComponents();
		FluidTankContents carried = components.get(ModDataComponents.FLUID_TANK_CONTENTS.get());
		if (carried == null || carried.fluid().value() != Fluids.WATER || carried.amount() != 3_750) {
			helper.fail("portable atomic contents component was not collected correctly: " + carried);
		}
		if (!Integer.valueOf(1).equals(components.get(DataComponents.MAX_STACK_SIZE))) {
			helper.fail("filled tank drop did not receive max_stack_size=1");
		}

		FluidTankBlockEntity placedCopy = new FluidTankBlockEntity(tank.getBlockPos(), tank.getBlockState());
		placedCopy.applyComponents(components, DataComponentPatch.EMPTY);
		if (!placedCopy.fluidTank.fluid.is(Fluids.WATER) || placedCopy.fluidTank.amount != 3_750) {
			helper.fail("placed tank did not restore its item component");
		}
		helper.succeed();
	}

	@GameTest
	public void tcFluidTank001Safe01_filledItemIsAtomicAndUnstackable(GameTestHelper helper) {
		FluidTankBlockEntity tank = place(helper);
		tank.fluidTank.fluid = FluidHolder.of(Fluids.LAVA);
		tank.fluidTank.amount = FluidAmounts.BUCKET;

		ItemStack filled = new ItemStack(ModItems.FLUID_TANK_ITEM);
		filled.applyComponents(tank.collectComponents());
		if (filled.getMaxStackSize() != 1 || filled.isStackable()) {
			helper.fail("filled portable tank must have max stack size 1 and be unstackable");
		}
		ItemStack copy = filled.copy();
		ItemStack split = copy.split(1);
		FluidTankContents splitContents = split.get(ModDataComponents.FLUID_TANK_CONTENTS.get());
		if (splitContents == null || splitContents.amount() != FluidAmounts.BUCKET
				|| splitContents.fluid().value() != Fluids.LAVA) {
			helper.fail("copy/split lost or separated the atomic fluid contents component");
		}

		ItemStack empty = new ItemStack(ModItems.FLUID_TANK_ITEM);
		if (empty.get(ModDataComponents.FLUID_TANK_CONTENTS.get()) != null || empty.getMaxStackSize() <= 1) {
			helper.fail("fresh empty tanks must carry no contents component and remain stackable");
		}
		helper.succeed();
	}

	@GameTest
	public void tcFluidTank001Bva01_componentAmountClampsToCapacity(GameTestHelper helper) {
		FluidTankBlockEntity original = place(helper);
		DataComponentMap overCapacity = DataComponentMap.builder()
				.set(ModDataComponents.FLUID_TANK_CONTENTS.get(),
						new FluidTankContents(Fluids.WATER.builtInRegistryHolder(), Config.fluidTankCapacity + 50_000L))
				.set(DataComponents.MAX_STACK_SIZE, 1)
				.build();
		FluidTankBlockEntity restored = new FluidTankBlockEntity(original.getBlockPos(), original.getBlockState());
		restored.applyComponents(overCapacity, DataComponentPatch.EMPTY);
		if (restored.fluidTank.amount != Config.fluidTankCapacity
				|| !restored.fluidTank.fluid.is(Fluids.WATER)) {
			helper.fail("component amount was not clamped to configured capacity");
		}
		helper.succeed();
	}

	@GameTest
	public void tcFluidTank001Int01_allFacesExposeTransactionalFabricStorage(GameTestHelper helper) {
		FluidTankBlockEntity tank = place(helper);
		for (Direction side : Direction.values()) {
			Storage<FluidVariant> storage = FluidStorage.SIDED.find(
					helper.getLevel(), tank.getBlockPos(), side);
			if (storage == null) {
				helper.fail("missing Fabric fluid capability on face " + side);
				return;
			}
		}

		Storage<FluidVariant> storage = FluidStorage.SIDED.find(
				helper.getLevel(), tank.getBlockPos(), Direction.UP);
		try (Transaction tx = Transaction.openOuter()) {
			long inserted = storage.insert(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, tx);
			if (inserted != FluidConstants.BUCKET) {
				helper.fail("capability did not accept exactly one bucket");
			}
			tx.commit();
		}
		if (tank.fluidTank.amount != FluidAmounts.BUCKET || !tank.fluidTank.fluid.is(Fluids.WATER)) {
			helper.fail("Fabric droplet conversion did not store exactly 1000 mB of water");
		}

		try (Transaction tx = Transaction.openOuter()) {
			long rejected = storage.insert(FluidVariant.of(Fluids.LAVA), FluidConstants.BUCKET, tx);
			if (rejected != 0) {
				helper.fail("single-fluid tank accepted lava while it contained water");
			}
			// Deliberately no commit: the failed operation must leave the tank unchanged.
		}
		if (tank.fluidTank.amount != FluidAmounts.BUCKET || !tank.fluidTank.fluid.is(Fluids.WATER)) {
			helper.fail("rejected/rolled-back transaction changed tank contents");
		}
		helper.succeed();
	}

	/**
	 * Placing a filled tank keeps its contents through the real placement path — {@code BlockItem.place},
	 * which places the block first and applies the components after.
	 *
	 * <p>Note what this does NOT cover: that the client is told. The tank renders what the client
	 * knows, and the client only learns through a block update, which {@code applyImplicitComponents}
	 * has to ask for — {@code setChanged()} alone just marks the chunk dirty, leaving a freshly placed
	 * filled tank rendering empty until the chunk reloads. A server-side gametest cannot see that:
	 * every server-side assertion here passes with or without the update. Verified by hand in the
	 * client instead; a real regression test needs a client gametest.
	 */
	@GameTest
	public void tcFluidTank001Per02_placingAFilledTankKeepsItsContents(GameTestHelper helper) {
		FluidTankBlockEntity source = place(helper);
		source.fluidTank.fluid = FluidHolder.of(Fluids.WATER);
		source.fluidTank.amount = FluidAmounts.BUCKET;
		ItemStack filled = new ItemStack(ModItems.FLUID_TANK_ITEM);
		filled.applyComponents(source.collectComponents());
		helper.setBlock(POS, net.minecraft.world.level.block.Blocks.AIR);

		BlockPos target = POS.east();
		helper.setBlock(target.below(), net.minecraft.world.level.block.Blocks.STONE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().instabuild = false;
		player.setItemInHand(InteractionHand.MAIN_HAND, filled);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(target.below())),
				Direction.UP, helper.absolutePos(target.below()), false);

		InteractionResult placed = player.gameMode.useItemOn(player, helper.getLevel(),
				player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
		if (!placed.consumesAction()) {
			helper.fail("the filled tank item did not place");
		}

		FluidTankBlockEntity tank = helper.getBlockEntity(target, FluidTankBlockEntity.class);
		if (tank.fluidTank.amount != FluidAmounts.BUCKET || !tank.fluidTank.fluid.is(Fluids.WATER)) {
			helper.fail("placing a filled tank lost its contents");
		}
		helper.succeed();
	}

	@GameTest
	public void tcFluidTank001Fun01_bucketAndCapsuleUseRealClickRouting(GameTestHelper helper) {
		FluidTankBlockEntity tank = place(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().instabuild = false;
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(tank.getBlockPos()), Direction.UP,
				tank.getBlockPos(), false);

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
		InteractionResult bucketIn = player.gameMode.useItemOn(player, helper.getLevel(),
				player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
		if (!bucketIn.consumesAction() || !player.getMainHandItem().is(Items.BUCKET)
				|| tank.fluidTank.amount != FluidAmounts.BUCKET || !tank.fluidTank.fluid.is(Fluids.WATER)) {
			helper.fail("water bucket did not deposit exactly 1000 mB through the real click route");
		}

		InteractionResult bucketOut = player.gameMode.useItemOn(player, helper.getLevel(),
				player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
		if (!bucketOut.consumesAction() || !player.getMainHandItem().is(Items.WATER_BUCKET)
				|| tank.fluidTank.amount != 0) {
			helper.fail("empty bucket did not retrieve exactly 1000 mB through the real click route");
		}

		ItemStack filledCapsule = new ItemStack(ModItems.FILLED_VACUUM_CAPSULE);
		ItemFluid.set(filledCapsule, Fluids.LAVA);
		player.setItemInHand(InteractionHand.MAIN_HAND, filledCapsule);
		InteractionResult capsuleIn = player.gameMode.useItemOn(player, helper.getLevel(),
				player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
		if (!capsuleIn.consumesAction() || !player.getMainHandItem().is(ModItems.VACUUM_CAPSULE)
				|| tank.fluidTank.amount != FluidAmounts.BUCKET || !tank.fluidTank.fluid.is(Fluids.LAVA)) {
			helper.fail("filled Vacuum Capsule did not deposit lava into the portable tank");
		}

		InteractionResult capsuleOut = player.gameMode.useItemOn(player, helper.getLevel(),
				player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
		if (!capsuleOut.consumesAction()
				|| !player.getMainHandItem().is(ModItems.FILLED_VACUUM_CAPSULE)
				|| ItemFluid.get(player.getMainHandItem()) != Fluids.LAVA
				|| tank.fluidTank.amount != 0) {
			helper.fail("empty Vacuum Capsule did not retrieve lava from the portable tank");
		}
		helper.succeed();
	}
}
