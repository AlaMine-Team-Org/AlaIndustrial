package dev.alaindustrial.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The Network Analyzer's two diagnostic modes (MOD-047). The mode rides on the tool itself as a
 * {@code network_analyzer_mode} data component so it survives hand swaps and relogs.
 *
 * <ul>
 *   <li><b>TRAVERSE</b> (default) — a storage sink (BatteryBox) is treated as part of the network:
 *       the analyzer walks through it and highlights every adjacent cable segment beyond it, even
 *       though those are separate {@link dev.alaindustrial.core.energy.EnergyNetwork} instances.</li>
 *   <li><b>STOP_AT_STORAGE</b> — the analyzer shows exactly the one {@code EnergyNetwork} the
 *       clicked cable belongs to and stops at the first storage sink (the original MOD-016
 *       behaviour), useful for segment-local diagnostics.</li>
 * </ul>
 *
 * <p>Mode is switchable with Shift + right-click in the air / off-network; right-clicking a cable
 * always scans in the currently selected mode.
 *
 * <p>Serialization: persistent coding uses the idiomatic 26.2 {@link StringRepresentable#fromEnum}
 * factory (returns a {@code StringRepresentable.EnumCodec} keyed by {@link #getSerializedName()});
 * {@code Codec.enumCodec(...)} does not exist in DFU 10.0.21, confirmed against the unpacked
 * sources. Network coding uses {@link ByteBufCodecs#idMapper} over the ordinal — also verified
 * against the 26.2 mappings.
 */
public enum AnalyzerMode implements StringRepresentable {
	/** Walk through storage sinks and highlight every connected cable segment (default). */
	TRAVERSE("traverse"),
	/** Stop at the first storage sink; show only the clicked cable's own EnergyNetwork. */
	STOP_AT_STORAGE("stop_at_storage");

	private final String serialName;

	AnalyzerMode(String serialName) {
		this.serialName = serialName;
	}

	@Override
	public String getSerializedName() {
		return serialName;
	}

	/** The next mode in the 1↔2 cycle, used by the Shift + right-click switch. */
	public AnalyzerMode next() {
		return this == TRAVERSE ? STOP_AT_STORAGE : TRAVERSE;
	}

	/** Persistent codec — the idiomatic 26.2 enum codec keyed by {@link #getSerializedName()}. */
	public static final Codec<AnalyzerMode> CODEC = StringRepresentable.fromEnum(AnalyzerMode::values);

	/** Network codec — codes by ordinal via the verified {@code idMapper} factory. */
	public static final StreamCodec<ByteBuf, AnalyzerMode> STREAM_CODEC = ByteBufCodecs.idMapper(
			i -> AnalyzerMode.values()[i], AnalyzerMode::ordinal);
}
