package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.PumpBlockEntity;
import dev.alaindustrial.core.EnergyTransactions;
import dev.alaindustrial.core.FluidAmounts;
import dev.alaindustrial.core.FluidHolder;
import dev.alaindustrial.item.ItemFluid;
import dev.alaindustrial.menu.PumpMenu;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametest bodies for the Vacuum Capsule (MOD-063, suite TC-CAPS-001). Same pattern as
 * {@link CoreFluidScenarios}/{@link PouchScenarios}: plain {@code Consumer<GameTestHelper>} bodies over
 * vanilla {@code GameTestHelper} + neutral content, wrapped by the Fabric {@code CapsuleGameTest} suite
 * and registered on the NeoForge {@code gameTestServer} lane via {@code NeoForgeGameTests} — both loaders
 * exercise the SAME capsule logic (the {@code capsule_fluid} data component resolves per loader).
 *
 * <p>Covers the mod-specific behaviour: the {@code capsule_fluid} component round-trip and absent-means-empty
 * rule, per-fluid stacking, and the exchange with a neighbouring mod tank via {@code FluidLookup} (both
 * directions). World fill/place ports vanilla {@code BucketItem} verbatim and is verified in the dev client.
 */
public final class CapsuleScenarios {

	private CapsuleScenarios() {
	}

	private static final BlockPos PUMP = new BlockPos(1, 2, 1);

	private static ItemStack empty() {
		return new ItemStack(ModContent.VACUUM_CAPSULE.get());
	}

	private static ItemStack filled(net.minecraft.world.level.material.Fluid fluid) {
		ItemStack stack = new ItemStack(ModContent.FILLED_VACUUM_CAPSULE.get());
		ItemFluid.set(stack, fluid);
		return stack;
	}

	private static PumpBlockEntity placePump(GameTestHelper helper) {
		helper.setBlock(PUMP, ModContent.PUMP.get().defaultBlockState());
		PumpBlockEntity be = helper.getBlockEntity(PUMP, PumpBlockEntity.class);
		if (be == null) {
			helper.fail("pump block entity missing after placement");
		}
		return be;
	}

	private static void fill(PumpBlockEntity pump, net.minecraft.world.level.material.Fluid fluid, long mb) {
		EnergyTransactions.get().runCommitting(txn -> pump.fluidTank.insert(FluidHolder.of(fluid), mb, txn));
	}

	private static UseOnContext ctxOn(GameTestHelper helper, Player player, BlockPos rel, Direction face) {
		BlockPos abs = helper.absolutePos(rel);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false);
		return new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
	}

	// ── PER01: component round-trip + absent-means-empty ──────────────────────────────────────────

	/**
	 * The {@code capsule_fluid} component stores and returns the fluid, and writing empty removes it (a
	 * drained capsule is component-identical to a crafted one). Traced by {@code CapsuleGameTest} PER01.
	 */
	public static void per01ComponentRoundTrip(GameTestHelper helper) {
		ItemStack stack = filled(Fluids.WATER);
		if (ItemFluid.get(stack) != Fluids.WATER || ItemFluid.isEmpty(stack)) {
			helper.fail("capsule did not store water");
			return;
		}
		ItemFluid.set(stack, Fluids.EMPTY);
		if (!ItemFluid.isEmpty(stack) || stack.has(ModDataComponents.CAPSULE_FLUID.get())) {
			helper.fail("writing empty did not remove the capsule_fluid component");
			return;
		}
		if (!ItemFluid.isEmpty(empty())) {
			helper.fail("a fresh empty capsule reported non-empty");
			return;
		}
		helper.succeed();
	}

	// ── FUN01: stacking is per-fluid ──────────────────────────────────────────────────────────────

	/**
	 * Two filled capsules of the same fluid share one component value and stack; different fluids never
	 * merge. This is the whole "16 buckets in one slot, separated by fluid" guarantee, riding vanilla
	 * component-equality (no custom stacking override). Traced by {@code CapsuleGameTest} FUN01.
	 */
	public static void fun01StackingByFluid(GameTestHelper helper) {
		boolean sameMerges = ItemStack.isSameItemSameComponents(filled(Fluids.WATER), filled(Fluids.WATER));
		boolean diffKept = !ItemStack.isSameItemSameComponents(filled(Fluids.WATER), filled(Fluids.LAVA));
		int max = filled(Fluids.WATER).getMaxStackSize();
		if (!sameMerges || !diffKept || max != dev.alaindustrial.item.FilledCapsuleItem.STACK_SIZE) {
			helper.fail("stacking wrong: sameMerges=" + sameMerges + " diffKept=" + diffKept + " max=" + max);
			return;
		}
		helper.succeed();
	}

	// ── FUN02: fill an empty capsule from a mod tank (extraction) ─────────────────────────────────

	/**
	 * Right-clicking a mod tank that holds at least one bucket with an empty capsule pulls exactly one
	 * bucket into the capsule (via {@code FluidLookup}) and swaps it for a filled one. Traced by
	 * {@code CapsuleGameTest} FUN02.
	 */
	public static void fun02FillFromTank(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper);
		if (pump == null) {
			return;
		}
		fill(pump, Fluids.LAVA, FluidAmounts.BUCKET);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, empty());
		InteractionResult result = ModContent.VACUUM_CAPSULE.get().useOn(ctxOn(helper, player, PUMP, Direction.UP));
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean handFilledLava = hand.is(ModContent.FILLED_VACUUM_CAPSULE.get()) && ItemFluid.get(hand) == Fluids.LAVA;
		boolean tankEmptied = pump.fluidTank.amount == 0;
		if (!result.consumesAction() || !handFilledLava || !tankEmptied) {
			helper.fail("fill-from-tank: result=" + result + " hand=" + hand + " tank=" + pump.fluidTank.amount);
			return;
		}
		helper.succeed();
	}

	// ── FUN03: empty a filled capsule into a mod tank (insertion) ─────────────────────────────────

	/**
	 * Right-clicking a mod tank with a filled capsule pushes its bucket into the tank and swaps back to an
	 * empty capsule. Traced by {@code CapsuleGameTest} FUN03.
	 */
	public static void fun03EmptyIntoTank(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper);
		if (pump == null) {
			return;
		}
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, filled(Fluids.LAVA));
		InteractionResult result = ModContent.FILLED_VACUUM_CAPSULE.get().useOn(ctxOn(helper, player, PUMP, Direction.UP));
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean handEmpty = hand.is(ModContent.VACUUM_CAPSULE.get());
		boolean tankHasLava = pump.fluidTank.amount == FluidAmounts.BUCKET && pump.fluidTank.fluid.is(Fluids.LAVA);
		if (!result.consumesAction() || !handEmpty || !tankHasLava) {
			helper.fail("empty-into-tank: result=" + result + " hand=" + hand
					+ " tank=" + pump.fluidTank.amount + " fluid=" + pump.fluidTank.fluid);
			return;
		}
		helper.succeed();
	}

	// ── FUN05/FUN06: capsule ↔ pump through the REAL right-click routing (MOD-099) ─────────────────
	//
	// FUN02/FUN03 prove the FluidLookup exchange works by calling Item#useOn directly. But vanilla's
	// ServerPlayerGameMode.useItemOn (26.2 bytecode) runs BlockState.useItemOn → useWithoutItem BEFORE
	// Item.useOn — and the pump's useWithoutItem opens the GUI with SUCCESS, an early return that used to
	// prevent the capsule's Item.useOn from ever running. FUN05/FUN06 drive the real gameMode path (the
	// first tests in the suite to do so) so a regression that re-introduces the GUI-eats-click ordering
	// fails here even though FUN02/FUN03 still pass.

	/** A survival ServerPlayer (real gameMode, no instabuild) so openMenu + item swap both behave. */
	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().instabuild = false;
		return player;
	}

	private static BlockHitResult hitOn(GameTestHelper helper, BlockPos rel, Direction face) {
		BlockPos abs = helper.absolutePos(rel);
		return new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false);
	}

	/**
	 * Right-clicking the pump block with an empty capsule (through the real ServerPlayerGameMode routing)
	 * pulls one bucket from the tank and swaps the capsule to filled. MOD-099 regression guard: without
	 * the PumpBlock.useItemOn override, the GUI opens instead and the tank is unchanged. Traced by
	 * {@code CapsuleGameTest} FUN05.
	 */
	public static void fun05UseRoutingFill(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper);
		if (pump == null) {
			return;
		}
		fill(pump, Fluids.LAVA, FluidAmounts.BUCKET);
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, empty());
		// The real click path — drives BlockState.useItemOn → (our override) → Item.useOn, exactly as a
		// player's right-click does. This is the call that a GUI-eats-click regression would bypass.
		InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(),
				player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
				hitOn(helper, PUMP, Direction.UP));
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean handFilledLava = hand.is(ModContent.FILLED_VACUUM_CAPSULE.get()) && ItemFluid.get(hand) == Fluids.LAVA;
		boolean tankEmptied = pump.fluidTank.amount == 0;
		if (!result.consumesAction() || !handFilledLava || !tankEmptied) {
			helper.fail("use-routing-fill: result=" + result + " hand=" + hand
					+ " tank=" + pump.fluidTank.amount);
			return;
		}
		helper.succeed();
	}

	/**
	 * Right-clicking the pump block with a filled capsule (through the real ServerPlayerGameMode routing)
	 * pushes its bucket into the tank and swaps back to an empty capsule. MOD-099 regression guard for the
	 * insertion direction. Traced by {@code CapsuleGameTest} FUN06.
	 */
	public static void fun06UseRoutingEmpty(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper);
		if (pump == null) {
			return;
		}
		ServerPlayer player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, filled(Fluids.LAVA));
		InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(),
				player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
				hitOn(helper, PUMP, Direction.UP));
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean handEmpty = hand.is(ModContent.VACUUM_CAPSULE.get());
		boolean tankHasLava = pump.fluidTank.amount == FluidAmounts.BUCKET && pump.fluidTank.fluid.is(Fluids.LAVA);
		if (!result.consumesAction() || !handEmpty || !tankHasLava) {
			helper.fail("use-routing-empty: result=" + result + " hand=" + hand
					+ " tank=" + pump.fluidTank.amount + " fluid=" + pump.fluidTank.fluid);
			return;
		}
		helper.succeed();
	}

	/**
	 * MOD-099 regression guard for the OFF-hand double-call bug: when the capsule is in the OFF hand,
	 * a naive {@code useItemOn} override that returned {@code TRY_WITH_EMPTY_HAND} on a no-op exchange
	 * let vanilla fall through to {@code Item.useOn} a <em>second</em> time, double-applying the swap.
	 * A tank starting with exactly 2 buckets must end with exactly 1 (one exchange), not 0 (two). Traced
	 * by {@code CapsuleGameTest} FUN07.
	 */
	public static void fun07UseRoutingOffHand(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper);
		if (pump == null) {
			return;
		}
		fill(pump, Fluids.LAVA, FluidAmounts.BUCKET * 2);
		ServerPlayer player = survivalPlayer(helper);
		// Empty capsule in the OFF hand; MAIN hand empty so vanilla dispatches the OFF-hand item.
		player.setItemInHand(InteractionHand.OFF_HAND, empty());
		player.gameMode.useItemOn(player, helper.getLevel(),
				player.getItemInHand(InteractionHand.OFF_HAND), InteractionHand.OFF_HAND,
				hitOn(helper, PUMP, Direction.UP));
		ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
		boolean offHandFilledOnce = offHand.is(ModContent.FILLED_VACUUM_CAPSULE.get())
				&& ItemFluid.get(offHand) == Fluids.LAVA
				&& offHand.getCount() == 1;
		// The decisive assertion: tank went 2 → 1 (one exchange). A double-call bug would leave 0.
		boolean tankHasOneBucket = pump.fluidTank.amount == FluidAmounts.BUCKET;
		if (!offHandFilledOnce || !tankHasOneBucket) {
			helper.fail("use-routing-offhand: offHand=" + offHand
					+ " tank=" + pump.fluidTank.amount + " (expected " + FluidAmounts.BUCKET + ")");
			return;
		}
		helper.succeed();
	}

	// ── FUN08: the pump's sync channels must survive the wire (MOD-099) ────────────────────────────
	//
	// ClientboundContainerSetDataPacket writes every ContainerData channel with writeShort and reads it
	// back with readShort (26.2 bytecode), so any value outside signed-short range silently arrives as its
	// low 16 bits. A MOD-099 revision shipped the fluid's packed ARGB on a channel and lost it exactly this
	// way: lava's 0xFFFF0000 reached the client as 0 (bar drew grey) and water's 0xFF4040FF as 0x40FF
	// (alpha 0 → bar drew invisible). Every server-side test passed, because none of them crossed the wire.
	// This test asserts the invariant the wire imposes, on the server, without needing a client.

	/**
	 * Every {@code PumpBlockEntity} sync channel must round-trip through a signed short unchanged, for any
	 * tank content. Guards the whole channel set (not just the one that broke) against a future channel
	 * carrying a colour, a raw mB amount, or any other >16-bit value. Traced by {@code CapsuleGameTest} FUN08.
	 */
	public static void fun08SyncChannelsFitShort(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper);
		if (pump == null) {
			return;
		}
		// Lava and water are the two fluids whose colours broke; a full-ish tank also maximises permille and
		// the projected progress/maxProgress channels.
		for (net.minecraft.world.level.material.Fluid fluid : new net.minecraft.world.level.material.Fluid[] {
				Fluids.LAVA, Fluids.WATER }) {
			pump.fluidTank.amount = 0;
			pump.fluidTank.fluid = FluidHolder.EMPTY;
			fill(pump, fluid, PumpBlockEntity.TANK_CAPACITY);
			ContainerData data = pump.getDataAccess();
			for (int channel = 0; channel < data.getCount(); channel++) {
				int value = data.get(channel);
				if ((short) value != value) {
					helper.fail("sync-channel-overflow: fluid=" + fluid + " channel=" + channel
							+ " value=" + value + " arrives as " + (short) value + " (short-encoded wire)");
					return;
				}
			}
		}
		helper.succeed();
	}

	// ── FUN10–FUN13: the pump's SLOTS exchange with fluid containers (MOD-107) ────────────────────
	//
	// MOD-107 replaced the pump's hardcoded lava/water bucket handling with ItemFluidBridge (the loader's
	// item fluid capability), so buckets, our capsule and foreign containers share one mechanism. The bucket
	// slots had NO test coverage at all before this, so FUN10/FUN11 are regression guards for behaviour that
	// already shipped, and FUN12/FUN13 cover the capsule the player reported as "just sits there".

	/** Drop {@code stack} into {@code slot} of the pump and let the machine tick act on it. */
	private static void putSlot(PumpBlockEntity pump, int slot, ItemStack stack) {
		pump.setItem(slot, stack);
	}

	/**
	 * REGRESSION GUARD (MOD-107): a vanilla lava bucket in the fill slot still empties into the tank and
	 * leaves an empty bucket in the fill-output slot — the behaviour that existed before the bridge and had
	 * no test. Traced by {@code CapsuleGameTest} FUN10.
	 */
	public static void fun10BucketFillsTankFromSlot(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper);
		if (pump == null) {
			return;
		}
		putSlot(pump, PumpBlockEntity.FILL_INPUT_SLOT, new ItemStack(Items.LAVA_BUCKET));
		helper.runAfterDelay(3, () -> {
			boolean tankOk = pump.fluidTank.amount == FluidAmounts.BUCKET && pump.fluidTank.fluid.is(Fluids.LAVA);
			ItemStack out = pump.getItem(PumpBlockEntity.FILL_OUTPUT_SLOT);
			ItemStack in = pump.getItem(PumpBlockEntity.FILL_INPUT_SLOT);
			if (!tankOk || !out.is(Items.BUCKET) || !in.isEmpty()) {
				helper.fail("bucket-fill-slot: tank=" + pump.fluidTank.amount + " fluid=" + pump.fluidTank.fluid
						+ " out=" + out + " in=" + in);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * REGRESSION GUARD (MOD-107): an empty bucket in the drain slot still fills from the tank and leaves a
	 * lava bucket in the drain-output slot. Traced by {@code CapsuleGameTest} FUN11.
	 */
	public static void fun11BucketDrainsTankFromSlot(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper);
		if (pump == null) {
			return;
		}
		fill(pump, Fluids.LAVA, FluidAmounts.BUCKET);
		putSlot(pump, PumpBlockEntity.DRAIN_INPUT_SLOT, new ItemStack(Items.BUCKET));
		helper.runAfterDelay(3, () -> {
			ItemStack out = pump.getItem(PumpBlockEntity.DRAIN_OUTPUT_SLOT);
			if (pump.fluidTank.amount != 0 || !out.is(Items.LAVA_BUCKET)) {
				helper.fail("bucket-drain-slot: tank=" + pump.fluidTank.amount + " out=" + out);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * MOD-107 — the player's report: a filled capsule in the fill slot must empty into the tank and leave an
	 * empty capsule in the fill-output slot. Before the bridge it just sat in the slot doing nothing.
	 * Traced by {@code CapsuleGameTest} FUN12.
	 */
	public static void fun12CapsuleFillsTankFromSlot(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper);
		if (pump == null) {
			return;
		}
		putSlot(pump, PumpBlockEntity.FILL_INPUT_SLOT, filled(Fluids.LAVA));
		helper.runAfterDelay(3, () -> {
			boolean tankOk = pump.fluidTank.amount == FluidAmounts.BUCKET && pump.fluidTank.fluid.is(Fluids.LAVA);
			ItemStack out = pump.getItem(PumpBlockEntity.FILL_OUTPUT_SLOT);
			ItemStack in = pump.getItem(PumpBlockEntity.FILL_INPUT_SLOT);
			if (!tankOk || !out.is(ModContent.VACUUM_CAPSULE.get()) || !in.isEmpty()) {
				helper.fail("capsule-fill-slot: tank=" + pump.fluidTank.amount + " fluid=" + pump.fluidTank.fluid
						+ " out=" + out + " in=" + in);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * MOD-107 — the other direction: an empty capsule in the drain slot fills from the tank and leaves a
	 * filled capsule carrying the tank's fluid in the drain-output slot. Traced by {@code CapsuleGameTest}
	 * FUN13.
	 */
	public static void fun13CapsuleDrainsTankFromSlot(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper);
		if (pump == null) {
			return;
		}
		fill(pump, Fluids.WATER, FluidAmounts.BUCKET);
		putSlot(pump, PumpBlockEntity.DRAIN_INPUT_SLOT, empty());
		helper.runAfterDelay(3, () -> {
			ItemStack out = pump.getItem(PumpBlockEntity.DRAIN_OUTPUT_SLOT);
			boolean filledWater = out.is(ModContent.FILLED_VACUUM_CAPSULE.get()) && ItemFluid.get(out) == Fluids.WATER;
			if (pump.fluidTank.amount != 0 || !filledWater) {
				helper.fail("capsule-drain-slot: tank=" + pump.fluidTank.amount + " out=" + out);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * The client-side {@code PumpMenu} stub must declare exactly as many data channels as the block entity
	 * projects. A stub narrower than the server's data throws when the screen reads the missing channel;
	 * a wider one silently reads zeros. Traced by {@code CapsuleGameTest} FUN09.
	 */
	public static void fun09MenuStubWidthMatches(GameTestHelper helper) {
		PumpBlockEntity pump = placePump(helper);
		if (pump == null) {
			return;
		}
		int serverChannels = pump.getDataAccess().getCount();
		ServerPlayer player = survivalPlayer(helper);
		// The client stub constructor — the one a real client builds on menu open.
		PumpMenu stub = new PumpMenu(1, player.getInventory());
		int stubChannels = stub.getDataChannelCount();
		if (stubChannels != serverChannels) {
			helper.fail("menu-stub-width: client stub has " + stubChannels + " channels, block entity projects "
					+ serverChannels + " — the screen would read a missing or stale channel");
			return;
		}
		helper.succeed();
	}
}
