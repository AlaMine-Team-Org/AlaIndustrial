package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.item.EnergyPackItem;
import dev.alaindustrial.item.ItemEnergy;
import dev.alaindustrial.item.JetpackItem;
import dev.alaindustrial.item.JetpackLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.level.GameType;

/**
 * Loader-neutral gametest bodies for the Jetpack (MOD-148, suite TC-JET-001). Same pattern as
 * {@link EnergyPackScenarios}: plain {@code GameTestHelper} bodies wrapped by the Fabric
 * {@code JetpackGameTest} suite and registered on the NeoForge {@code gameTestServer} lane via
 * {@code NeoForgeGameTests}.
 *
 * <p>The flight logic is driven through {@link JetpackItem#serverFlightTick} with the held-jump
 * state passed in — the real tick reads it from the vanilla input sync
 * ({@code ServerPlayer.getLastClientInput()}), which a mock player cannot press. The airborne gate
 * is exercised through {@code setOnGround}; the client-side velocity change is not testable here
 * (client-authoritative motion has no server-side assertion surface) and is covered by the manual
 * dev-client check in the task log.
 */
public final class JetpackScenarios {

	private JetpackScenarios() {}

	private static final BlockPos BOX = new BlockPos(1, 2, 1);

	private static ItemStack jetpack(long eu) {
		ItemStack stack = new ItemStack(dev.alaindustrial.registry.ModContent.JETPACK.get());
		ItemEnergy.set(stack, eu);
		return stack;
	}

	/** A survival mock player pinned airborne with an accumulated fall — the flight-tick premise.
	 * A ground tick runs first so the static airborne-thrust session is deterministically cleared
	 * even if another test's mock player shared this UUID. */
	private static Player airborne(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setOnGround(true);
		JetpackItem.serverFlightTick(jetpack(0), helper.getLevel(), player, false);
		player.setOnGround(false);
		player.fallDistance = 10.0;
		return player;
	}

	// ── FUN — functional ─────────────────────────────────────────────────────────────────────────

	/** FUN01: a held jump while airborne burns exactly jetpackEuPerTick per tick and zeroes the fall. */
	public static void fun01ThrustBurnsEuAndZeroesFall(GameTestHelper helper) {
		Player player = airborne(helper);
		ItemStack jetpack = jetpack(Config.jetpackBuffer);

		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		if (ItemEnergy.get(jetpack) != Config.jetpackBuffer - Config.jetpackEuPerTick) {
			helper.fail("one thrust tick must burn exactly " + Config.jetpackEuPerTick
					+ " EU, left " + ItemEnergy.get(jetpack));
		}
		if (player.fallDistance != 0.0) {
			helper.fail("a thrust tick must reset the fall distance, got " + player.fallDistance);
		}
		for (int i = 0; i < 9; i++) {
			JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		}
		if (ItemEnergy.get(jetpack) != Config.jetpackBuffer - 10L * Config.jetpackEuPerTick) {
			helper.fail("ten thrust ticks must burn ten times the per-tick cost");
		}
		helper.succeed();
	}

	/** FUN02: releasing jump stops the engine — no burn, and the fall distance accumulates again. */
	public static void fun02ReleasedJumpBurnsNothing(GameTestHelper helper) {
		Player player = airborne(helper);
		ItemStack jetpack = jetpack(Config.jetpackBuffer);

		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, false);
		if (ItemEnergy.get(jetpack) != Config.jetpackBuffer) {
			helper.fail("no jump held: the engine must burn nothing");
		}
		if (player.fallDistance != 10.0) {
			helper.fail("no jump held: the fall distance must keep accumulating");
		}
		helper.succeed();
	}

	/** FUN03: an engine that cuts out mid-flight glides — burns nothing more, but the held jump
	 * still zeroes the fall (the glide is the safety net of THIS flight's thrust). */
	public static void fun03DrainedGlideStillZeroesFall(GameTestHelper helper) {
		Player player = airborne(helper);
		// One tick's worth of charge: the first held tick thrusts (marking the flight), then cuts out.
		ItemStack jetpack = jetpack(Config.jetpackEuPerTick);

		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		if (ItemEnergy.get(jetpack) != 0) {
			helper.fail("the only charged tick must drain the buffer to exactly 0");
		}
		player.fallDistance = 10.0;
		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		if (ItemEnergy.get(jetpack) != 0) {
			helper.fail("a drained jetpack must not go below 0 EU");
		}
		if (player.fallDistance != 0.0) {
			helper.fail("after a mid-flight cutout the powerless glide must still protect from fall damage");
		}
		helper.succeed();
	}

	/** FUN07: the last tick of a nearly-empty jetpack burns the remainder — the buffer hits a clean
	 * 0, never a dead sub-cost tail (15–20 EU showing an unusable 1%, the playtest bug). */
	public static void fun07TailChargeDrainsToZero(GameTestHelper helper) {
		Player player = airborne(helper);
		long tail = Config.jetpackEuPerTick / 3;
		ItemStack jetpack = jetpack(tail);

		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		if (ItemEnergy.get(jetpack) != 0) {
			helper.fail("a sub-cost tail (" + tail + " EU) must be burned to exactly 0, left "
					+ ItemEnergy.get(jetpack));
		}
		if (player.fallDistance != 0.0) {
			helper.fail("the tail tick is still a thrust tick — the fall distance must reset");
		}
		helper.succeed();
	}

	/** FUN04: the jetpack charges in the Battery Box slot at min(LV ceiling, its own intake rate). */
	public static void fun04ChargeInBatteryBox(GameTestHelper helper) {
		helper.setBlock(BOX, dev.alaindustrial.registry.ModContent.BATTERY_BOX.get());
		BatteryBoxBlockEntity box = helper.getBlockEntity(BOX, BatteryBoxBlockEntity.class);
		if (box == null) {
			helper.fail("battery_box block entity missing");
		}
		box.getEnergyStorage().amount = box.getEnergyStorage().getCapacity();
		box.setItem(BatteryBoxBlockEntity.CHARGE_SLOT, jetpack(0));

		box.serverTick(helper.getLevel(), box.getBlockPos(), helper.getLevel().getBlockState(box.getBlockPos()));
		long expected = Math.min(EnergyTier.LV.maxVoltage(), Config.jetpackInputRate);
		long gained = ItemEnergy.get(box.getItem(BatteryBoxBlockEntity.CHARGE_SLOT));
		if (gained != expected) {
			helper.fail("one tick must move min(LV ceiling, jetpack intake) = " + expected + " EU, got " + gained);
		}
		helper.succeed();
	}

	/** FUN05: a worn Energy Pack tops the jetpack up like any other powered item in the inventory. */
	public static void fun05WornPackChargesJetpack(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack pack = new ItemStack(dev.alaindustrial.registry.ModContent.ENERGY_PACK.get());
		ItemEnergy.set(pack, Config.energyPackBuffer);
		ItemStack jetpack = jetpack(0);
		player.getInventory().setItem(0, jetpack);

		long step = (long) Config.energyPackOutputRate * 20L;
		long moved = EnergyPackItem.chargeStep(pack, player);
		if (moved != step || ItemEnergy.get(jetpack) != step) {
			helper.fail("the pack must charge a carried jetpack by one full batch, moved " + moved);
		}
		helper.succeed();
	}

	/** FUN06: the worn asset follows the charge — lit while charged, drained at 0 (both explicit). */
	public static void fun06WornAssetFollowsCharge(GameTestHelper helper) {
		ItemStack jetpack = jetpack(Config.jetpackBuffer);
		if (assetOf(jetpack) != JetpackItem.JETPACK_ASSET) {
			helper.fail("a charged jetpack must wear the lit asset");
		}
		ItemEnergy.set(jetpack, 0);
		if (assetOf(jetpack) != JetpackItem.JETPACK_OFF_ASSET) {
			helper.fail("a drained jetpack must wear the off asset");
		}
		if (jetpack.get(DataComponents.EQUIPPABLE) == null) {
			helper.fail("draining must never drop the EQUIPPABLE component (the jetpack would stop being wearable)");
		}
		helper.succeed();
	}

	// ── NEG — nothing happens when it should not ─────────────────────────────────────────────────

	/** NEG01: creative burns no EU (the charge stays), but the flight safety still applies. */
	public static void neg01CreativeBurnsNothing(GameTestHelper helper) {
		Player player = airborne(helper);
		GameType.CREATIVE.updatePlayerAbilities(player.getAbilities());
		ItemStack jetpack = jetpack(Config.jetpackBuffer);

		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		if (ItemEnergy.get(jetpack) != Config.jetpackBuffer) {
			helper.fail("creative thrust must not burn EU (ItemEnergy.spend guard)");
		}
		if (player.fallDistance != 0.0) {
			helper.fail("creative thrust must still reset the fall distance");
		}
		helper.succeed();
	}

	/** NEG02: on the ground the engine is off — a held jump is a vanilla jump, not a burn. */
	public static void neg02OnGroundBurnsNothing(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setOnGround(true);
		player.fallDistance = 10.0;
		ItemStack jetpack = jetpack(Config.jetpackBuffer);

		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		if (ItemEnergy.get(jetpack) != Config.jetpackBuffer) {
			helper.fail("a held jump on the ground must not burn EU");
		}
		if (player.fallDistance != 10.0) {
			helper.fail("the grounded gate must not touch the fall distance either");
		}
		helper.succeed();
	}

	/** NEG03: above the altitude ceiling the engine refuses to thrust — a flight that got there
	 * under power keeps its glide safety, but no EU is burned. */
	public static void neg03AboveCeilingRefusesThrust(GameTestHelper helper) {
		Player player = airborne(helper);
		ItemStack jetpack = jetpack(Config.jetpackBuffer);
		// One powered tick below the ceiling marks the flight, then the player crosses jetpackMaxY.
		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		long afterThrust = ItemEnergy.get(jetpack);
		player.setPos(player.getX(), Config.jetpackMaxY + 1.0, player.getZ());
		player.fallDistance = 10.0;

		if (JetpackItem.isPowered(jetpack, player)) {
			helper.fail("isPowered must be false above jetpackMaxY");
		}
		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		if (ItemEnergy.get(jetpack) != afterThrust) {
			helper.fail("above the ceiling the engine must burn nothing");
		}
		if (player.fallDistance != 0.0) {
			helper.fail("above the ceiling a powered flight still glides (fall distance reset)");
		}
		helper.succeed();
	}

	/** NEG04: a jetpack that never fired this flight gives NO glide — an empty backpack must fall
	 * exactly like no backpack (the playtest "levitation" bug). */
	public static void neg04EmptyJetpackFallsNormally(GameTestHelper helper) {
		Player player = airborne(helper);
		ItemStack jetpack = jetpack(0);

		JetpackItem.serverFlightTick(jetpack, helper.getLevel(), player, true);
		if (player.fallDistance != 10.0) {
			helper.fail("an engine that never fired this flight must not touch the fall distance, got "
					+ player.fallDistance);
		}
		helper.succeed();
	}

	/** FUN08: a thrusting jetpack casts a moving light block around the flyer; landing clears it, and
	 * the once-per-tick sweep clears any light a flight left behind (logout/death safety). */
	public static void fun08FlightGlowPlacedAndSwept(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().instabuild = false;
		player.setOnGround(true);
		JetpackItem.serverFlightTick(jetpack(0), level, player, false); // clear any shared-UUID state
		player.setOnGround(false);
		// The light goes to the block above the player's feet — force it to air so the placement never
		// depends on where the mock player spawned inside the test structure.
		net.minecraft.core.BlockPos lightPos = player.blockPosition().above();
		level.setBlock(lightPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
				net.minecraft.world.level.block.Block.UPDATE_CLIENTS);

		JetpackItem.serverFlightTick(jetpack(Config.jetpackBuffer), level, player, true);
		if (!level.getBlockState(lightPos).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
			helper.fail("a thrusting jetpack must place a light block at the flyer");
		}

		// Sweep with a later tick than the one the light was stamped with: an un-refreshed light (the
		// flight ended — logout/death/unequip) must be cleared. This is the sole leak-free cleanup path.
		// The stamp and the sweep both read server.getTickCount() (never null — a level's game time via
		// overworld() is null during server startup and crashed the tick loop, the bug this guards).
		JetpackLight.sweep(level.getServer(), level.getServer().getTickCount() + 1);
		if (level.getBlockState(lightPos).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
			helper.fail("the sweep must clear a light block whose flight tick did not refresh it");
		}

		// And landing clears it directly (the common path), never leaving a stray light.
		JetpackItem.serverFlightTick(jetpack(Config.jetpackBuffer), level, player, true); // re-light
		player.setOnGround(true);
		JetpackItem.serverFlightTick(jetpack(Config.jetpackBuffer), level, player, true); // land → extinguish
		if (level.getBlockState(lightPos).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
			helper.fail("landing must clear the flight glow");
		}
		helper.succeed();
	}

	private static ResourceKey<EquipmentAsset> assetOf(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable == null ? null : equippable.assetId().orElse(null);
	}
}
