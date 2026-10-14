package dev.alaindustrial.item.tool;

import dev.alaindustrial.client.ServerBalance;
import dev.alaindustrial.item.ToolConfig;
import dev.alaindustrial.item.energy.ItemBuffers;
import java.util.function.IntSupplier;

/**
 * The energy numbers of the electric mining tools, per tool line and tier (MOD-707): buffer, intake and
 * EU per block. The same shape as {@link MagnetTier} and {@code ScytheTiers}: one place that says which
 * {@code Config} knob a tool reads, instead of a {@code Config.electric*} read in every method.
 *
 * <p>Only the three energy numbers live here. Mining speed and enchantability stay in each tier's
 * {@code Properties} builder, whose full copies are deliberate (see their javadoc).
 *
 * <p>Values are {@link IntSupplier}s because {@code Config} is mutable at runtime: a reloaded config must
 * reach a tier resolved at class-init. The buffer is picked out of an {@link ItemBuffers} instead (MOD-790): the
 * client draws it from the server's numbers.
 *
 * <p>The diamond tips share their base line's numbers; the netherite drill has a buffer of its own and
 * the base drill's intake and cost (MOD-534). The drill's column price
 * ({@code ToolConfig.electricDrillColumnEuPerBlock}) is a feature of the drill, not of a tier, and stays there.
 */
public enum ElectricToolTier {
	DRILL(() -> ToolConfig.electricDrillInputRate, () -> ToolConfig.electricDrillEuPerBlock),
	DRILL_NETHERITE_TIP(() -> ToolConfig.electricDrillInputRate, () -> ToolConfig.electricDrillEuPerBlock),
	CHAINSAW(() -> ToolConfig.electricChainsawInputRate, () -> ToolConfig.electricChainsawEuPerBlock),
	SHOVEL(() -> ToolConfig.electricShovelInputRate, () -> ToolConfig.electricShovelEuPerBlock),
	HOE(() -> ToolConfig.electricHoeInputRate, () -> ToolConfig.electricHoeEuPerBlock);

	private final IntSupplier inputRate;
	private final IntSupplier euPerBlock;

	ElectricToolTier(IntSupplier inputRate, IntSupplier euPerBlock) {
		this.inputRate = inputRate;
		this.euPerBlock = euPerBlock;
	}

	/** EU the tool holds, by this side's own balance. */
	public int buffer() {
		return buffer(ItemBuffers.live());
	}

	/**
	 * EU the tool holds when the buffers are {@code buffers} (MOD-790): picked by the tier, no supplier — a client
	 * drawing the bar passes the server's buffers, and a supplier field would tie it to the enum's initialiser,
	 * where every knob of the class is read.
	 */
	public int buffer(ItemBuffers buffers) {
		return switch (this) {
			case DRILL -> buffers.electricDrillBuffer();
			case DRILL_NETHERITE_TIP -> buffers.electricDrillNetheriteTipBuffer();
			case CHAINSAW -> buffers.electricChainsawBuffer();
			case SHOVEL -> buffers.electricShovelBuffer();
			case HOE -> buffers.electricHoeBuffer();
		};
	}

	/** Max EU/tick it accepts while charging. */
	public int inputRate() {
		return inputRate.getAsInt();
	}

	/** EU spent per block of non-zero hardness broken at powered speed, by this side's own balance. */
	public int euPerBlock() {
		return euPerBlock.getAsInt();
	}

	/**
	 * The same price as the client PREDICTS with it (MOD-798): the server's, through {@link ServerBalance}, or this
	 * side's own until a server has sent them (so on a dedicated server it is the {@link #euPerBlock} price). A
	 * switch on the tier and no supplier, for the reason {@link #buffer(ItemBuffers)} is one: the client-reach rule
	 * would follow a supplier field into the initialiser, where every knob of the enum is read.
	 */
	public int predictedEuPerBlock() {
		return switch (this) {
			case DRILL, DRILL_NETHERITE_TIP -> ServerBalance.electricDrillEuPerBlock();
			case CHAINSAW -> ServerBalance.electricChainsawEuPerBlock();
			case SHOVEL -> ServerBalance.electricShovelEuPerBlock();
			case HOE -> ServerBalance.electricHoeEuPerBlock();
		};
	}
}
