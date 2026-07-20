package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.ItemEnergy;
import dev.alaindustrial.item.MagnetItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral gametest bodies for the Electromagnet (MOD-132, suite TC-MAGNET-001). Same pattern as
 * {@link ElectricDrillScenarios}/{@link EnergyPackScenarios}: plain {@code GameTestHelper} bodies wrapped
 * by the Fabric {@code MagnetGameTest} suite and registered on the NeoForge {@code gameTestServer} lane
 * via {@code NeoForgeGameTests} — both loaders exercise the SAME pull logic.
 *
 * <p>The pull is driven by calling {@link MagnetItem#pullSingle} on a single, <em>detached</em>
 * {@link ItemEntity} (constructed relative to the player but never added to the world). This is the
 * per-item core of {@code magnetStep} without the world scan — deliberately so: on a shared gametest
 * server the live scan's radius would pick up the dropped items of neighbouring tests running a few
 * blocks away (and a freshly {@code addFreshEntity}-ed item is not reliably indexed for a same-tick
 * scan), which made count/EU assertions flaky. A detached entity is visible only to this test, so every
 * assertion is deterministic. Numbers come from {@link Config} (magnetBuffer, magnetEuPerItem,
 * magnetRange), the balance source of truth.
 */
public final class MagnetScenarios {

	private MagnetScenarios() {}

	private static ItemStack magnet(long eu) {
		ItemStack stack = new ItemStack(ModContent.ELECTROMAGNET.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	/** A survival ServerPlayer mock with {@code instabuild} off, so {@link ItemEnergy#free} is false and EU
	 * is actually spent (the mock's gameMode() hardcodes CREATIVE — see ElectricDrillScenarios). */
	private static ServerPlayer makeSurvivalPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().instabuild = false;
		return player;
	}

	/**
	 * A loose iron-ingot drop {@code (dx, dy, dz)} from the player, with the given pickup delay —
	 * <em>detached</em> (never added to the world), so only this test can see or move it.
	 */
	private static ItemEntity dropNear(GameTestHelper helper, ServerPlayer player, double dx, double dy, double dz,
			int pickupDelay) {
		Vec3 p = player.position();
		ItemEntity item = new ItemEntity(helper.getLevel(), p.x + dx, p.y + dy, p.z + dz,
				new ItemStack(Items.IRON_INGOT));
		item.setDeltaMovement(Vec3.ZERO);
		item.setPickUpDelay(pickupDelay);
		return item;
	}

	/**
	 * TC-MAGNET-001-FUN01 — a charged, enabled magnet pulls a nearby drop toward the player
	 *     (velocity points at the player) and spends exactly magnetEuPerItem.
	 */
	public static void fun01PullsNearbyDrop(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		ItemEntity item = dropNear(helper, player, 2.5, 0.5, 0.0, 0);
		ItemStack magnet = magnet(Config.magnetBuffer);

		if (!MagnetItem.pullSingle(magnet, player, item)) {
			helper.fail("a charged, enabled magnet must pull a nearby drop");
		}
		if (item.getDeltaMovement().x >= 0.0) {
			helper.fail("pulled item must accelerate toward the player (-x), got dx=" + item.getDeltaMovement().x);
		}
		long expected = Config.magnetBuffer - Config.magnetEuPerItem;
		if (ItemEnergy.get(magnet) != expected) {
			helper.fail("pulling one item must spend magnetEuPerItem; left " + ItemEnergy.get(magnet)
					+ ", expected " + expected);
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-001-FUN02 — a flat magnet (0 EU) pulls nothing and the drop keeps zero velocity.
	 */
	public static void fun02FlatMagnetInert(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		ItemEntity item = dropNear(helper, player, 2.5, 0.5, 0.0, 0);
		ItemStack magnet = magnet(0);

		if (MagnetItem.pullSingle(magnet, player, item) || item.getDeltaMovement().length() > 1.0e-9) {
			helper.fail("a flat magnet must not move anything");
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-001-FUN03 — a disabled magnet (toggled off) pulls nothing even while charged.
	 */
	public static void fun03DisabledMagnetInert(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		ItemEntity item = dropNear(helper, player, 2.5, 0.5, 0.0, 0);
		ItemStack magnet = magnet(Config.magnetBuffer);
		MagnetItem.setEnabled(magnet, false);

		if (MagnetItem.pullSingle(magnet, player, item) || item.getDeltaMovement().length() > 1.0e-9) {
			helper.fail("a disabled magnet must not move anything");
		}
		if (ItemEnergy.get(magnet) != Config.magnetBuffer) {
			helper.fail("a disabled magnet must spend no EU");
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-001-FUN04 — a drop still on its pickup delay (a fresh Q-drop) is left alone, so
	 *     the magnet does not instantly suck back what was just thrown.
	 */
	public static void fun04RespectsPickupDelay(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		ItemEntity item = dropNear(helper, player, 2.5, 0.5, 0.0, 40);
		ItemStack magnet = magnet(Config.magnetBuffer);

		if (MagnetItem.pullSingle(magnet, player, item) || item.getDeltaMovement().length() > 1.0e-9) {
			helper.fail("an item on pickup delay must not be pulled");
		}
		if (ItemEnergy.get(magnet) != Config.magnetBuffer) {
			helper.fail("an item on pickup delay must cost no EU");
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-001-FUN05 — the spherical range refinement: a drop at a cube corner (dx=dz=range−0.4, so
	 *     distance ≈ √2·(range−0.4) &gt; range) sits inside the broad-phase {@code inflate(range)} cube but
	 *     outside the sphere, and must NOT be pulled or charged. A control drop the same axis-distance
	 *     straight ahead IS inside the sphere and IS pulled — proving it is the sphere trim
	 *     ({@code canPull}'s {@code distanceToSqr}), which a cube-only test would leave unexercised.
	 */
	public static void fun05OutOfRangeIgnored(GameTestHelper helper) {
		ServerPlayer player = makeSurvivalPlayer(helper);
		double corner = Config.magnetRange - 0.4; // inside inflate(range) cube, outside the sphere (√2·corner > range)
		ItemEntity outside = dropNear(helper, player, corner, 0.5, corner, 0);
		ItemStack magnet = magnet(Config.magnetBuffer);

		if (MagnetItem.pullSingle(magnet, player, outside) || outside.getDeltaMovement().length() > 1.0e-9) {
			helper.fail("a drop inside the AABB but outside the range sphere must not be pulled");
		}
		if (ItemEnergy.get(magnet) != Config.magnetBuffer) {
			helper.fail("an out-of-sphere drop must cost no EU");
		}
		// Control: a drop the same axis-distance straight ahead is inside the sphere and IS pulled.
		ItemEntity inside = dropNear(helper, player, corner, 0.5, 0.0, 0);
		if (!MagnetItem.pullSingle(magnet, player, inside)) {
			helper.fail("a drop within the range sphere must still be pulled (control)");
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-001-FUN06 — the toggle entry point {@link MagnetItem#use}: a plain (non-sneak) right-click
	 *     passes through and leaves the magnet enabled; a sneak right-click flips it off, and again back on.
	 *     Guards the {@code isShiftKeyDown} gate and the server-side mutation that {@code per01} (static
	 *     helpers only) does not exercise.
	 */
	public static void fun06ToggleViaUse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = makeSurvivalPlayer(helper);
		ItemStack magnet = magnet(Config.magnetBuffer);
		player.setItemInHand(InteractionHand.MAIN_HAND, magnet);

		player.setShiftKeyDown(false);
		InteractionResult plain = magnet.getItem().use(level, player, InteractionHand.MAIN_HAND);
		if (plain != InteractionResult.PASS || !MagnetItem.isEnabled(magnet)) {
			helper.fail("a non-sneak use must pass through and leave the magnet enabled");
		}

		player.setShiftKeyDown(true);
		InteractionResult off = magnet.getItem().use(level, player, InteractionHand.MAIN_HAND);
		if (off != InteractionResult.SUCCESS || MagnetItem.isEnabled(magnet)) {
			helper.fail("a sneak use must toggle the magnet off");
		}

		InteractionResult on = magnet.getItem().use(level, player, InteractionHand.MAIN_HAND);
		if (on != InteractionResult.SUCCESS || !MagnetItem.isEnabled(magnet)) {
			helper.fail("a second sneak use must toggle the magnet back on");
		}
		helper.succeed();
	}

	/**
	 * TC-MAGNET-001-PER01 — the on/off flag round-trips: enabled is the absent-component default,
	 *     disabling stores it, re-enabling removes it (stacks stay component-identical to a fresh magnet).
	 */
	public static void per01ToggleRoundTrip(GameTestHelper helper) {
		ItemStack magnet = magnet(Config.magnetBuffer);
		if (!MagnetItem.isEnabled(magnet) || magnet.has(ModDataComponents.MAGNET_ENABLED.get())) {
			helper.fail("a fresh magnet must read enabled with no component present");
		}
		MagnetItem.setEnabled(magnet, false);
		if (MagnetItem.isEnabled(magnet) || !Boolean.FALSE.equals(magnet.get(ModDataComponents.MAGNET_ENABLED.get()))) {
			helper.fail("disabling must store false and read disabled");
		}
		MagnetItem.setEnabled(magnet, true);
		if (!MagnetItem.isEnabled(magnet) || magnet.has(ModDataComponents.MAGNET_ENABLED.get())) {
			helper.fail("re-enabling must remove the component (back to the fresh default)");
		}
		helper.succeed();
	}
}
