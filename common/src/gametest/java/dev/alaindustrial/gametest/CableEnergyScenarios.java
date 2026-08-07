package dev.alaindustrial.gametest;

import dev.alaindustrial.Config;
import dev.alaindustrial.block.CableBlock;
import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.BatteryBoxBlockEntity;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MaceratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.EnergyShare;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.LINE_CABLE;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.LINE_GEN;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.LINE_MAC;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.driveLine;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.tick;

/**
 * Loader-neutral world-based energy gametest bodies (MOD-022) — cable transport and network
 * topology: segment buffers (MOD-070), per-grade throughput and mixed networks (MOD-219),
 * distance loss (MOD-021), ring merge (MOD-025), split/rejoin, packet caps and endpoint
 * negatives. Suite contract and shared helpers: {@link EnergyScenarioSupport}.
 */
public final class CableEnergyScenarios {

	private CableEnergyScenarios() {}

	// ── scenario 2: generator → cable → macerator (network transport) ─────────────────────────────

	/**
	 * Generator delivers EU down a copper cable to a macerator, and an energy network forms.
	 * Mirrors: NetworkGameTest.it001_delivery
	 *
	 * <p>Written with the {@link EnergyLine} builder (MOD-309), which also buys the assertion this
	 * test was missing: it used to check only that the macerator ended up with EU, which stays green
	 * even when the consumer is charged off-network with every cable left at 0 — the MOD-070 failure
	 * mode. {@code assertFlowedThroughCable()} samples the wire itself on every driven tick.
	 */
	public static void generatorDeliversDownCable(GameTestHelper helper) {
		EnergyLine line = EnergyLine.in(helper)
				.fueledGenerator()
				.cables(1)
				.consumer(ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 8))
				.build()
				.drive(120)
				.assertConsumerCharged()
				.assertFlowedThroughCable();

		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(),
				helper.absolutePos(line.cablePos(0)));
		if (net == null) {
			helper.fail("no energy network formed on the cable");
		}
		helper.succeed();
	}

	/**
	 * returnRoundRobin no-leak: a cabled generator whose only consumer accepts less than a full LV
	 * packet (5 EU room, well below the 32 EU cap) must not destroy any EU into the void. With the
	 * MOD-070 line buffers, conservation is system-wide: gen_drain == mac_gain + cable_buffered + loss
	 * (loss floors to 0 for a 5 EU flow over this short line).
	 * Mirrors: NetworkGameTest.tcCable001Nrg03_generatorNotDrainedByPartialConsumer
	 */
	public static void returnRoundRobinNoLeak(GameTestHelper helper) {
		helper.setBlock(LINE_GEN, ModContent.GENERATOR.get());
		helper.setBlock(LINE_CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(LINE_MAC, ModContent.MACERATOR.get());

		long genStart = Config.generatorBuffer;
		if (be(helper, LINE_GEN) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = genStart; // pre-charged, NO fuel: buffer only goes down
			gen.setChanged();
		}
		final long room = 5;
		long macStart = Config.maceratorBuffer - room;
		if (be(helper, LINE_MAC) instanceof MaceratorBlockEntity mac) {
			mac.getEnergyStorage().amount = macStart; // NO input: never processes, room only shrinks
			mac.setChanged();
		}

		driveLine(helper, 10);

		long genEnd = be(helper, LINE_GEN) instanceof GeneratorBlockEntity g ? g.getEnergyStorage().getAmount() : -1;
		long macEnd = be(helper, LINE_MAC) instanceof MaceratorBlockEntity m ? m.getEnergyStorage().getAmount() : -1;
		if (macEnd != Config.maceratorBuffer) {
			helper.fail("macerator did not top off its 5 EU of room: " + macEnd + "/" + Config.maceratorBuffer);
		}
		long genDrain = genStart - genEnd;
		long macGain = macEnd - macStart;
		// MOD-070: the generator also fills the cable line buffer; conservation is system-wide.
		long cableBuffered = be(helper, LINE_CABLE) instanceof CableBlockEntity c ? c.getEnergyStorage().getAmount() : 0;
		if (genDrain != macGain + cableBuffered) {
			helper.fail("generator lost " + genDrain + " EU but macerator gained " + macGain
					+ " and cables buffered " + cableBuffered
					+ " — surplus destroyed in returnRoundRobin (EnergyMover-class leak on the cable path)");
		}
		helper.succeed();
	}

	// ── scenario 2b: MOD-070 segment-to-segment flow — cables carry a live buffer, not a teleport ──

	private static final BlockPos FLOW_GEN = new BlockPos(1, 2, 1);
	private static final BlockPos[] FLOW_CABLES = {
		new BlockPos(2, 2, 1), new BlockPos(3, 2, 1), new BlockPos(4, 2, 1),
		new BlockPos(5, 2, 1), new BlockPos(6, 2, 1),
	};
	private static final BlockPos FLOW_MAC = new BlockPos(7, 2, 1);

	private static void buildFlowLine(GameTestHelper helper) {
		buildFlowLine(helper, ModContent.COPPER_CABLE.get());
	}

	private static void buildFlowLine(GameTestHelper helper, net.minecraft.world.level.block.Block cable) {
		helper.setBlock(FLOW_GEN, ModContent.GENERATOR.get());
		for (BlockPos c : FLOW_CABLES) {
			helper.setBlock(c, cable);
		}
		helper.setBlock(FLOW_MAC, ModContent.MACERATOR.get());
		if (be(helper, FLOW_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, FLOW_MAC) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
		}
	}

	private static void driveFlow(GameTestHelper helper, int n) {
		for (int i = 0; i < n; i++) {
			tick(helper, be(helper, FLOW_GEN));
			for (BlockPos c : FLOW_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, FLOW_MAC));
		}
	}

	private static long cableAmount(GameTestHelper helper, BlockPos pos) {
		return be(helper, pos) instanceof CableBlockEntity c ? c.getEnergyStorage().getAmount() : -1;
	}

	/**
	 * MOD-070 accumulation: energy flows through the cable buffers segment-to-segment (it does not
	 * teleport). The intermediate cable holds real EU (>0 mid-line — impossible under the old dead
	 * buffer), delivery still works, and no cable exceeds the tiny {@code cableBuffer} cap.
	 * Mirrors: NetworkGameTest.tcCable001Nrg04_lineAccumulatesInSegments
	 */
	public static void lineAccumulatesInSegments(GameTestHelper helper) {
		buildFlowLine(helper);
		driveFlow(helper, 40);

		long mac = be(helper, FLOW_MAC) instanceof MaceratorBlockEntity m ? m.getEnergyStorage().getAmount() : -1;
		if (mac <= 0) {
			helper.fail("macerator received no EU over the cable line");
		}
		long mid = cableAmount(helper, FLOW_CABLES[2]);
		if (mid <= 0) {
			helper.fail("intermediate cable holds no EU — energy teleported instead of flowing through the line");
		}
		for (BlockPos c : FLOW_CABLES) {
			long a = cableAmount(helper, c);
			if (a > Config.cableBuffer) {
				helper.fail("cable " + c + " holds " + a + " EU > cableBuffer=" + Config.cableBuffer
						+ " — per-segment ceiling breached (battery from wires)");
			}
		}
		helper.succeed();
	}

	/**
	 * MOD-219 per-grade throughput: a gold line genuinely carries more per tick than a copper one, and a
	 * tin line carries less. The cable's segment buffer IS its throughput (MOD-070), so this asserts the
	 * live buffered EU mid-line — the thing a player feels as "thicker wire" — rather than the packet cap,
	 * which would have stayed a paper number.
	 *
	 * <p>Regression guard for the "recoloured copper" bug class: before per-cable parameters, all three
	 * grades were built with {@code Config.cableBuffer}, so every line here would settle at the same 12 EU
	 * and the two inequalities below would both fail.
	 */
	public static void cableGradesCarryTheirOwnBuffer(GameTestHelper helper) {
		long electrum = fillAndReadMidCable(helper, ModContent.ELECTRUM_CABLE.get());
		long gold = fillAndReadMidCable(helper, ModContent.GOLD_CABLE.get());
		long copper = fillAndReadMidCable(helper, ModContent.COPPER_CABLE.get());
		long tin = fillAndReadMidCable(helper, ModContent.TIN_CABLE.get());

		if (electrum <= gold) {
			helper.fail("electrum cable buffered " + electrum + " EU vs gold's " + gold
					+ " — the HV cable must carry strictly more, otherwise the top rung is fiction");
		}
		if (electrum != CableType.ELECTRUM.segmentBuffer()) {
			helper.fail("electrum line settled at " + electrum + " EU instead of its full "
					+ CableType.ELECTRUM.segmentBuffer() + " — the run did not saturate, so this "
					+ "comparison measures the drive length rather than the wire");
		}

		if (gold <= copper) {
			helper.fail("gold cable buffered " + gold + " EU vs copper's " + copper
					+ " — the MV cable must carry strictly more, otherwise its niche is fiction");
		}
		if (tin >= copper) {
			helper.fail("tin cable buffered " + tin + " EU vs copper's " + copper
					+ " — tin is meant to be the narrow grade");
		}
		// Each grade must also respect its own per-segment ceiling, not some shared one.
		if (electrum > CableType.ELECTRUM.segmentBuffer() || gold > CableType.GOLD.segmentBuffer()
				|| copper > CableType.COPPER.segmentBuffer() || tin > CableType.TIN.segmentBuffer()) {
			helper.fail("a cable exceeded its grade's segment buffer (electrum=" + electrum + "/"
					+ CableType.ELECTRUM.segmentBuffer() + ", gold=" + gold + "/"
					+ CableType.GOLD.segmentBuffer() + ", copper=" + copper + "/"
					+ CableType.COPPER.segmentBuffer() + ", tin=" + tin + "/"
					+ CableType.TIN.segmentBuffer() + ")");
		}
		helper.succeed();
	}

	/**
	 * Build a generator + cable line out of {@code cable} with NO consumer, run it to saturation, and read
	 * the middle segment.
	 *
	 * <p>The missing consumer is the point: a generator fills the wires to capacity even with nothing
	 * downstream, so the segment settles at the grade's own buffer. With a macerator on the end every
	 * grade instead settles at the generator's 8 EU/t output — measuring the source, not the wire.
	 */
	private static long fillAndReadMidCable(GameTestHelper helper, net.minecraft.world.level.block.Block cable) {
		helper.setBlock(FLOW_GEN, ModContent.GENERATOR.get());
		for (BlockPos c : FLOW_CABLES) {
			helper.setBlock(c, cable);
		}
		if (be(helper, FLOW_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		// The drive length is DERIVED from the grade, not a constant. A fixed 120 ticks was enough while
		// gold (5 × 48 = 240 EU at 8 EU/t = 30 ticks) was the widest grade, but electrum needs 5 × 192 =
		// 960 EU = 120 ticks of generation before a single tick of travel — the run would have stopped
		// exactly at saturation and this helper would have silently measured the drive length instead of
		// the wire. Doubling the fill time and adding a flat margin covers the segment-to-segment walk
		// (~1 hop/tick) and keeps the helper correct through any future rebalance of the ladder.
		long fillEu = (long) FLOW_CABLES.length * CableBlock.typeOf(cable.defaultBlockState()).segmentBuffer();
		int ticks = (int) (2 * fillEu / Math.max(1, Config.fuelEuPerTick)) + 120;
		driveFlow(helper, ticks);
		long mid = cableAmount(helper, FLOW_CABLES[2]);
		// Tear the line down so the next grade starts from a clean network rather than inheriting this
		// one's cables (the topology cache keys off the block entities that are actually present).
		helper.setBlock(FLOW_GEN, Blocks.AIR);
		for (BlockPos c : FLOW_CABLES) {
			helper.setBlock(c, Blocks.AIR);
		}
		helper.setBlock(FLOW_MAC, Blocks.AIR);
		NetworkManager.tickAll(helper.getLevel());
		return mid;
	}

	/**
	 * MOD-070 break retains at source: after the line fills, removing a middle cable splits the network;
	 * the source-side half sleeps but keeps the EU buffered in its cables (>0 after the break).
	 * Mirrors: NetworkGameTest.tcCable001Nrg05_breakRetainsAtSource
	 */
	public static void breakRetainsAtSource(GameTestHelper helper) {
		buildFlowLine(helper);
		driveFlow(helper, 40);

		helper.setBlock(FLOW_CABLES[2], Blocks.AIR);
		long macAtBreak = be(helper, FLOW_MAC) instanceof MaceratorBlockEntity m ? m.getEnergyStorage().getAmount() : -1;
		driveFlow(helper, 10);

		long sourceSide = Math.max(0, cableAmount(helper, FLOW_CABLES[0]))
				+ Math.max(0, cableAmount(helper, FLOW_CABLES[1]));
		if (sourceSide <= 0) {
			helper.fail("source-side cables retained no EU after the break — the line buffer is not real");
		}
		long macAfter = be(helper, FLOW_MAC) instanceof MaceratorBlockEntity m ? m.getEnergyStorage().getAmount() : -1;
		if (macAfter > macAtBreak) {
			helper.fail("macerator gained EU after the line was cut (" + macAtBreak + " -> " + macAfter
					+ ") — energy teleported across the break");
		}
		helper.succeed();
	}

	/**
	 * MOD-070: a fuelled generator wired to a cable with NO consumer still fills the cable to its buffer
	 * capacity — the wire holds energy from a source even with nowhere to deliver it (0 under the old
	 * "producer-only network sleeps and never charges" behaviour).
	 * Mirrors: NetworkGameTest.rNrg09b_sourceFillsCableWithoutConsumer
	 */
	public static void sourceFillsCableWithoutConsumer(GameTestHelper helper) {
		BlockPos genPos = new BlockPos(1, 2, 1);
		BlockPos cablePos = new BlockPos(2, 2, 1);
		helper.setBlock(genPos, ModContent.GENERATOR.get());
		helper.setBlock(cablePos, ModContent.COPPER_CABLE.get());
		if (be(helper, genPos) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		for (int i = 0; i < 10; i++) {
			tick(helper, be(helper, genPos));
			tick(helper, be(helper, cablePos));
			NetworkManager.tickAll(helper.getLevel());
		}
		long cable = be(helper, cablePos) instanceof CableBlockEntity c ? c.getEnergyStorage().getAmount() : -1;
		if (cable != Config.cableBuffer) {
			helper.fail("producer-only cable did not fill to its buffer: " + cable + "/" + Config.cableBuffer
					+ " — a source must charge the wires even with no consumer");
		}
		helper.succeed();
	}

	// ── scenario 4: MOD-021 proportional distance loss over a 10-cable line ───────────────────────

	// Zig-zag to fit 10 cables inside the default gametest region.
	private static final BlockPos LOSS_GEN = new BlockPos(0, 2, 1);
	private static final BlockPos[] LOSS_CABLES = {
		new BlockPos(1, 2, 1), new BlockPos(2, 2, 1), new BlockPos(3, 2, 1), new BlockPos(4, 2, 1),
		new BlockPos(5, 2, 1), new BlockPos(6, 2, 1), new BlockPos(7, 2, 1),
		new BlockPos(7, 2, 2), new BlockPos(7, 2, 3), new BlockPos(7, 2, 4),
	};
	private static final BlockPos LOSS_BOX = new BlockPos(7, 2, 5);

	/**
	 * MOD-021/MOD-253: a 10-cable line loses EU according to the attenuating distance model. Flow is
	 * bounded by the live copper segment buffer, and the receiver gains
	 * {@code flow - EnergyShare.cableLoss(flow, rate, 10)} EU/tick.
	 * Mirrors: NetworkGameTest.tcCable001Nrg02_lossOverTenCables
	 */
	public static void mod021LossOverTenCables(GameTestHelper helper) {
		helper.setBlock(LOSS_GEN, ModContent.GENERATOR.get());
		for (BlockPos c : LOSS_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		helper.setBlock(LOSS_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		long cap = EnergyTier.LV.maxVoltage();
		if (be(helper, LOSS_GEN) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = cap * 200;
		}
		if (be(helper, LOSS_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0;
		}
		int ticks = 50;
		for (int i = 0; i < ticks; i++) {
			tick(helper, be(helper, LOSS_GEN));
			for (BlockPos c : LOSS_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, LOSS_BOX));
		}
		long got = be(helper, LOSS_BOX) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		// MOD-070: per-cable throughput is the segment buffer (cableBuffer), not the tier voltage;
		// MOD-253 attenuation applies to the actual per-tick flow. Fill front ~1 cable/tick.
		long flow = Config.cableBuffer;
		long lossPerTick = EnergyShare.cableLoss(flow, Config.copperCableLossPerBlock, 10);
		long deliveredPerTick = flow - lossPerTick;
		long fillLatency = LOSS_CABLES.length;
		long expected = deliveredPerTick * (ticks - fillLatency);
		if (lossPerTick <= 0) {
			helper.fail("test misconfigured: copperCableLossPerBlock too low to lose EU over 10 cables");
		}
		if (got >= flow * ticks) {
			helper.fail("expected proportional cable loss over 10 cables but got full lossless delivery: " + got);
		}
		if (got < expected - 2 * flow || got > deliveredPerTick * ticks) {
			helper.fail("delivered EU does not match the loss model: expected ~" + expected + ", got " + got);
		}
		helper.succeed();
	}

	/**
	 * MOD-253 end-to-end regression: fifty real copper segment buffers must attenuate a 12 EU packet
	 * to 5 EU, never black-hole it, and must still pass the final one-EU BatteryBox top-off.
	 */
	public static void mod253LongCopperLineStillDeliversAndTopsOff(GameTestHelper helper) {
		// NeoForge runs many minecraft:empty tests in one shared level with closely spaced origins.
		// Keep this large fixture above the ordinary y=2 plane so neighbouring test sources cannot
		// shorten its producer-distance field.
		int baseY = 100;
		BlockPos genPos = new BlockPos(0, baseY, 1);
		List<BlockPos> cables = mod253FiftyCablePath();
		BlockPos boxPos = new BlockPos(3, baseY + 2, 3);

		helper.setBlock(genPos, ModContent.GENERATOR.get());
		for (BlockPos cable : cables) {
			helper.setBlock(cable, ModContent.COPPER_CABLE.get());
		}
		helper.setBlock(boxPos, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));

		if (be(helper, genPos) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = EnergyTier.LV.maxVoltage() * 400;
		}
		if (be(helper, boxPos) instanceof BatteryBoxBlockEntity box) {
			box.getEnergyStorage().amount = 0;
		}

		driveMod253Line(helper, genPos, cables, boxPos, 100);
		long before = be(helper, boxPos) instanceof BatteryBoxBlockEntity box
				? box.getEnergyStorage().getAmount() : -1;
		driveMod253Line(helper, genPos, cables, boxPos, 20);
		long after = be(helper, boxPos) instanceof BatteryBoxBlockEntity box
				? box.getEnergyStorage().getAmount() : -1;

		long flow = Config.cableBuffer;
		long expectedPerTick = (long) Math.ceil(flow
				* Math.pow(1.0 - Config.copperCableLossPerBlock, cables.size()));
		if (expectedPerTick != 5L) {
			helper.fail("MOD-253 golden balance drift: expected copper/50 delivery 5 EU/t, got "
					+ expectedPerTick + " from config");
			return;
		}
		long gained = after - before;
		if (gained != expectedPerTick * 20) {
			helper.fail("50-cable line delivered " + gained + " EU over 20 steady ticks; expected "
					+ (expectedPerTick * 20) + " from real " + flow + " EU segment buffers");
			return;
		}

		for (BlockPos cable : cables) {
			if (!(be(helper, cable) instanceof CableBlockEntity segment)) {
				helper.fail("missing cable block entity at " + cable);
				return;
			}
			if (segment.getEnergyStorage().getCapacity() != flow
					|| segment.getEnergyStorage().getAmount() > flow) {
				helper.fail("cable at " + cable + " does not use the real " + flow
						+ " EU copper segment buffer");
				return;
			}
		}

		long capacity = Config.batteryBoxBuffer;
		if (be(helper, boxPos) instanceof BatteryBoxBlockEntity box) {
			box.getEnergyStorage().amount = capacity - 1;
			box.setChanged();
		}
		driveMod253Line(helper, genPos, cables, boxPos, 5);
		long toppedOff = be(helper, boxPos) instanceof BatteryBoxBlockEntity box
				? box.getEnergyStorage().getAmount() : -1;
		if (toppedOff != capacity) {
			helper.fail("MOD-009 regression on 50-cable line: BatteryBox stopped at "
					+ toppedOff + "/" + capacity);
			return;
		}
		helper.succeed();
	}

	private static List<BlockPos> mod253FiftyCablePath() {
		int baseY = 100;
		List<BlockPos> path = new ArrayList<>(50);
		for (int row = 0; row < 4; row++) {
			int z = 1 + row * 2;
			if ((row & 1) == 0) {
				for (int x = 1; x <= 7; x++) path.add(new BlockPos(x, baseY, z));
				if (row < 3) path.add(new BlockPos(7, baseY, z + 1));
			} else {
				for (int x = 7; x >= 1; x--) path.add(new BlockPos(x, baseY, z));
				if (row < 3) path.add(new BlockPos(1, baseY, z + 1));
			}
		}
		path.add(new BlockPos(1, baseY + 1, 7));
		for (int x = 1; x <= 7; x++) path.add(new BlockPos(x, baseY + 2, 7));
		path.add(new BlockPos(7, baseY + 2, 6));
		for (int x = 7; x >= 1; x--) path.add(new BlockPos(x, baseY + 2, 5));
		path.add(new BlockPos(1, baseY + 2, 4));
		path.add(new BlockPos(1, baseY + 2, 3));
		path.add(new BlockPos(2, baseY + 2, 3));
		if (path.size() != 50) {
			throw new IllegalStateException("MOD-253 fixture must contain exactly 50 cables");
		}
		return path;
	}

	private static void driveMod253Line(GameTestHelper helper, BlockPos genPos, List<BlockPos> cables,
			BlockPos boxPos, int ticks) {
		for (int i = 0; i < ticks; i++) {
			tick(helper, be(helper, genPos));
			for (BlockPos cable : cables) {
				tick(helper, be(helper, cable));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, boxPos));
		}
	}

	/**
	 * MOD-219 in-place grade swap: replacing a copper cable with a gold one WITHOUT breaking it first
	 * (a {@code /setblock}, {@code /fill}, structure or third-party world edit) must leave a segment that
	 * actually behaves as gold.
	 *
	 * <p>All five cable blocks share one {@code BlockEntityType}, which raises the question of whether the
	 * old entity survives such a swap and keeps the previous grade's numbers. Measured on 26.2 it does
	 * NOT: the entity is rebuilt and the segment reports gold's buffer immediately. This test pins that
	 * down — it is a guard on behaviour the cable ladder depends on but does not itself control, so it
	 * fails loudly if a future version (or a change to how these blocks are registered) starts reusing the
	 * entity and silently leaves a gold cable transporting on copper's throughput.
	 */
	public static void inPlaceGradeSwapRebuildsSegment(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 1);
		helper.setBlock(pos, ModContent.COPPER_CABLE.get());
		tick(helper, be(helper, pos));
		NetworkManager.tickAll(helper.getLevel());
		long copperCapacity = be(helper, pos) instanceof CableBlockEntity c
				? c.getEnergyStorage().getCapacity() : -1;

		// Swap the block in place — no break, no air in between.
		helper.setBlock(pos, ModContent.GOLD_CABLE.get());
		tick(helper, be(helper, pos));
		NetworkManager.tickAll(helper.getLevel());

		if (!(be(helper, pos) instanceof CableBlockEntity swapped)) {
			helper.fail("no cable block entity after the in-place swap");
			return;
		}
		if (swapped.cableType() != CableType.GOLD) {
			helper.fail("after swapping copper -> gold in place the segment still reports "
					+ swapped.cableType() + " — the network would transport on the wrong grade");
		}
		long goldCapacity = swapped.getEnergyStorage().getCapacity();
		if (goldCapacity != CableType.GOLD.segmentBuffer()) {
			helper.fail("segment buffer stayed at " + goldCapacity + " EU after the swap (copper was "
					+ copperCapacity + ", gold must be " + CableType.GOLD.segmentBuffer()
					+ ") — the cable looks gold but still carries copper's throughput");
		}
		helper.succeed();
	}

	/**
	 * MOD-219 mixed network: a single gold segment spliced into an otherwise copper line re-tariffs the
	 * WHOLE network — it is governed by the strongest cable present, not by the majority or by the
	 * segments the consumer happens to touch.
	 *
	 * <p><b>Observable effect: delivery goes DOWN.</b> The strongest cable supplies both numbers and they
	 * pull in opposite directions — gold raises the packet cap (32 → 128 EU/t) but also raises the loss
	 * (0.02 → 0.03/block). Measured end-to-end over ten cables the higher toll wins: the spliced line
	 * banks ~10% less than the all-copper baseline (400 → 360 EU on both loaders). The raised cap buys
	 * nothing here because the flow is already bounded by the copper segments' 12 EU buffers, not by the
	 * cap — which is exactly why a player must run gold end-to-end rather than splice one segment in.
	 *
	 * <p>This is the scenario the unit tests cannot reach: {@code CableTypeTest} only ranks the grades in
	 * isolation and would stay green even if {@code EnergyTopologyCache} stopped consulting
	 * {@link CableType#strongerThan} altogether. Here the whole path — topology cache → network → packet
	 * cap — is under test: if the network ignored the gold segment, both lines would deliver exactly the
	 * same amount, which is precisely how this test fails when the rule is reverted.
	 */
	public static void mixedNetworkTakesLossFromStrongestCable(GameTestHelper helper) {
		// Both lines are built in cells no other scenario touches, at two distinct heights well above the
		// shared y=2 working plane. Two reasons, both learned the hard way here:
		//   1. Rebuilding the second line in the SAME cells left NeoForge still seeing the first network
		//      (identical delivery both runs) — block-entity removal and the topology rebuild do not settle
		//      inside this synthetic tick loop.
		//   2. Reusing the standard LOSS_* plane made the baseline flap between 400 and 200 EU on NeoForge,
		//      where every scenario shares one ServerLevel and mod021LossOverTenCables owns those very cells.
		long allCopper = runLossLine(helper, 28, -1);
		long withOneGold = runLossLine(helper, 32, LOSS_CABLES.length / 2);

		if (allCopper <= 0 || withOneGold <= 0) {
			helper.fail("no EU delivered (copper=" + allCopper + ", mixed=" + withOneGold
					+ ") — the fixture is broken, not the strongest-cable rule");
		}
		if (withOneGold >= allCopper) {
			helper.fail("one gold segment did not govern the line: copper-only delivered " + allCopper
					+ " EU, the mixed line delivered " + withOneGold
					+ " — expected strictly less, since the network must take the strongest cable's loss"
					+ " (0.03 vs 0.02). Equal numbers mean the gold segment was ignored entirely.");
		}
		helper.succeed();
	}

	/**
	 * Build the 10-cable loss line {@code dy} blocks above the standard one, drive it for a fixed number of
	 * ticks and return the EU banked by its box. {@code goldIndex} &gt;= 0 makes that one cable gold; -1
	 * keeps the line all copper. Each call must use a distinct {@code dy} — see the caller for why.
	 */
	private static long runLossLine(GameTestHelper helper, int dy, int goldIndex) {
		BlockPos gen = LOSS_GEN.above(dy);
		BlockPos box = LOSS_BOX.above(dy);
		BlockPos[] cables = new BlockPos[LOSS_CABLES.length];
		for (int i = 0; i < LOSS_CABLES.length; i++) {
			cables[i] = LOSS_CABLES[i].above(dy);
		}
		helper.setBlock(gen, ModContent.GENERATOR.get());
		for (int i = 0; i < cables.length; i++) {
			helper.setBlock(cables[i],
					i == goldIndex ? ModContent.GOLD_CABLE.get() : ModContent.COPPER_CABLE.get());
		}
		helper.setBlock(box, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		// Over-charge the generator and empty the box so the flow is bounded by the line, not the source.
		if (be(helper, gen) instanceof GeneratorBlockEntity g) {
			g.getEnergyStorage().amount = EnergyTier.LV.maxVoltage() * 200;
		}
		if (be(helper, box) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0;
		}
		for (int i = 0; i < 50; i++) {
			tick(helper, be(helper, gen));
			for (BlockPos c : cables) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, box));
		}
		return be(helper, box) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
	}

	// ── scenario 4b: ring network — two arms joined by a closing cable merge on tick 0 (MOD-025) ────

	// Ring: GEN(1,2,2) touches two cable arms directly (north + south), each arm runs +x to
	// RING_A3/RING_B3 (x=4). MAC(4,2,2) sits between the two arms but — being a machine, not a
	// cable — is NOT itself part of the union-find graph (NetworkManager.register only unions on
	// cable-to-cable adjacency). The two arms are joined into a real ring by a 3-cable bypass column
	// one block further east (x=5), cable-to-cable adjacent to RING_A3/RING_B3, closing the cycle
	// around GEN+MAC without any cable occupying MAC's own cell. Mirrors:
	// NetworkGameTest.tcCable001Con04_ringNetworkNoDeadlock.
	private static final BlockPos RING_GEN = new BlockPos(1, 2, 2);
	private static final BlockPos RING_A0 = new BlockPos(1, 2, 1); // GEN's north neighbour
	private static final BlockPos RING_A1 = new BlockPos(2, 2, 1);
	private static final BlockPos RING_A2 = new BlockPos(3, 2, 1);
	private static final BlockPos RING_A3 = new BlockPos(4, 2, 1); // MAC's north neighbour
	private static final BlockPos RING_B0 = new BlockPos(1, 2, 3); // GEN's south neighbour
	private static final BlockPos RING_B1 = new BlockPos(2, 2, 3);
	private static final BlockPos RING_B2 = new BlockPos(3, 2, 3);
	private static final BlockPos RING_B3 = new BlockPos(4, 2, 3); // MAC's south neighbour
	private static final BlockPos RING_MAC = new BlockPos(4, 2, 2);
	private static final BlockPos RING_BYPASS_A = new BlockPos(5, 2, 1); // east of RING_A3
	private static final BlockPos RING_BYPASS_MID = new BlockPos(5, 2, 2); // closes the cycle
	private static final BlockPos RING_BYPASS_B = new BlockPos(5, 2, 3); // east of RING_B3

	private static void driveRing(GameTestHelper helper, int n) {
		BlockPos[] cables = {
			RING_A0, RING_A1, RING_A2, RING_A3, RING_B0, RING_B1, RING_B2, RING_B3,
			RING_BYPASS_A, RING_BYPASS_MID, RING_BYPASS_B,
		};
		for (int i = 0; i < n; i++) {
			tick(helper, be(helper, RING_GEN));
			for (BlockPos c : cables) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, RING_MAC));
		}
	}

	/**
	 * MOD-025: a ring network (two independent cable arms connecting the same generator to the same
	 * macerator, closed into a cycle by a bypass cable joining the two arms directly) resolves to ONE
	 * {@link EnergyNetwork} the instant the closing cable registers (tick 0) — union-find merge on
	 * cycle, no deadlock, no duplicate network.
	 * Mirrors: NetworkGameTest.tcCable001Con04_ringNetworkNoDeadlock
	 */
	public static void ringNetworkMergesOnClose(GameTestHelper helper) {
		helper.setBlock(RING_GEN, ModContent.GENERATOR.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		helper.setBlock(RING_A0, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_A1, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_A2, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_A3, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_B0, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_B1, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_B2, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_B3, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_MAC, ModContent.MACERATOR.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		if (be(helper, RING_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}
		if (be(helper, RING_MAC) instanceof MaceratorBlockEntity mac) {
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
		}

		// helper.setBlock bypasses CableBlock.setPlacedBy (no LivingEntity placer), so a cable only joins
		// NetworkManager on its own first serverTick (CableBlockEntity.ensureRegistered). Register both
		// arms with one tick each — no NetworkManager.tickAll, so this is still "tick 0" for the network
		// topology, before any EU has moved.
		BlockPos[] armCables = {RING_A0, RING_A1, RING_A2, RING_A3, RING_B0, RING_B1, RING_B2, RING_B3};
		for (BlockPos c : armCables) {
			tick(helper, be(helper, c));
		}

		// Both arms are up but not yet joined: two disjoint cable networks.
		EnergyNetwork armABefore = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RING_A0));
		EnergyNetwork armBBefore = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RING_B0));
		if (armABefore == null || armBBefore == null || armABefore == armBBefore) {
			helper.fail("test setup: the two arms must start as disjoint networks before closing the ring");
		}

		// Close the cycle: place + register the bypass cable(s), cable-to-cable adjacent to both RING_A3
		// and RING_B3, so NetworkManager.register's adjacency scan must union both arms into one network
		// immediately (still tick 0 for the topology: registration, not NetworkManager.tickAll).
		helper.setBlock(RING_BYPASS_A, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_BYPASS_MID, ModContent.COPPER_CABLE.get());
		helper.setBlock(RING_BYPASS_B, ModContent.COPPER_CABLE.get());
		tick(helper, be(helper, RING_BYPASS_A));
		tick(helper, be(helper, RING_BYPASS_MID));
		tick(helper, be(helper, RING_BYPASS_B));

		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RING_A0));
		EnergyNetwork netB = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(RING_B0));
		if (net == null || netB == null || net != netB) {
			helper.fail("both ring branches must resolve to the same network (union-find merge on cycle)");
		}

		driveRing(helper, 120);

		long mac = be(helper, RING_MAC) instanceof MaceratorBlockEntity m ? m.getEnergyStorage().getAmount() : -1;
		if (mac <= 0) {
			helper.fail("ring network delivered no EU to the macerator: " + mac);
		}
		helper.succeed();
	}

	// ── scenario 11: cable network negatives — diagonal no-connect, vanilla neighbor no-NPE ────────

	/**
	 * Two cables touching only at a corner/edge (no shared face) must NOT merge into one network:
	 * connections are strictly the 6 orthogonal faces, never diagonal.
	 * Mirrors: NetworkGameTest.tcCable001Phy09_diagonalCablesDoNotConnect
	 */
	public static void diagonalCablesDoNotConnect(GameTestHelper helper) {
		BlockPos a = new BlockPos(1, 2, 1);
		BlockPos b = new BlockPos(2, 3, 1); // +x AND +y from a: corner/edge contact only
		helper.setBlock(a, ModContent.COPPER_CABLE.get());
		helper.setBlock(b, ModContent.COPPER_CABLE.get());
		if (be(helper, a) instanceof CableBlockEntity ca) {
			ca.ensureRegistered();
		}
		if (be(helper, b) instanceof CableBlockEntity cb) {
			cb.ensureRegistered();
		}
		NetworkManager.tickAll(helper.getLevel());
		EnergyNetwork netA = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(a));
		EnergyNetwork netB = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(b));
		if (netA == null || netB == null) {
			helper.fail("both diagonally-placed cables must register their own (separate) networks");
			return;
		}
		if (netA == netB) {
			helper.fail("diagonal (corner/edge-only) cables must NOT merge into one network (R-CON-06)");
			return;
		}
		helper.succeed();
	}

	/**
	 * A cable adjacent to a vanilla furnace (no Team Reborn / NeoForge energy capability) does not NPE
	 * during endpoint discovery and does not leak EU into it.
	 * Mirrors: NetworkGameTest.tcCable001Neg01_vanillaNeighborNoNpe
	 */
	public static void cableVanillaNeighborNoNpe(GameTestHelper helper) {
		BlockPos gen = new BlockPos(1, 2, 1);
		BlockPos cable = new BlockPos(2, 2, 1);
		BlockPos furnace = new BlockPos(3, 2, 1);
		helper.setBlock(gen, ModContent.GENERATOR.get());
		helper.setBlock(cable, ModContent.COPPER_CABLE.get());
		helper.setBlock(furnace, Blocks.FURNACE);
		long genEuBefore = 0;
		if (be(helper, gen) instanceof GeneratorBlockEntity g) {
			g.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			genEuBefore = g.getEnergyStorage().getAmount();
		} else {
			helper.fail("generator block entity missing");
			return;
		}
		// 60 ticks: must not NPE while probing the vanilla furnace neighbour each tick.
		for (int i = 0; i < 60; i++) {
			tick(helper, be(helper, gen));
			tick(helper, be(helper, cable));
			NetworkManager.tickAll(helper.getLevel());
		}
		// Cross-loader no-leak oracle: the generator burns coal and produces EU, but the only neighbour
		// on the cable's far side is a vanilla furnace (no energy capability on either loader). EU must
		// therefore accumulate in the generator's own buffer (strictly more than before) — if it had
		// leaked into / been voided by the furnace, the buffer would not grow. This mirrors the Fabric
		// lane's EnergyStorage.SIDED.find(...)==null check without depending on a loader-specific API.
		long genEuAfter = be(helper, gen) instanceof GeneratorBlockEntity g2 ? g2.getEnergyStorage().getAmount() : -1;
		if (genEuAfter <= genEuBefore) {
			helper.fail("generator buffer did not grow over 60 ticks (before=" + genEuBefore
					+ ", after=" + genEuAfter + ") — EU likely leaked into the vanilla furnace or was voided");
			return;
		}
		helper.succeed();
	}

	// ── scenario 27: network split + rejoin (R-CON-04, R-CON-09) ───────────────────────────────────

	/**
	 * Removing the only cable between a generator and a macerator stops delivery; replacing it resumes
	 * flow. Catches a regression where the network fails to split/rejoin on topology change.
	 * Mirrors: NetworkGameTest.it001Neg01_breakStopsDelivery + it001Fun02_rejoinResumesFlow
	 */
	public static void networkSplitRejoinResumesFlow(GameTestHelper helper) {
		// Built through EnergyLine so that BOTH halves prove the wire carried the energy, not just
		// that the macerator ended up with some. Note the rebaseline() before the rejoin phase: the
		// peak-buffer observation is cumulative, so without it an assertFlowedThroughCable() after the
		// rejoin would pass on data collected BEFORE the cable was ever broken — a vacuous assertion
		// that reads like a strong one.
		EnergyLine line = EnergyLine.in(helper)
				.fueledGenerator()
				.cables(1)
				.consumer(ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 8))
				.build()
				.drive(60)
				.assertConsumerCharged()
				.assertFlowedThroughCable();

		// Break the cable: the macerator must stop receiving.
		BlockPos cable = line.cablePos(0);
		helper.setBlock(cable, Blocks.AIR);
		if (be(helper, line.consumerPos()) instanceof MachineBlockEntity mac) {
			mac.getEnergyStorage().amount = 0;
		}
		line.drive(40);
		long afterBreak = be(helper, line.consumerPos()) instanceof MachineBlockEntity m
				? m.getEnergyStorage().getAmount() : -1;
		if (afterBreak != 0) {
			helper.fail("macerator kept receiving EU after the cable was removed: " + afterBreak);
			return;
		}
		// Rejoin: replacing the cable must resume flow — and it must resume THROUGH the new cable.
		helper.setBlock(cable, ModContent.COPPER_CABLE.get());
		line.rebaseline()
				.drive(80)
				.assertConsumerCharged()
				.assertFlowedThroughCable();
		helper.succeed();
	}

	// ── scenario 31: cable throughput cap ≤ LV.maxVoltage (R-NRG-04) ───────────────────────────────

	/**
	 * A cabled generator with an ample pre-charged buffer delivers at most {@code EnergyTier.LV.maxVoltage()}
	 * EU to the consumer in one network tick; the cable is not destroyed and the surplus is simply not
	 * transferred (no overvoltage penalty). Catches a regression that removes the per-tick packet cap.
	 * Mirrors: NetworkGameTest.tcCable001Nrg01_throughputCappedAtLvVoltage
	 */
	public static void cableThroughputCappedAtLv(GameTestHelper helper) {
		BlockPos genPos = new BlockPos(1, 2, 1);
		BlockPos cablePos = new BlockPos(2, 2, 1);
		BlockPos boxPos = new BlockPos(3, 2, 1);
		helper.setBlock(genPos, ModContent.GENERATOR.get());
		helper.setBlock(cablePos, ModContent.COPPER_CABLE.get());
		helper.setBlock(boxPos, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		long cap = EnergyTier.LV.maxVoltage();
		if (be(helper, genPos) instanceof GeneratorBlockEntity gen) {
			gen.getEnergyStorage().amount = cap * 100; // ample, no fuel: buffer far above any throughput
		}
		if (be(helper, boxPos) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0; // empty: room is never the limit
		}
		// MOD-070: per-cable throughput is the segment buffer (cableBuffer), not the tier voltage. Drive
		// several ticks; cumulative delivery is bounded by cableBuffer EU/tick, never a 32 EU packet.
		int ticks = 20;
		for (int i = 0; i < ticks; i++) {
			tick(helper, be(helper, genPos));
			tick(helper, be(helper, cablePos));
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, boxPos));
		}
		long got = be(helper, boxPos) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		long perTickCap = Config.cableBuffer;
		if (got > perTickCap * ticks) {
			helper.fail("cable exceeded its throughput cap: delivered=" + got + " > " + (perTickCap * ticks));
			return;
		}
		if (got <= 0) {
			helper.fail("cable delivered no EU even though supply and room were ample: " + got);
			return;
		}
		helper.succeed();
	}

	// ── scenario 36: two-generator supply sums into one consumer (R-CON-16, no dupe) ──────────────

	/**
	 * Two LV generators feeding one battery box through joined cables deliver summed supply (bounded by
	 * the LV packet cap) — the delivered EU exceeds what a single generator alone could deliver, proving
	 * the two sources sum rather than one shadowing the other.
	 * Mirrors: NetworkGameTest.tcCable001Con05_twoGeneratorsSumIntoOneConsumer
	 */
	public static void twoGeneratorsSumIntoOneConsumer(GameTestHelper helper) {
		BlockPos genA = new BlockPos(1, 2, 1);
		BlockPos cableA = new BlockPos(2, 2, 1);
		BlockPos cableB = new BlockPos(3, 2, 1);
		BlockPos genB = new BlockPos(4, 2, 1);
		BlockPos boxPos = new BlockPos(3, 2, 2);
		helper.setBlock(genA, ModContent.GENERATOR.get());
		helper.setBlock(cableA, ModContent.COPPER_CABLE.get());
		helper.setBlock(cableB, ModContent.COPPER_CABLE.get());
		helper.setBlock(genB, ModContent.GENERATOR.get());
		helper.setBlock(boxPos, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		for (BlockPos g : new BlockPos[]{genA, genB}) {
			if (be(helper, g) instanceof GeneratorBlockEntity gen) {
				gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
				gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();
			}
		}
		if (be(helper, boxPos) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0;
		}
		int ticks = 20;
		for (int i = 0; i < ticks; i++) {
			for (BlockPos g : new BlockPos[]{genA, genB}) {
				tick(helper, be(helper, g));
			}
			tick(helper, be(helper, cableA));
			tick(helper, be(helper, cableB));
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, boxPos));
		}
		long got = be(helper, boxPos) instanceof BatteryBoxBlockEntity bb ? bb.getEnergyStorage().getAmount() : -1;
		long singleCeiling = Config.fuelEuPerTick * (long) ticks;
		if (got <= singleCeiling) {
			helper.fail("battery_box should receive summed supply from two generators, got only " + got
					+ " (a single generator alone could not exceed " + singleCeiling + " over " + ticks + ")");
			return;
		}
		helper.succeed();
	}

	// ── MOD-252: a source must not fence off the sources behind it (the "watershed" seam) ─────────

	// The player's horizontal arm, reduced to its smallest reproducing shape. Both generators sit on the
	// same bus; only genB's cable touches the consumer. genA is the "far" source — the one the seam used
	// to lock out. Own cell block (y=40) because the NeoForge lane shares one ServerLevel across scenarios.
	private static final BlockPos ARM_GEN_FAR = new BlockPos(1, 40, 1);
	private static final BlockPos ARM_CABLE_FAR = new BlockPos(2, 40, 1);
	private static final BlockPos ARM_CABLE_NEAR = new BlockPos(3, 40, 1);
	private static final BlockPos ARM_GEN_NEAR = new BlockPos(4, 40, 1);
	private static final BlockPos ARM_BOX = new BlockPos(3, 40, 2);

	/**
	 * MOD-252 — a generator standing between another generator and the consumer must not lock that other
	 * generator out.
	 *
	 * <p>Found on a playtest: on a bus carrying nine wind mills the player saw four of them frozen at
	 * 4000/4000 while their neighbours ran dry, and a battery box wired onto the arm drained the mills one
	 * at a time rather than all at once. Cause: the flow field was a BFS from the SOURCES, so the midpoint
	 * between two sources was a local maximum, and the strictly-downhill pull rule cannot cross a maximum.
	 * The source behind the seam filled its own stretch of bus, hit a full buffer and stalled forever.
	 *
	 * <p>Reduced here to two generators on one bus. Both are pre-charged and UNFUELLED, so each one's
	 * buffer is a one-way meter of what it actually contributed. The oracle is the far generator: with the
	 * seam it gives up exactly {@code cableBuffer} EU — one fill of its own dead-end segment — and then
	 * nothing, ever. Both must be draining for this to pass.
	 */
	public static void mod252FarSourceBehindNearSourceDischarges(GameTestHelper helper) {
		helper.setBlock(ARM_GEN_FAR, ModContent.GENERATOR.get());
		helper.setBlock(ARM_CABLE_FAR, ModContent.COPPER_CABLE.get());
		helper.setBlock(ARM_CABLE_NEAR, ModContent.COPPER_CABLE.get());
		helper.setBlock(ARM_GEN_NEAR, ModContent.GENERATOR.get());
		// FACING = NORTH → the IN face is the cable at (3,40,1), i.e. only the NEAR cable touches the box.
		helper.setBlock(ARM_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		long genStart = Config.generatorBuffer;
		for (BlockPos g : new BlockPos[]{ARM_GEN_FAR, ARM_GEN_NEAR}) {
			if (be(helper, g) instanceof GeneratorBlockEntity gen) {
				gen.getEnergyStorage().amount = genStart; // no fuel: the buffer can only go down
				gen.setChanged();
			}
		}
		if (be(helper, ARM_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0L;
		}
		for (int i = 0; i < 60; i++) {
			tick(helper, be(helper, ARM_GEN_FAR));
			tick(helper, be(helper, ARM_GEN_NEAR));
			tick(helper, be(helper, ARM_CABLE_FAR));
			tick(helper, be(helper, ARM_CABLE_NEAR));
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, ARM_BOX));
		}
		long farLeft = be(helper, ARM_GEN_FAR) instanceof GeneratorBlockEntity g ? g.getEnergyStorage().getAmount() : -1;
		long nearLeft = be(helper, ARM_GEN_NEAR) instanceof GeneratorBlockEntity g ? g.getEnergyStorage().getAmount() : -1;
		long farDrain = genStart - farLeft;
		long nearDrain = genStart - nearLeft;
		if (nearDrain <= 0) {
			helper.fail("the generator next to the consumer gave nothing (" + nearDrain
					+ " EU) — the fixture is broken, not the watershed rule");
			return;
		}
		if (farDrain <= Config.cableBuffer) {
			helper.fail("the far generator gave only " + farDrain + " EU over 60 ticks (one segment fill is "
					+ Config.cableBuffer + ") — it is fenced off by the nearer generator: the flow field still"
					+ " puts a local maximum between two sources, and energy cannot cross a maximum");
			return;
		}
		helper.succeed();
	}

	// The same watershed, stood on end — the player's tower verbatim: a wind-mill group high up, a second
	// group lower down the same trunk, and the base at the bottom. The cable graph has no notion of Y, so
	// this is topologically the arm above; it is pinned separately because the reported case was vertical
	// and "it is the same graph" is an argument, not a gate. Own cell block (y=72..80).
	private static final BlockPos VERT_GEN_TOP = new BlockPos(1, 80, 1);
	private static final BlockPos[] VERT_CABLES = {
		new BlockPos(1, 79, 1), new BlockPos(1, 78, 1), new BlockPos(1, 77, 1),
		new BlockPos(1, 76, 1), new BlockPos(1, 75, 1), new BlockPos(1, 74, 1),
		new BlockPos(1, 73, 1), new BlockPos(1, 72, 1),
	};
	private static final BlockPos VERT_GEN_MID = new BlockPos(2, 76, 1);
	private static final BlockPos VERT_BOX = new BlockPos(2, 72, 1);

	/**
	 * MOD-252 acceptance — two source groups at different heights on one trunk both discharge.
	 *
	 * <p>The top generator hangs off the dead end of the trunk and the mid generator sits between it and
	 * the base, which is exactly the shape that used to lock the top group out: seeded from the sources,
	 * the midpoint between the two was a local maximum, and the strictly-downhill pull rule cannot cross a
	 * maximum. In game that read as four wind mills frozen at a full buffer while the group below them ran.
	 *
	 * <p>Same oracle as the arm case: neither generator has fuel, so its buffer is a one-way meter of what
	 * it actually contributed. The far source giving up no more than one segment fill ({@code cableBuffer})
	 * is the signature of the seam.
	 */
	public static void mod252VerticalSourceGroupsBothDischarge(GameTestHelper helper) {
		helper.setBlock(VERT_GEN_TOP, ModContent.GENERATOR.get());
		for (BlockPos c : VERT_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		// FACING = EAST so the inert front (R-NRG-03) points away from the trunk and the west face — the
		// one touching the cable at (1,76,1) — is a real output. The top generator needs no such care: it
		// meets the trunk on its DOWN face, and FACING is horizontal.
		helper.setBlock(VERT_GEN_MID, ModContent.GENERATOR.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		// FACING = WEST → the IN face is the cable at (1,72,1); the box is a pure sink at the foot.
		helper.setBlock(VERT_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));

		long genStart = Config.generatorBuffer;
		for (BlockPos g : new BlockPos[]{VERT_GEN_TOP, VERT_GEN_MID}) {
			if (be(helper, g) instanceof GeneratorBlockEntity gen) {
				gen.getEnergyStorage().amount = genStart; // no fuel: the buffer can only go down
				gen.setChanged();
			}
		}
		if (be(helper, VERT_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0L;
		}
		for (int i = 0; i < 120; i++) {
			tick(helper, be(helper, VERT_GEN_TOP));
			tick(helper, be(helper, VERT_GEN_MID));
			for (BlockPos c : VERT_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, VERT_BOX));
		}

		long topLeft = be(helper, VERT_GEN_TOP) instanceof GeneratorBlockEntity g
				? g.getEnergyStorage().getAmount() : -1;
		long midLeft = be(helper, VERT_GEN_MID) instanceof GeneratorBlockEntity g
				? g.getEnergyStorage().getAmount() : -1;
		long topDrain = genStart - topLeft;
		long midDrain = genStart - midLeft;
		if (midDrain <= 0) {
			helper.fail("the lower generator gave nothing (" + midDrain
					+ " EU) — the fixture is broken, not the watershed rule");
			return;
		}
		// A fenced-off source can still fill the pocket of cable on its own side of the seam, so the ceiling
		// is the whole line's buffer capacity, not one segment: here the seam falls two cables up the trunk,
		// which is 24 EU — a "> cableBuffer" oracle would pass while still fenced (it did, on first run).
		// Anything above the entire line's capacity had to have been delivered, not merely parked in wire.
		long fencedCeiling = (long) VERT_CABLES.length * Config.cableBuffer;
		if (topDrain <= fencedCeiling) {
			helper.fail("the top generator gave only " + topDrain + " EU over 120 ticks (the whole line holds "
					+ fencedCeiling + ", so this never left the wire) — the group higher up the trunk is"
					+ " fenced off by the group below it, the vertical form of the source-seeded watershed");
			return;
		}
		helper.succeed();
	}

	// Two consumers on opposite sides of one source — the mirror case of the arm above, and the one that
	// must NOT regress: a maximum sitting on the source's own cable is harmless only while both sides
	// drain. Separate cell block (y=44) for the same shared-level reason.
	private static final BlockPos SPLIT_BOX_WEST = new BlockPos(1, 44, 1);
	private static final BlockPos[] SPLIT_CABLES = {
		new BlockPos(2, 44, 1), new BlockPos(3, 44, 1), new BlockPos(4, 44, 1),
		new BlockPos(5, 44, 1), new BlockPos(6, 44, 1),
	};
	private static final BlockPos SPLIT_GEN = new BlockPos(4, 44, 2);
	private static final BlockPos SPLIT_BOX_EAST = new BlockPos(7, 44, 1);

	/**
	 * MOD-252 acceptance — two consumers on opposite sides of one generator both get served. With the
	 * flow field seeded from demand, the generator's own cable becomes the local maximum; that seam is the
	 * harmless kind, because something is drinking on either side of it. Cheap guard that the reversal did
	 * not simply move the starvation to the other end of the line.
	 */
	public static void mod252TwoConsumersOnBothSidesOfSource(GameTestHelper helper) {
		// FACING = EAST → IN face at (2,44,1); FACING = WEST → IN face at (6,44,1).
		helper.setBlock(SPLIT_BOX_WEST, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		for (BlockPos c : SPLIT_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		// FACING = SOUTH so the generator's front (energy-inert, R-NRG-03) points AWAY from the bus and its
		// north face — the one touching the cable at (4,44,1) — is a real output.
		helper.setBlock(SPLIT_GEN, ModContent.GENERATOR.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH));
		helper.setBlock(SPLIT_BOX_EAST, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (be(helper, SPLIT_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();
		}
		for (BlockPos b : new BlockPos[]{SPLIT_BOX_WEST, SPLIT_BOX_EAST}) {
			if (be(helper, b) instanceof BatteryBoxBlockEntity bb) {
				bb.getEnergyStorage().amount = 0L;
			}
		}
		for (int i = 0; i < 120; i++) {
			tick(helper, be(helper, SPLIT_GEN));
			for (BlockPos c : SPLIT_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, SPLIT_BOX_WEST));
			tick(helper, be(helper, SPLIT_BOX_EAST));
		}
		long west = be(helper, SPLIT_BOX_WEST) instanceof BatteryBoxBlockEntity b ? b.getEnergyStorage().getAmount() : -1;
		long east = be(helper, SPLIT_BOX_EAST) instanceof BatteryBoxBlockEntity b ? b.getEnergyStorage().getAmount() : -1;
		if (west <= 0 || east <= 0) {
			helper.fail("both sides of the generator must be served: west=" + west + " EU, east=" + east
					+ " EU — one side is fenced off from the source");
		}
		helper.succeed();
	}

	// A machine on one side of the source and a Battery Box on the other — the mixed-class version of the
	// split rig above, and the one the review found broken. Own cell block (y=60) because the NeoForge lane
	// shares one ServerLevel across scenarios.
	private static final BlockPos MIXED_MAC = new BlockPos(1, 60, 1);
	private static final BlockPos[] MIXED_CABLES = {
		new BlockPos(2, 60, 1), new BlockPos(3, 60, 1), new BlockPos(4, 60, 1),
		new BlockPos(5, 60, 1), new BlockPos(6, 60, 1),
	};
	private static final BlockPos MIXED_GEN = new BlockPos(4, 60, 2);
	private static final BlockPos MIXED_BOX = new BlockPos(7, 60, 1);

	/**
	 * MOD-252 review guard — seeding the flow field is about REACHABILITY, not about priority.
	 *
	 * <p>The first cut of MOD-252 seeded the field from machines only, and fell back to storage sinks just
	 * when no machine wanted anything. That reintroduced the very defect it was written to kill, mirrored to
	 * the other side of the source: EU enters the line only at source-adjacent cables and the pull rule is
	 * strictly downhill, so every cable whose potential sat above the source's own had no filling path at
	 * all. Here the Battery Box is past the generator relative to the macerator, so with a machine-only seed
	 * set the two cables east of the generator stay at 0 and the box never charges — while a single hungry
	 * machine anywhere on the net keeps that state latched.
	 *
	 * <p>The macerator holds ore, so it consumes {@code 2} EU/t against the generator's {@code 8}: its room
	 * is above zero on every tick (it is latched into the seed set, which is what makes this red without the
	 * fix) yet it leaves a genuine surplus for the far side. Both ends must end with energy.
	 */
	public static void mod252MachineAndStorageOnBothSidesOfSource(GameTestHelper helper) {
		helper.setBlock(MIXED_MAC, ModContent.MACERATOR.get());
		for (BlockPos c : MIXED_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		// FACING = SOUTH so the generator's front (energy-inert, R-NRG-03) points away from the bus and its
		// north face — the one touching the cable at (4,60,1) — is a real output.
		helper.setBlock(MIXED_GEN, ModContent.GENERATOR.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH));
		// FACING = WEST → IN face on the cable at (6,60,1); the box is a pure sink of this line.
		helper.setBlock(MIXED_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (be(helper, MIXED_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();
		}
		if (be(helper, MIXED_MAC) instanceof MaceratorBlockEntity mac) {
			mac.getEnergyStorage().amount = 0L;
			mac.setItem(MaceratorBlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 8));
		}
		if (be(helper, MIXED_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0L;
		}
		for (int i = 0; i < 160; i++) {
			tick(helper, be(helper, MIXED_GEN));
			for (BlockPos c : MIXED_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, MIXED_MAC));
			tick(helper, be(helper, MIXED_BOX));
		}
		long mac = be(helper, MIXED_MAC) instanceof MaceratorBlockEntity m ? m.getEnergyStorage().getAmount() : -1;
		long box = be(helper, MIXED_BOX) instanceof BatteryBoxBlockEntity b ? b.getEnergyStorage().getAmount() : -1;
		if (mac <= 0) {
			// MOD-254: this branch is NOT a broken fixture, whatever it used to claim. The generator's own
			// cable is the fork of this rig, and the sweep used to hand that whole buffer to whichever
			// neighbour it visited first — so the machine's side of the bus could read a flat zero while
			// the Box on the far side charged normally. Both cables west of the source are the tell.
			helper.fail("the macerator next to the source got nothing (" + mac + " EU, cables toward it: "
					+ cableAmount(helper, MIXED_CABLES[0]) + "/" + cableAmount(helper, MIXED_CABLES[1])
					+ " EU) — the source's own segment is being drained whole by the other branch instead of"
					+ " being split between the two");
			return;
		}
		long farCable = cableAmount(helper, MIXED_CABLES[4]);
		if (box <= 0) {
			helper.fail("the Battery Box past the generator never charged (" + box + " EU, far cable "
					+ farCable + " EU) — a hungry machine on the other side is fencing off the whole stretch"
					+ " of bus behind the source: only machines seed the flow field, so nothing out here is"
					+ " reachable");
		}
		helper.succeed();
	}

	// Two generators feeding ONE cable that a Battery Box keeps drained — the line is saturated in the
	// sense that matters: the cable never has room for more than a single source's packet. Own cell block
	// (y=64) because the NeoForge lane shares one ServerLevel across scenarios.
	private static final BlockPos SAT_GEN_A = new BlockPos(1, 64, 1);
	private static final BlockPos SAT_CABLE = new BlockPos(2, 64, 1);
	private static final BlockPos SAT_GEN_B = new BlockPos(3, 64, 1);
	private static final BlockPos SAT_BOX = new BlockPos(2, 64, 2);

	/**
	 * MOD-254 (D4) — every source on a saturated line gets to inject, not just the first one in the list.
	 *
	 * <p>The line charge walked its source list from index 0 on every single tick. The cable next to both
	 * generators has room for exactly one packet (the Box drains one segment per tick), so the first source
	 * in the list filled it and the second found {@code room == 0} — forever. The network's rotation cursor
	 * existed but was taken modulo the producer count, which pins it to 0 on the single-producer networks it
	 * was meant to help, and it was never handed to the charge pass at all.
	 *
	 * <p>Both generators are pre-charged and UNFUELLED, so each buffer is a one-way meter of what that
	 * generator actually contributed. The oracle is the pair: each must have given more than one segment
	 * fill, which no amount of first-tick luck can produce.
	 */
	public static void mod254EverySourceFeedsASaturatedLine(GameTestHelper helper) {
		// Default FACING (north) keeps each generator's inert front off the bus: the cable is EAST of gen A
		// and WEST of gen B, so both touch the line through a real output face.
		helper.setBlock(SAT_GEN_A, ModContent.GENERATOR.get());
		helper.setBlock(SAT_CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(SAT_GEN_B, ModContent.GENERATOR.get());
		// FACING = NORTH → the IN face is the cable at (2,64,1); the box is a pure sink that keeps the
		// single cable segment drained, so its room per tick is exactly one source's packet.
		helper.setBlock(SAT_BOX, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.NORTH));
		long genStart = Config.generatorBuffer;
		for (BlockPos g : new BlockPos[]{SAT_GEN_A, SAT_GEN_B}) {
			if (be(helper, g) instanceof GeneratorBlockEntity gen) {
				gen.getEnergyStorage().amount = genStart; // no fuel: the buffer can only go down
				gen.setChanged();
			}
		}
		if (be(helper, SAT_BOX) instanceof BatteryBoxBlockEntity bb) {
			bb.getEnergyStorage().amount = 0L;
		}
		for (int i = 0; i < 40; i++) {
			tick(helper, be(helper, SAT_GEN_A));
			tick(helper, be(helper, SAT_GEN_B));
			tick(helper, be(helper, SAT_CABLE));
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, SAT_BOX));
		}
		long drainA = genStart - (be(helper, SAT_GEN_A) instanceof GeneratorBlockEntity g ? g.getEnergyStorage().getAmount() : -1);
		long drainB = genStart - (be(helper, SAT_GEN_B) instanceof GeneratorBlockEntity g ? g.getEnergyStorage().getAmount() : -1);
		long oneFill = Config.cableBuffer;
		if (drainA <= oneFill || drainB <= oneFill) {
			helper.fail("a source on a saturated line was starved over 40 ticks: gen A gave " + drainA
					+ " EU, gen B gave " + drainB + " EU (one segment fill is " + oneFill + ") — the charge"
					+ " sweep still starts at the same source every tick, so whoever is later in the list"
					+ " only ever finds a full cable");
		}
		helper.succeed();
	}

	// A source in the middle of a bus with a consumer at each end — the fork the fair split exists for.
	// Own cell block (y=68) for the same shared-level reason.
	private static final BlockPos FORK_BOX_WEST = new BlockPos(1, 68, 1);
	private static final BlockPos[] FORK_CABLES = {
		new BlockPos(2, 68, 1), new BlockPos(3, 68, 1), new BlockPos(4, 68, 1),
		new BlockPos(5, 68, 1), new BlockPos(6, 68, 1),
	};
	private static final BlockPos FORK_GEN = new BlockPos(4, 68, 2);
	private static final BlockPos FORK_BOX_EAST = new BlockPos(7, 68, 1);

	/**
	 * MOD-254 (D3) — a forked buffer is SHARED between both branches, not taken whole by whichever branch
	 * the iteration reached first.
	 *
	 * <p>Deliberately measures the CABLES, not the consumers. Two consumers of the same class pool their
	 * touched cable buffers in the serve pass, so the west Battery Box can drink out of the east branch's
	 * last segment: a "both boxes charged" oracle stays green even when nothing ever flows down the west
	 * wire, which is why {@code mod252TwoConsumersOnBothSidesOfSource} could not catch this. Energy can only
	 * reach the cable next to the fork by being handed down the gradient, so a branch whose first segment
	 * never holds anything is a branch that was never served.
	 *
	 * <p>The oracle is the peak charge each branch's fork-side segment ever carried: with the buffer split
	 * both peak at their share, while the losing branch of a winner-takes-all sweep reads a flat zero for
	 * the whole run.
	 */
	public static void mod254ForkFeedsBothBranches(GameTestHelper helper) {
		// FACING = EAST → IN face at (2,68,1); FACING = WEST → IN face at (6,68,1).
		helper.setBlock(FORK_BOX_WEST, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.EAST));
		for (BlockPos c : FORK_CABLES) {
			helper.setBlock(c, ModContent.COPPER_CABLE.get());
		}
		// FACING = SOUTH so the generator's inert front points away from the bus and its north face — the
		// one touching the middle cable at (4,68,1) — is a real output. That middle cable is the fork.
		helper.setBlock(FORK_GEN, ModContent.GENERATOR.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.SOUTH));
		helper.setBlock(FORK_BOX_EAST, ModContent.BATTERY_BOX.get().defaultBlockState()
				.setValue(HorizontalMachineBlock.FACING, Direction.WEST));
		if (be(helper, FORK_GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
			gen.getEnergyStorage().amount = gen.getEnergyStorage().getCapacity();
		}
		for (BlockPos b : new BlockPos[]{FORK_BOX_WEST, FORK_BOX_EAST}) {
			if (be(helper, b) instanceof BatteryBoxBlockEntity bb) {
				bb.getEnergyStorage().amount = 0L;
			}
		}
		long peakWest = 0;
		long peakEast = 0;
		for (int i = 0; i < 120; i++) {
			tick(helper, be(helper, FORK_GEN));
			for (BlockPos c : FORK_CABLES) {
				tick(helper, be(helper, c));
			}
			NetworkManager.tickAll(helper.getLevel());
			tick(helper, be(helper, FORK_BOX_WEST));
			tick(helper, be(helper, FORK_BOX_EAST));
			// FORK_CABLES[1] and [3] are the two segments flanking the fork at [2].
			peakWest = Math.max(peakWest, cableAmount(helper, FORK_CABLES[1]));
			peakEast = Math.max(peakEast, cableAmount(helper, FORK_CABLES[3]));
		}
		long fairShare = Config.cableBuffer / 2;
		if (peakWest < fairShare || peakEast < fairShare) {
			helper.fail("the fork did not share its buffer: the segment west of the source peaked at "
					+ peakWest + " EU and the one east of it at " + peakEast + " EU, against a fair half"
					+ " segment of " + fairShare + " — one branch is taking the source's whole output and"
					+ " the other never carries anything");
		}
		long west = be(helper, FORK_BOX_WEST) instanceof BatteryBoxBlockEntity b ? b.getEnergyStorage().getAmount() : -1;
		long east = be(helper, FORK_BOX_EAST) instanceof BatteryBoxBlockEntity b ? b.getEnergyStorage().getAmount() : -1;
		if (west <= 0 || east <= 0) {
			helper.fail("both consumers must end with energy: west=" + west + " EU, east=" + east + " EU");
		}
		helper.succeed();
	}
}
