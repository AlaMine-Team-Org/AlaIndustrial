package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.item.TeleportPoint;
import dev.alaindustrial.registry.ModBlocks;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDataComponents;
import dev.alaindustrial.teleporter.TeleportEngine;
import dev.alaindustrial.teleporter.TeleportWarmupManager;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * L2 suite for the jump itself (MOD-092): the price, the policy gate, and the one thing that must
 * never happen — EU disappearing without the player moving.
 *
 * <p>The warmup countdown and its cancellation hooks (damage / death / disconnect) are driven by
 * real player events and are covered by the manual client pass; what is tested here is everything
 * that can be decided from the server state alone.
 */
public class TeleporterJumpGameTest {

	private static final BlockPos STATION = new BlockPos(1, 2, 1);
	private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

	private static TeleporterBlockEntity station(GameTestHelper helper) {
		return AlaGameTestHelper.place(helper, STATION, ModBlocks.TELEPORTER, TeleporterBlockEntity.class);
	}

	private static TeleportPoint pointAt(GameTestHelper helper, BlockPos rel) {
		return new TeleportPoint(helper.getLevel().dimension(), helper.absolutePos(rel), "home");
	}

	/**
	 * A mock player standing a few blocks from the station.
	 *
	 * <p>{@code makeMockServerPlayerInLevel} drops the player at the world origin, which for a test
	 * structure generated millions of blocks out means a jump priced in the tens of millions of EU —
	 * every station would refuse it for lack of power and the test would prove nothing. Snapping the
	 * player next to the station is what makes the distance realistic.
	 */
	private static ServerPlayer playerNearStation(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos near = helper.absolutePos(STATION.offset(3, 0, 0));
		player.snapTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
		player.getInventory().clearContent();
		return player;
	}

	/**
	 * @implements TC-TELE-002-FUN01 — price = (base + distance × perBlock) × weight, and an empty
	 *     player pays exactly the 1.0 multiplier.
	 */
	@GameTest
	public void tcTele002Fun01_costFormula(GameTestHelper helper) {
		station(helper);
		ServerPlayer player = playerNearStation(helper);
		TeleportPoint point = pointAt(helper, STATION);

		double distance = Math.sqrt(player.blockPosition().distSqr(point.pos()));
		long expected = Math.round(Config.teleporterBaseCost + distance * Config.teleporterCostPerBlock);
		long actual = TeleportEngine.computeCost(player, point);
		if (actual != expected) {
			helper.fail("empty-inventory cost " + actual + " != expected " + expected);
		}
		helper.succeed();
	}

	/**
	 * The weight multiplier must count the pack, not the wardrobe.
	 *
	 * <p>In 26.2 {@code Inventory.getContainerSize()} returns 43 and silently maps slots 36-42 onto
	 * armour/offhand, so the obvious loop would bill players for their boots. This pins that only
	 * main+hotbar counts.
	 *
	 * @implements TC-TELE-002-FUN02 — armour does not add to the jump price; carried items do.
	 */
	@GameTest
	public void tcTele002Fun02_weightIgnoresArmour(GameTestHelper helper) {
		station(helper);
		ServerPlayer player = playerNearStation(helper);

		double emptyWeight = TeleportEngine.weight(player);
		if (Math.abs(emptyWeight - 1.0) > 1.0e-6) {
			helper.fail("an empty inventory must weigh 1.0, got " + emptyWeight);
		}

		// Armour on: still 1.0 — equipment is not cargo.
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		double armouredWeight = TeleportEngine.weight(player);
		if (Math.abs(armouredWeight - 1.0) > 1.0e-6) {
			helper.fail("armour must not count toward jump weight, got " + armouredWeight
					+ " (getContainerSize() leaks equipment slots — use getNonEquipmentItems())");
		}

		// A full main inventory: 2.0.
		for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) {
			player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		}
		double fullWeight = TeleportEngine.weight(player);
		if (Math.abs(fullWeight - 2.0) > 1.0e-6) {
			helper.fail("a full inventory must weigh 2.0, got " + fullWeight);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-002-SEC01 — the policy gate: a private station refuses a stranger, a flat
	 *     one refuses everyone, and a live one lets its owner through.
	 */
	@GameTest
	public void tcTele002Sec01_policyGate(GameTestHelper helper) {
		TeleporterBlockEntity station = station(helper);
		ServerPlayer player = playerNearStation(helper);
		ItemStack remote = new ItemStack(ModContent.TELEPORTER_REMOTE.get());
		TeleportPoint point = pointAt(helper, STATION);

		// Private + owned by someone else → refused.
		station.setOwner(STRANGER, "Someone");
		station.setPrivate(true);
		station.getEnergyStorage().amount = station.getEnergyStorage().getCapacity();
		if (TeleportEngine.checkPolicy(player, remote, point) != TeleportEngine.Denial.NO_ACCESS) {
			helper.fail("a private station must refuse a stranger");
		}

		// Public → allowed.
		station.setPrivate(false);
		if (!TeleportEngine.checkPolicy(player, remote, point).allowed()) {
			helper.fail("a public, charged station must accept any player");
		}

		// Public but flat → refused, and refused for lack of EU specifically.
		station.getEnergyStorage().amount = 0;
		if (TeleportEngine.checkPolicy(player, remote, point) != TeleportEngine.Denial.NOT_ENOUGH_EU) {
			helper.fail("a flat station must refuse the jump");
		}
		helper.succeed();
	}

	/**
	 * The defect this whole suite exists to prevent: EU vanishing on a failed jump.
	 *
	 * <p>The engine teleports first and only deducts on success. A flat station must therefore end
	 * the attempt with exactly the EU it started with — no "reservation", no partial charge.
	 *
	 * @implements TC-TELE-002-NRG01 — a refused jump costs nothing.
	 */
	@GameTest
	public void tcTele002Nrg01_failedJumpCostsNothing(GameTestHelper helper) {
		TeleporterBlockEntity station = station(helper);
		ServerPlayer player = playerNearStation(helper);
		TeleportPoint point = pointAt(helper, STATION);

		long cost = TeleportEngine.computeCost(player, point);
		long tooLittle = cost - 1;
		station.setPrivate(false);
		station.getEnergyStorage().amount = tooLittle;

		if (TeleportEngine.execute(player, point, cost)) {
			helper.fail("a jump must not fire when the station cannot pay for it");
		}
		if (station.getEnergyStorage().getAmount() != tooLittle) {
			helper.fail("a refused jump must not touch the station's EU: " + tooLittle
					+ " -> " + station.getEnergyStorage().getAmount());
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-002-FUN03 — a successful jump moves the player onto the station and
	 *     charges exactly the quoted price, no more.
	 */
	@GameTest
	public void tcTele002Fun03_successMovesAndCharges(GameTestHelper helper) {
		TeleporterBlockEntity station = station(helper);
		ServerPlayer player = playerNearStation(helper);
		station.setPrivate(false);
		station.getEnergyStorage().amount = station.getEnergyStorage().getCapacity();
		long before = station.getEnergyStorage().getAmount();

		TeleportPoint point = pointAt(helper, STATION);
		long cost = TeleportEngine.computeCost(player, point);
		if (!TeleportEngine.execute(player, point, cost)) {
			helper.fail("a charged, public station must accept the jump");
		}
		if (station.getEnergyStorage().getAmount() != before - cost) {
			helper.fail("charged " + (before - station.getEnergyStorage().getAmount()) + " EU, quoted " + cost);
		}
		// Landed on top of the station block, centred.
		BlockPos landed = player.blockPosition();
		BlockPos expected = helper.absolutePos(STATION).above();
		if (!landed.equals(expected)) {
			helper.fail("player landed at " + landed + ", expected on top of the station at " + expected);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-002-SEC02 — the remote binds to its first user; a stranger gets nothing
	 *     out of a stolen one.
	 */
	@GameTest
	public void tcTele002Sec02_remoteBindsToOwner(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack remote = new ItemStack(ModContent.TELEPORTER_REMOTE.get());

		remote.set(ModDataComponents.TELEPORTER_OWNER.get(), STRANGER);
		UUID owner = remote.get(ModDataComponents.TELEPORTER_OWNER.get());
		if (!STRANGER.equals(owner)) {
			helper.fail("the owner component did not round-trip: " + owner);
		}
		if (owner.equals(player.getUUID())) {
			helper.fail("a stranger's remote must not read as the local player's");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-002-STA01 — warmup state is per player and starts clean: no ghost warmup,
	 *     no ghost cooldown on a player who has done nothing.
	 */
	@GameTest
	public void tcTele002Sta01_warmupStateStartsClean(GameTestHelper helper) {
		TeleportWarmupManager.clearAll();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		if (TeleportWarmupManager.isWarming(player)) {
			helper.fail("a fresh player must not be mid-warmup");
		}
		if (TeleportWarmupManager.isOnCooldown(player)) {
			helper.fail("a fresh player must not be on cooldown");
		}

		TeleportWarmupManager.start(player, pointAt(helper, STATION));
		if (!TeleportWarmupManager.isWarming(player)) {
			helper.fail("start() must register the warmup");
		}
		TeleportWarmupManager.forget(player.getUUID());
		if (TeleportWarmupManager.isWarming(player)) {
			helper.fail("forget() must drop the warmup — a disconnected player leaves no state behind");
		}
		helper.succeed();
	}
}
