package dev.alaindustrial.gametest;

import dev.alaindustrial.block.TeleporterBlock;
import dev.alaindustrial.block.entity.TeleporterBlockEntity;
import dev.alaindustrial.core.energy.EnergyTransactions;
import dev.alaindustrial.item.teleport.TeleportPoint;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.teleporter.TeleportEngine;
import dev.alaindustrial.teleporter.TeleporterRegistry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * L2 suite for the teleporter station registry (MOD-628): every change the remote's screen shows reaches
 * the registry, a removed station leaves it, and charging is throttled.
 *
 * <p>Each case checks the registry entry at the station's absolute position; the registry is server-global,
 * so parallel tests never share a key.
 */
public final class TeleporterRegistryScenarios {

	private TeleporterRegistryScenarios() {}

	private static final BlockPos STATION = new BlockPos(1, 2, 1);

	private static TeleporterBlockEntity place(GameTestHelper helper) {
		return AlaGameTestHelper.place(helper, STATION, ModContent.TELEPORTER.get(), TeleporterBlockEntity.class);
	}

	private static Optional<TeleporterRegistry.Entry> entry(GameTestHelper helper) {
		return TeleporterRegistry.get(helper.getLevel().getServer())
				.find(helper.getLevel().dimension(), helper.absolutePos(STATION));
	}

	/** The entry, or a failed test naming what was expected of it. */
	private static TeleporterRegistry.Entry require(GameTestHelper helper, String when) {
		Optional<TeleporterRegistry.Entry> entry = entry(helper);
		if (entry.isEmpty()) {
			helper.fail("no registry entry " + when);
			throw new IllegalStateException("unreachable: helper.fail throws");
		}
		return entry.get();
	}

	/** A committed insert of one EU: the network's own path into the buffer, and so the energy hook's. */
	private static void deliverOneEu(TeleporterBlockEntity station) {
		EnergyTransactions.get().runCommitting(txn -> station.getEnergyStorage().insert(1, txn));
	}

	/**
	 * @implements TC-TELE-006-FUN01 — a station placed in the world is recorded at once: private, with no
	 *     owner, no chip, no capsule and no charge.
	 */
	public static void tcTele006Fun01_placedStationIsRecorded(GameTestHelper helper) {
		place(helper);
		TeleporterRegistry.Entry entry = require(helper, "for a freshly placed station");
		if (!entry.isPrivate() || entry.owner().isPresent() || entry.hasChip() || entry.formed() || entry.energy() != 0) {
			helper.fail("a fresh station must be recorded private, unowned, without chip, capsule or charge: " + entry);
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-006-FUN02 — assembling the capsule marks the entry formed, and losing a capsule cell
	 *     clears it again. The second half runs through {@code TeleporterBlock#updateShape}, which cannot write
	 *     to the registry itself.
	 */
	public static void tcTele006Fun02_capsuleIsRecorded(GameTestHelper helper) {
		place(helper);
		helper.setBlock(STATION.above(), Blocks.GLASS);
		helper.setBlock(STATION.above(2), Blocks.GLASS);
		TeleporterBlock.tryAssemble(helper.getLevel(), helper.absolutePos(STATION));
		if (!TeleporterBlock.isFormed(helper.getLevel().getBlockState(helper.absolutePos(STATION)))) {
			helper.fail("control: the capsule did not assemble, so this case proves nothing");
			return;
		}
		if (!require(helper, "after assembly").formed()) {
			helper.fail("the registry did not hear that the capsule assembled");
			return;
		}
		helper.setBlock(STATION.above(), Blocks.AIR);
		if (TeleporterBlock.isFormed(helper.getLevel().getBlockState(helper.absolutePos(STATION)))) {
			helper.fail("control: removing the middle cell did not take the capsule apart");
			return;
		}
		if (require(helper, "after disassembly").formed()) {
			helper.fail("the registry still says formed after the capsule came apart");
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-006-FUN03 — the owner set on placement, the privacy switch and the fitted chip each
	 *     reach the entry.
	 */
	public static void tcTele006Fun03_ownerPrivacyAndChipAreRecorded(GameTestHelper helper) {
		TeleporterBlockEntity station = place(helper);
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		BlockPos pos = helper.absolutePos(STATION);
		ModContent.TELEPORTER.get().setPlacedBy(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), player,
				ItemStack.EMPTY);
		if (!require(helper, "after setPlacedBy").owner().equals(Optional.of(player.getUUID()))) {
			helper.fail("the placer is not the recorded owner: " + entry(helper));
			return;
		}
		station.setPrivate(false);
		if (require(helper, "after making it public").isPrivate()) {
			helper.fail("the registry still says private after the station was made public");
			return;
		}
		station.setRtpModule(true);
		if (!require(helper, "after fitting the chip").hasChip()) {
			helper.fail("the registry did not hear about the fitted chip");
		}
		helper.succeed();
	}

	/**
	 * The energy hook sits on the network's hot path, so it records at most once per 100 ticks and only when
	 * the charge moved by at least 1 % of the buffer. Each half of that rule is asserted where it would fail
	 * on its own: an unthrottled hook records the first delivery, a hook without the step records the last.
	 *
	 * @implements TC-TELE-006-NRG01 — charging reaches the registry no more than once per 100 ticks, and only
	 *     after a change of at least 1 % of the buffer.
	 */
	public static void tcTele006Nrg01_chargingIsThrottled(GameTestHelper helper) {
		TeleporterBlockEntity station = place(helper);
		long step = station.getEnergyStorage().getCapacity() / 100;
		station.getEnergyStorage().setAmountUntracked(step * 4);
		deliverOneEu(station);
		if (require(helper, "right after placement").energy() != 0) {
			helper.fail("a delivery in the placement tick was recorded — the interval is not applied");
			return;
		}
		helper.runAfterDelay(101, () -> {
			deliverOneEu(station);
			long recorded = require(helper, "after the interval").energy();
			if (recorded != step * 4 + 2) {
				helper.fail("a 4 % change after the interval must be recorded, entry says " + recorded);
				return;
			}
			helper.runAfterDelay(101, () -> {
				deliverOneEu(station);
				long unchanged = require(helper, "after a small change").energy();
				if (unchanged != step * 4 + 2) {
					helper.fail("a 1 EU change was recorded (" + unchanged + ") — the 1 % step is not applied");
					return;
				}
				helper.succeed();
			});
		});
	}

	/**
	 * A jump drains the station internally, which fires no energy commit, so {@code TeleportEngine} records the
	 * spend itself. Without that the entry would still show the charge from before the jump.
	 *
	 * @implements TC-TELE-006-NRG02 — a successful jump records the station's charge after the spend.
	 */
	public static void tcTele006Nrg02_jumpSpendIsRecorded(GameTestHelper helper) {
		TeleporterBlockEntity station = place(helper);
		helper.setBlock(STATION.above(), Blocks.GLASS);
		helper.setBlock(STATION.above(2), Blocks.GLASS);
		TeleporterBlock.tryAssemble(helper.getLevel(), helper.absolutePos(STATION));
		station.setPrivate(false);
		station.getEnergyStorage().setAmountUntracked(station.getEnergyStorage().getCapacity());

		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);
		BlockPos near = helper.absolutePos(STATION.offset(3, 0, 0));
		player.snapTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
		player.getInventory().clearContent();
		TeleportPoint point = new TeleportPoint(helper.getLevel().dimension(), helper.absolutePos(STATION), "home");
		long cost = TeleportEngine.computeCost(player, point);
		if (!TeleportEngine.execute(player, point, cost)) {
			helper.fail("control: the jump was refused, so the spend was never made");
			return;
		}
		long expected = station.getEnergyStorage().getCapacity() - cost;
		long recorded = require(helper, "after the jump").energy();
		if (recorded != expected) {
			helper.fail("after the jump the registry shows " + recorded + " EU, the station holds " + expected);
		}
		helper.succeed();
	}

	/**
	 * {@code LevelChunk.setBlockEntity} calls {@code clearRemoved} both on placement and when a chunk loads a
	 * saved station — the way a station from before the registry gets its first entry. The chunk load itself is
	 * checked by hand on an existing world; this pins the hook.
	 *
	 * @implements TC-TELE-006-FUN04 — a station entering the world records itself even when the registry had no
	 *     entry for it.
	 */
	public static void tcTele006Fun04_enteringTheWorldRecordsItself(GameTestHelper helper) {
		TeleporterBlockEntity station = place(helper);
		TeleporterRegistry.forget(helper.getLevel(), helper.absolutePos(STATION));
		if (entry(helper).isPresent()) {
			helper.fail("control: forget left the entry in place");
			return;
		}
		station.clearRemoved();
		require(helper, "after the station re-entered the world");
		helper.succeed();
	}

	/**
	 * The server hides someone else's private station: the snapshot says only that it is private. The owner's snapshot
	 * of the same station is the control — without it an empty snapshot would pass too.
	 *
	 * @implements TC-TELE-006-SEC01 — for a viewer who does not own a private station the snapshot carries no charge,
	 *     no price, no chip and no capsule; for its owner it carries all of them.
	 */
	public static void tcTele006Sec01_foreignPrivateStationIsHidden(GameTestHelper helper) {
		TeleporterBlockEntity station = place(helper);
		helper.setBlock(STATION.above(), Blocks.GLASS);
		helper.setBlock(STATION.above(2), Blocks.GLASS);
		TeleporterBlock.tryAssemble(helper.getLevel(), helper.absolutePos(STATION));
		station.setRtpModule(true);
		station.getEnergyStorage().setAmountUntracked(station.getEnergyStorage().getCapacity());

		ServerPlayer viewer = AlaGameTestHelper.mockPlayerInLevel(helper);
		BlockPos near = helper.absolutePos(STATION.offset(3, 0, 0));
		viewer.snapTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
		dev.alaindustrial.item.teleport.TeleportPoints points = dev.alaindustrial.item.teleport.TeleportPoints.EMPTY
				.with(new TeleportPoint(helper.getLevel().dimension(), helper.absolutePos(STATION), "home"));

		station.setOwner(java.util.UUID.fromString("00000000-0000-0000-0000-0000000000d4"), "Alex");
		dev.alaindustrial.network.TeleportStationsPayload.Station hidden =
				dev.alaindustrial.teleporter.TeleportStationSnapshot.build(viewer, points, 1).stations().get(0);
		if (!hidden.has(dev.alaindustrial.network.TeleportStationsPayload.HIDDEN)
				|| !hidden.has(dev.alaindustrial.network.TeleportStationsPayload.PRIVATE)
				|| hidden.has(dev.alaindustrial.network.TeleportStationsPayload.FORMED)
				|| hidden.has(dev.alaindustrial.network.TeleportStationsPayload.CHIP)
				|| hidden.energy() != 0 || hidden.cost() != 0
				|| hidden.denial() != TeleportEngine.Denial.NO_ACCESS) {
			helper.fail("someone else's private station leaked into the snapshot: " + hidden);
			return;
		}

		station.setOwner(viewer.getUUID(), "viewer");
		dev.alaindustrial.network.TeleportStationsPayload.Station own =
				dev.alaindustrial.teleporter.TeleportStationSnapshot.build(viewer, points, 1).stations().get(0);
		if (own.has(dev.alaindustrial.network.TeleportStationsPayload.HIDDEN)
				|| !own.has(dev.alaindustrial.network.TeleportStationsPayload.FORMED)
				|| !own.has(dev.alaindustrial.network.TeleportStationsPayload.CHIP)
				|| own.energy() != station.getEnergyStorage().getCapacity() || own.cost() <= 0) {
			helper.fail("control: the owner's snapshot of the same station is incomplete: " + own);
			return;
		}
		helper.succeed();
	}

	/**
	 * The remote's screen must never load a station's chunk. The station here is far enough away that its chunk is
	 * not loaded — asserted first, or the case would prove nothing.
	 *
	 * @implements TC-TELE-006-FUN05 — building a snapshot for a far, unrecorded station loads no chunk and does not
	 *     claim to know the station.
	 */
	public static void tcTele006Fun05_snapshotLoadsNoChunk(GameTestHelper helper) {
		ServerPlayer viewer = AlaGameTestHelper.mockPlayerInLevel(helper);
		BlockPos far = helper.absolutePos(STATION).offset(20_000, 0, 20_000);
		if (helper.getLevel().isLoaded(far)) {
			helper.fail("control: the far chunk is already loaded, so this case proves nothing");
			return;
		}
		dev.alaindustrial.item.teleport.TeleportPoints points = dev.alaindustrial.item.teleport.TeleportPoints.EMPTY
				.with(new TeleportPoint(helper.getLevel().dimension(), far, "far"));
		dev.alaindustrial.network.TeleportStationsPayload.Station station =
				dev.alaindustrial.teleporter.TeleportStationSnapshot.build(viewer, points, 1).stations().get(0);
		if (helper.getLevel().isLoaded(far)) {
			helper.fail("building the snapshot loaded the station's chunk");
			return;
		}
		if (station.has(dev.alaindustrial.network.TeleportStationsPayload.KNOWN)) {
			helper.fail("an unrecorded station in an unloaded chunk must be unknown: " + station);
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements TC-TELE-006-BRK01 — a removed station leaves the registry.
	 */
	public static void tcTele006Brk01_removedStationIsForgotten(GameTestHelper helper) {
		place(helper);
		require(helper, "before removal");
		helper.setBlock(STATION, Blocks.AIR);
		if (entry(helper).isPresent()) {
			helper.fail("the registry still lists a station that was removed");
		}
		helper.succeed();
	}
}
