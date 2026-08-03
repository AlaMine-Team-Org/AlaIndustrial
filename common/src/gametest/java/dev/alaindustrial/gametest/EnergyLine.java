package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.energy.NetworkManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * A builder for the shape almost every energy gametest needs — <b>source → cable(s) → consumer</b> —
 * and for the assertions that shape deserves (MOD-309).
 *
 * <p><b>The problem it solves is not verbosity.</b> Writing the line by hand is a dozen lines of
 * {@code setBlock} / {@code tick} / {@code getEnergyStorage}, but the real cost was that the cheap
 * assertion and the correct one are not the same assertion. A test that drives the line and then
 * checks only <i>"did the consumer receive EU?"</i> stays green even when the energy reaches the
 * consumer <b>bypassing the wire</b> — which is exactly the in-game bug MOD-070 fixed, where storage
 * was charged off-line and every cable sat at 0. A cable is a live buffer, not a teleport, so the
 * honest question is <i>"did the energy actually sit in the wire on its way?"</i>
 *
 * <p>So this builder samples every intermediate cable at the end of every driven tick and remembers
 * the highest buffer it saw. {@link #assertFlowedThroughCable()} asserts on that sample.
 *
 * <p><b>How strong that is depends on the source</b>, and the assertion's own javadoc says so: with a
 * generator it proves the wire is a live buffer; it becomes an oracle for the actual MOD-070 bypass
 * only with a storage source, which does not fill the line on its own.
 *
 * <p>Geometry: the line runs along +X at {@code y=2, z=1}, starting at the same
 * {@code (1, 2, 1)} the hand-written fixtures use — so a one-cable line is positionally identical to
 * the old {@code LINE_GEN}/{@code LINE_CABLE}/{@code LINE_MAC} rig. Keep the cable count small; the
 * gametest rigs are only a few blocks wide.
 *
 * <p>Example:
 * <pre>{@code
 * EnergyLine.in(helper)
 *         .fueledGenerator()
 *         .cables(1)
 *         .consumer(ModContent.MACERATOR.get(), new ItemStack(Items.RAW_IRON, 8))
 *         .build()
 *         .drive(120)
 *         .assertConsumerCharged()
 *         .assertFlowedThroughCable();
 * }</pre>
 */
public final class EnergyLine {

	/** First cell of the line — the source. Matches the legacy hand-written fixture position. */
	private static final BlockPos ORIGIN = new BlockPos(1, 2, 1);

	private final GameTestHelper helper;
	private final BlockPos sourcePos;
	private final List<BlockPos> cablePositions;
	private final BlockPos consumerPos;

	/** Highest buffer observed in ANY intermediate cable since the last {@link #rebaseline()}. */
	private long peakCableBuffer;
	/** Consumer charge at the last {@link #rebaseline()}, so gains are measured, not absolute levels. */
	private long consumerChargeAtBaseline;

	private EnergyLine(GameTestHelper helper, BlockPos sourcePos, List<BlockPos> cablePositions,
			BlockPos consumerPos) {
		this.helper = helper;
		this.sourcePos = sourcePos;
		this.cablePositions = List.copyOf(cablePositions);
		this.consumerPos = consumerPos;
		this.consumerChargeAtBaseline = consumerCharge();
	}

	/** Starts describing a line inside {@code helper}'s rig. */
	public static Builder in(GameTestHelper helper) {
		return new Builder(helper);
	}

	// ── building ─────────────────────────────────────────────────────────────────────────────────

	/** Describes the line; nothing is placed in the world until {@link #build()}. */
	public static final class Builder {
		private final GameTestHelper helper;
		private Block sourceBlock;
		private ItemStack fuel = ItemStack.EMPTY;
		private Block cableBlock;
		private int cableCount = 1;
		private Block consumerBlock;
		private ItemStack consumerInput = ItemStack.EMPTY;

		private Builder(GameTestHelper helper) {
			this.helper = helper;
		}

		/** A coal-burning generator — the default source, with a stack of fuel already inside. */
		public Builder fueledGenerator() {
			return generator(dev.alaindustrial.registry.ModContent.GENERATOR.get(),
					new ItemStack(Items.COAL, 64));
		}

		/** An arbitrary source block; {@code fuel} goes into a {@link GeneratorBlockEntity} fuel slot. */
		public Builder generator(Block block, ItemStack fuel) {
			this.sourceBlock = block;
			this.fuel = fuel;
			return this;
		}

		/** {@code count} copper cables between source and consumer. */
		public Builder cables(int count) {
			return cables(dev.alaindustrial.registry.ModContent.COPPER_CABLE.get(), count);
		}

		/** {@code count} cables of a specific grade (tin / copper / gold / insulated). */
		public Builder cables(Block block, int count) {
			if (count < 1) {
				throw new IllegalArgumentException("a line needs at least one cable, got " + count);
			}
			this.cableBlock = block;
			this.cableCount = count;
			return this;
		}

		/** The consumer at the far end; {@code input} is placed in its first slot when non-empty. */
		public Builder consumer(Block block, ItemStack input) {
			this.consumerBlock = block;
			this.consumerInput = input;
			return this;
		}

		/** Places every block and returns the driveable line. */
		public EnergyLine build() {
			if (sourceBlock == null || cableBlock == null || consumerBlock == null) {
				throw new IllegalStateException(
						"an energy line needs a source, at least one cable and a consumer");
			}
			BlockPos source = ORIGIN;
			helper.setBlock(source, sourceBlock);
			if (!fuel.isEmpty()
					&& EnergyScenarioSupport.be(helper, source) instanceof GeneratorBlockEntity gen) {
				gen.setItem(GeneratorBlockEntity.FUEL_SLOT, fuel);
			}

			List<BlockPos> cables = new ArrayList<>(cableCount);
			for (int i = 0; i < cableCount; i++) {
				BlockPos pos = source.offset(1 + i, 0, 0);
				helper.setBlock(pos, cableBlock);
				cables.add(pos);
			}

			BlockPos consumer = source.offset(1 + cableCount, 0, 0);
			helper.setBlock(consumer, consumerBlock);
			if (!consumerInput.isEmpty()
					&& EnergyScenarioSupport.be(helper, consumer) instanceof MachineBlockEntity machine) {
				machine.setItem(0, consumerInput);
			}
			return new EnergyLine(helper, source, cables, consumer);
		}
	}

	// ── driving ──────────────────────────────────────────────────────────────────────────────────

	/**
	 * Runs the line for {@code ticks} server ticks in the same order the game does — source, each
	 * cable, the network, then the consumer — sampling every cable's buffer on each tick.
	 */
	public EnergyLine drive(int ticks) {
		for (int i = 0; i < ticks; i++) {
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, sourcePos));
			for (BlockPos cable : cablePositions) {
				EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, cable));
			}
			NetworkManager.tickAll(helper.getLevel());
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, consumerPos));
			// One sample, at end of tick. An earlier attempt also sampled before the consumer ticks,
			// on the theory that the consumer could drain the segment dry within the tick — it cannot:
			// the cable→consumer transfer happens inside NetworkManager.tickAll above, and a machine's
			// own tick only spends its own buffer. That extra sample measured nothing and its comment
			// explained an effect that does not exist. The genuine mid-tick peak is inside tickAll and
			// is not observable from here at all.
			sampleCables();
		}
		return this;
	}

	private void sampleCables() {
		for (BlockPos pos : cablePositions) {
			if (EnergyScenarioSupport.be(helper, pos) instanceof CableBlockEntity cable) {
				peakCableBuffer = Math.max(peakCableBuffer, cable.getEnergyStorage().getAmount());
			}
		}
	}

	// ── assertions ───────────────────────────────────────────────────────────────────────────────

	/**
	 * Forgets everything observed so far: the consumer baseline is re-read and the cable peak is reset.
	 *
	 * <p><b>Call this before every topology change.</b> Both observations are CUMULATIVE across
	 * {@link #drive} calls, and that is a trap with teeth: in a break-and-rejoin test the peak is
	 * already non-zero from the first phase, so an {@link #assertFlowedThroughCable()} placed after the
	 * rejoin would pass on stale data even if the replaced cable carried nothing at all — a vacuous
	 * assertion that reads like a strong one. Same for {@link #assertConsumerCharged()} against a
	 * consumer whose charge was reset by hand mid-test.
	 */
	public EnergyLine rebaseline() {
		peakCableBuffer = 0;
		consumerChargeAtBaseline = consumerCharge();
		return this;
	}

	/** The consumer gained EU since the last {@link #rebaseline()} (or since the line was built). */
	public EnergyLine assertConsumerCharged() {
		long gained = consumerCharge() - consumerChargeAtBaseline;
		if (gained <= 0) {
			helper.fail("consumer received no EU over the line (charge " + consumerCharge()
					+ ", baseline " + consumerChargeAtBaseline + ")");
		}
		return this;
	}

	/**
	 * At least one intermediate cable held a non-zero buffer at some point while the line ran.
	 *
	 * <p><b>Read the strength of this carefully.</b> With a GENERATOR as the source it proves the wire
	 * is a live buffer and not a dead pass-through — but not much more, because a generator fills the
	 * line to capacity even with nothing drawing from it (see
	 * {@code CableEnergyScenarios.sourceFillsCableWithoutConsumer}). So on a generator fixture a
	 * non-zero peak is guaranteed regardless of how the consumer got its energy.
	 *
	 * <p>It becomes a real oracle for the MOD-070 failure mode — consumer charged off-network with
	 * every cable at 0 — only with a STORAGE source, which does not fill the wire on its own and
	 * discharges solely against a machine's deficit. Use it with a battery box when that is the
	 * regression you mean to guard.
	 */
	public EnergyLine assertFlowedThroughCable() {
		if (peakCableBuffer <= 0) {
			helper.fail("no cable in the line ever buffered EU: the consumer was charged BYPASSING "
					+ "the wire (the MOD-070 failure mode), or the line never carried anything");
		}
		return this;
	}

	/**
	 * No cable was ever observed above {@code cap} EU at end of tick. Throughput is bounded by the
	 * cable's own buffer, NOT by the tier voltage — see {@code Config.cableBuffer}; computing an
	 * expectation from {@code EnergyTier.maxVoltage()} is the standing trap here (MOD-070).
	 *
	 * <p>Caveat: this observes end-of-tick levels. A transient overshoot that happens and resolves
	 * inside {@code NetworkManager.tickAll} is invisible from a gametest driver and is not covered.
	 */
	public EnergyLine assertCableBufferAtMost(long cap) {
		if (peakCableBuffer > cap) {
			helper.fail("cable buffered " + peakCableBuffer + " EU, above the expected cap " + cap);
		}
		return this;
	}

	/** Highest cable buffer observed across the whole run — for assertions the helpers do not cover. */
	public long peakCableBuffer() {
		return peakCableBuffer;
	}

	/** Position of the n-th cable (0-based), for tests that need to poke a specific segment. */
	public BlockPos cablePos(int index) {
		return cablePositions.get(index);
	}

	/** Position of the consumer, for assertions on its inventory or progress. */
	public BlockPos consumerPos() {
		return consumerPos;
	}

	/** Position of the source. */
	public BlockPos sourcePos() {
		return sourcePos;
	}

	/**
	 * Current charge of the consumer. Fails the test outright when the consumer block entity is gone
	 * rather than returning a sentinel: a {@code -1} mixed into the arithmetic made
	 * {@code 0 - (-1) = 1} look like a gain, i.e. {@link #assertConsumerCharged()} would have passed
	 * vacuously against a consumer that is not even there.
	 */
	private long consumerCharge() {
		if (EnergyScenarioSupport.be(helper, consumerPos) instanceof MachineBlockEntity machine) {
			return machine.getEnergyStorage().getAmount();
		}
		helper.fail("no machine block entity at the consumer position " + consumerPos
				+ " — the line is not built, or the block was removed");
		return 0;
	}
}
