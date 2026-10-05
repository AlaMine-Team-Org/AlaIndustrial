package dev.alaindustrial.item.tool;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.ToolConfig;
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
 * reach a tier resolved at class-init.
 *
 * <p>The diamond tips share their base line's numbers; the netherite drill has a buffer of its own and
 * the base drill's intake and cost (MOD-534). The drill's column price
 * ({@code ToolConfig.electricDrillColumnEuPerBlock}) is a feature of the drill, not of a tier, and stays there.
 */
public enum ElectricToolTier {
	DRILL(() -> ToolConfig.electricDrillBuffer, () -> ToolConfig.electricDrillInputRate,
			() -> ToolConfig.electricDrillEuPerBlock),
	DRILL_NETHERITE_TIP(() -> ToolConfig.electricDrillNetheriteTipBuffer, () -> ToolConfig.electricDrillInputRate,
			() -> ToolConfig.electricDrillEuPerBlock),
	CHAINSAW(() -> ToolConfig.electricChainsawBuffer, () -> ToolConfig.electricChainsawInputRate,
			() -> ToolConfig.electricChainsawEuPerBlock),
	SHOVEL(() -> ToolConfig.electricShovelBuffer, () -> ToolConfig.electricShovelInputRate,
			() -> ToolConfig.electricShovelEuPerBlock),
	HOE(() -> ToolConfig.electricHoeBuffer, () -> ToolConfig.electricHoeInputRate,
			() -> ToolConfig.electricHoeEuPerBlock);

	private final IntSupplier buffer;
	private final IntSupplier inputRate;
	private final IntSupplier euPerBlock;

	ElectricToolTier(IntSupplier buffer, IntSupplier inputRate, IntSupplier euPerBlock) {
		this.buffer = buffer;
		this.inputRate = inputRate;
		this.euPerBlock = euPerBlock;
	}

	/** EU the tool holds. */
	public int buffer() {
		return buffer.getAsInt();
	}

	/** Max EU/tick it accepts while charging. */
	public int inputRate() {
		return inputRate.getAsInt();
	}

	/** EU spent per block of non-zero hardness broken at powered speed. */
	public int euPerBlock() {
		return euPerBlock.getAsInt();
	}
}
