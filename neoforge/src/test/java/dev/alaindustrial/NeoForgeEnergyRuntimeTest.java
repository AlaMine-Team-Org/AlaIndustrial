package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.core.energy.EnergyBuffer;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.energy.FaceEnergyPort;
import dev.alaindustrial.core.neoforge.BufferAsEnergyHandler;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-022 — NeoForge RUNTIME energy tests, headless.
 *
 * <p>Boots a real {@link MinecraftServer} (via {@link EphemeralTestServerProvider}) so mod registration
 * ran, {@code RegisterCapabilitiesEvent} fired, and the NeoForge transfer API is bootstrapped — then
 * exercises the loader-specific {@code EnergyHandler <-> EnergyPort} seam that MOD-022 shipped
 * compile/review-verified only, and that has <b>no Fabric equivalent</b>:
 *
 * <ul>
 *   <li>{@link BufferAsEnergyHandler} — the reverse adapter published through
 *       {@code Capabilities.Energy.BLOCK} — over a common {@link EnergyBuffer}, optionally face-role
 *       wrapped via {@link FaceEnergyPort} (the exact shape {@code MachineBlockEntity#energyPort}
 *       returns);</li>
 *   <li>a REAL {@code net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()}, so the
 *       {@code NeoForgeEnergyPort.wrap} / {@code NeoForgeTxn} snapshot-journal bridge and its
 *       commit/rollback are driven at runtime.</li>
 * </ul>
 *
 * <p>Every NeoForge/MC symbol here is verified against the decompiled 26.2.0.8-beta sources:
 * {@code EnergyHandler.getAmountAsLong()/getCapacityAsLong()/insert(int,TransactionContext)/
 * extract(int,TransactionContext)}, {@code Transaction.openRoot()/commit()/close()} (implements
 * {@code AutoCloseable, TransactionContext}), {@code BufferAsEnergyHandler.of(EnergyPort)}.
 *
 * <p><b>Why no in-world block placement.</b> {@code EphemeralTestServerProvider}'s server boots the
 * registry/datapack/capability context but never runs a ticking chunk loop (its
 * {@code JUnitServer.initServer()} skips {@code MinecraftServer#createLevels()}; calling it reflectively
 * fails in {@code setInitialSpawn} — "No chunk holder after ticket has been added"). World placement +
 * {@code DirectAdjacencyDistributor.distribute} + position-keyed {@code Capabilities.Energy.BLOCK} lookup belong to the
 * gametest server, not this passive JUnit server. These tests validate the adapter + transaction
 * machinery — the part MOD-022 could only compile-check.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class NeoForgeEnergyRuntimeTest {

	/** A buffer that both accepts and emits up to {@code rate} per op — the general symmetric case. */
	private static EnergyBuffer buffer(long capacity, long rate, long amount) {
		EnergyBuffer b = new EnergyBuffer(capacity, rate, rate, () -> {});
		b.amount = amount;
		return b;
	}

	/**
	 * Real BE placement stand-in (the original feasibility case): a fuel generator charges an adjacent
	 * battery box through the NeoForge capability adapter, inside a real committed transaction.
	 */
	@Test
	void generatorChargesConsumer(MinecraftServer server) {
		assertNotNull(server, "ephemeral MinecraftServer was not injected");

		BlockPos genPos = new BlockPos(0, 64, 0);
		BlockPos boxPos = genPos.east();

		Block generatorBlock = ModContent.GENERATOR.get();
		Block batteryBoxBlock = ModContent.BATTERY_BOX.get();

		BlockState genState = generatorBlock.defaultBlockState();
		// Box FACING=WEST -> its WEST face is the IN (charge) face (MOD-006).
		BlockState boxState =
				batteryBoxBlock.defaultBlockState().setValue(HorizontalMachineBlock.FACING, Direction.WEST);

		GeneratorBlockEntity gen = new GeneratorBlockEntity(genPos, genState);
		BatteryBoxBlockEntity box = new BatteryBoxBlockEntity(boxPos, boxState);

		gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		long seeded = gen.getEnergyStorage().getCapacity();
		gen.getEnergyStorage().amount = seeded;

		// Generator FACING defaults to NORTH and is energy-inert; SOUTH is an OUT face.
		EnergyHandler genHandler = BufferAsEnergyHandler.of(gen.energyPort(Direction.SOUTH));
		EnergyHandler boxHandler = BufferAsEnergyHandler.of(box.energyPort(Direction.WEST));
		assertNotNull(genHandler, "generator exposes no EnergyHandler on an OUT face");
		assertNotNull(boxHandler, "battery box exposes no EnergyHandler on its IN (WEST) face");
		assertEquals(0L, boxHandler.getAmountAsLong(), "fresh battery box should start empty");

		int lvCap = (int) EnergyTier.LV.maxVoltage();
		int moved;
		try (Transaction tx = Transaction.openRoot()) {
			int extracted = genHandler.extract(lvCap, tx);
			moved = boxHandler.insert(extracted, tx);
			tx.commit();
		}

		assertTrue(moved > 0, "no EU moved through the NeoForge adapter; moved=" + moved);
		assertEquals(moved, boxHandler.getAmountAsLong(),
				"box stored EU (read via NeoForge EnergyHandler) != committed transfer");
		assertEquals(seeded - moved, genHandler.getAmountAsLong(),
				"generator buffer (read via NeoForge EnergyHandler) did not drop by the committed transfer");
	}

	/**
	 * Case 1 — extract->insert round trip conserves EU across two adapted buffers, exercising BOTH
	 * adapter directions (extract side and insert side) inside one committed transaction.
	 */
	@Test
	void case1_roundTripConservesEu() {
		EnergyBuffer src = buffer(10_000, 32, 5_000);
		EnergyBuffer dst = buffer(10_000, 32, 0);
		EnergyHandler srcH = BufferAsEnergyHandler.of(src);
		EnergyHandler dstH = BufferAsEnergyHandler.of(dst);

		long totalBefore = srcH.getAmountAsLong() + dstH.getAmountAsLong();

		int moved;
		try (Transaction tx = Transaction.openRoot()) {
			int extracted = srcH.extract(32, tx);
			moved = dstH.insert(extracted, tx);
			// Anything the destination could not take must be returned to the source so EU is conserved.
			if (extracted > moved) {
				srcH.insert(extracted - moved, tx);
			}
			tx.commit();
		}

		assertEquals(32, moved, "LV-scale move should transfer the full 32 EU");
		assertEquals(totalBefore, srcH.getAmountAsLong() + dstH.getAmountAsLong(),
				"EU not conserved across the two adapted buffers");
		assertEquals(4_968L, srcH.getAmountAsLong(), "source did not drop by exactly the moved amount");
		assertEquals(32L, dstH.getAmountAsLong(), "destination did not gain exactly the moved amount");
	}

	/**
	 * Case 2 — long->int saturation. A buffer whose amount and capacity both exceed
	 * {@link Integer#MAX_VALUE} is driven through the int-based {@link EnergyHandler} API: the getters
	 * report the exact long, a single extract/insert saturates its int ANSWER at {@code Integer.MAX_VALUE}
	 * without corrupting the stored long, and a subsequent normal LV move stays byte-exact.
	 */
	@Test
	void case2_longToIntSaturationNoCorruption() {
		long huge = 5_000_000_000L;               // > Integer.MAX_VALUE (2_147_483_647)
		EnergyBuffer big = new EnergyBuffer(huge, huge, huge, () -> {}); // per-op limits also huge
		big.amount = huge;
		EnergyHandler h = BufferAsEnergyHandler.of(big);

		// Getters keep the true long (no int narrowing on read).
		assertEquals(huge, h.getAmountAsLong(), "getAmountAsLong must report the exact long");
		assertEquals(huge, h.getCapacityAsLong(), "getCapacityAsLong must report the exact long");

		// Ask for Integer.MAX_VALUE: the buffer can satisfy it; the int answer is exactly Integer.MAX_VALUE
		// (no overflow to a negative), and the stored long drops by exactly that much.
		int extracted;
		try (Transaction tx = Transaction.openRoot()) {
			extracted = h.extract(Integer.MAX_VALUE, tx);
			tx.commit();
		}
		assertEquals(Integer.MAX_VALUE, extracted, "extract answer must saturate at Integer.MAX_VALUE");
		assertTrue(extracted > 0, "saturated extract must not wrap to a negative int");
		assertEquals(huge - Integer.MAX_VALUE, h.getAmountAsLong(),
				"stored long corrupted after a saturated extract");

		// A normal LV-scale move on the same (still huge) buffer is byte-exact.
		long afterSaturate = h.getAmountAsLong();
		int moved;
		try (Transaction tx = Transaction.openRoot()) {
			moved = h.extract(32, tx);
			tx.commit();
		}
		assertEquals(32, moved, "normal LV extract must move exactly 32 EU");
		assertEquals(afterSaturate - 32, h.getAmountAsLong(), "LV move not byte-exact on a huge buffer");
	}

	/**
	 * Case 3 — role gating. A producer face (maxInsert==0 / OUT role) exposed via the adapter reports the
	 * correct {@link EnergyHandler} capability and its insert returns 0; a pure consumer face
	 * (maxExtract==0 / IN role) extract returns 0. Tested through BOTH gating mechanisms: the buffer's own
	 * per-op limits, and the {@link FaceEnergyPort} role wrapper the real registration path uses.
	 */
	@Test
	void case3_roleGating() {
		// Producer via buffer limits: maxInsert == 0.
		EnergyBuffer producer = new EnergyBuffer(10_000, 0, 32, () -> {});
		producer.amount = 5_000;
		EnergyHandler producerH = BufferAsEnergyHandler.of(producer);
		try (Transaction tx = Transaction.openRoot()) {
			assertEquals(0, producerH.insert(32, tx), "producer (maxInsert==0) must reject insertion");
			assertTrue(producerH.extract(32, tx) > 0, "producer must still emit");
			tx.commit();
		}

		// Consumer via buffer limits: maxExtract == 0.
		EnergyBuffer consumer = new EnergyBuffer(10_000, 32, 0, () -> {});
		consumer.amount = 5_000;
		EnergyHandler consumerH = BufferAsEnergyHandler.of(consumer);
		try (Transaction tx = Transaction.openRoot()) {
			assertEquals(0, consumerH.extract(32, tx), "consumer (maxExtract==0) must reject extraction");
			assertTrue(consumerH.insert(32, tx) > 0, "consumer must still accept");
			tx.commit();
		}

		// Same gating via the FaceEnergyPort role wrapper (the shape MachineBlockEntity#energyPort returns).
		EnergyBuffer symmetric = buffer(10_000, 32, 5_000);
		EnergyHandler outFace = BufferAsEnergyHandler.of(new FaceEnergyPort(symmetric, EnergyRole.OUT));
		EnergyHandler inFace = BufferAsEnergyHandler.of(new FaceEnergyPort(symmetric, EnergyRole.IN));
		try (Transaction tx = Transaction.openRoot()) {
			assertEquals(0, outFace.insert(32, tx), "OUT face must reject insertion");
			assertEquals(0, inFace.extract(32, tx), "IN face must reject extraction");
			tx.commit();
		}

		// A NONE face yields no handler at all (null == "no capability here").
		assertNull(BufferAsEnergyHandler.of(null), "of(null) must be null (no capability on an inert face)");
	}

	/**
	 * Case 4 — transaction semantics. An extract inside a {@code Transaction.openRoot()} that is closed
	 * WITHOUT commit leaves the buffer unchanged (the simulate / dry-run contract the network relies on);
	 * a committed transaction applies. This is the exact simulate-vs-commit contract driven through the
	 * NeoForge snapshot journal.
	 */
	@Test
	void case4_uncommittedRollsBackCommittedApplies() {
		EnergyBuffer b = buffer(10_000, 32, 5_000);
		EnergyHandler h = BufferAsEnergyHandler.of(b);

		// Aborted (no commit): try-with-resources closes the transaction, reverting the snapshot.
		try (Transaction tx = Transaction.openRoot()) {
			int extracted = h.extract(32, tx);
			assertEquals(32, extracted, "extract should report 32 inside the open transaction");
			assertEquals(4_968L, h.getAmountAsLong(), "mid-transaction amount should reflect the extract");
			// no tx.commit()
		}
		assertEquals(5_000L, h.getAmountAsLong(), "uncommitted transaction must roll the buffer back");

		// Committed: the same extract applies.
		try (Transaction tx = Transaction.openRoot()) {
			h.extract(32, tx);
			tx.commit();
		}
		assertEquals(4_968L, h.getAmountAsLong(), "committed transaction must apply the extract");
	}

	/**
	 * Case 5 — MOD-009 exact top-off through the NeoForge adapter. A buffer 1 EU short of capacity accepts
	 * exactly 1 via {@link EnergyHandler#insert}, reaches exact capacity, and rejects any further insert
	 * (returns 0) even though the port still "supports" insertion — the exact-capacity invariant.
	 */
	@Test
	void case5_exactTopOff() {
		long cap = 10_000;
		EnergyBuffer b = new EnergyBuffer(cap, 32, 32, () -> {});
		b.amount = cap - 1; // one EU short
		EnergyHandler h = BufferAsEnergyHandler.of(b);

		int inserted;
		try (Transaction tx = Transaction.openRoot()) {
			inserted = h.insert(32, tx); // offer a full LV packet; only 1 EU of room remains
			tx.commit();
		}
		assertEquals(1, inserted, "top-off must accept exactly the 1 EU of remaining room");
		assertEquals(cap, h.getAmountAsLong(), "buffer must reach exact capacity, never overfill");

		// Full buffer: a further insert returns 0 (rate-capped floor), but the port is still a valid handler.
		assertFalse(BufferAsEnergyHandler.of(b) == null, "adapter must still expose the full buffer");
		int overfill;
		try (Transaction tx = Transaction.openRoot()) {
			overfill = h.insert(32, tx);
			tx.commit();
		}
		assertEquals(0, overfill, "a full buffer must accept 0 more EU");
		assertEquals(cap, h.getAmountAsLong(), "capacity must be unchanged after a rejected overfill");
	}
}
