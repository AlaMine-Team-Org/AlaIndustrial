package dev.alaindustrial.core.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A segment of the energy network's cable graph, as the network core sees it (MOD-715, CORE-3): its live
 * buffer and its grade. {@link NetworkManager}, {@link EnergyNetwork} and {@link EnergyTopologyCache} ask
 * this interface and never a block-entity class, so a new kind of segment — a cable and a pipe in one
 * block (MOD-320) — implements it instead of editing the core.
 *
 * <p>{@link #getLevel()} and {@link #getBlockPos()} are the block entity's own methods; they are what the
 * manager files the node under. The core layer rule (MOD-715) keeps {@code core} from naming
 * {@code dev.alaindustrial.block..}, so this is the only door the network has into a cable.
 */
public interface CableNode {

	/** The segment's live buffer: the real throughput between two points of the line (ADR-001). */
	default EnergyBuffer lineBuffer() {
		return getEnergyStorage();
	}

	/** The block entity's own buffer; {@link #lineBuffer()} is the name the network reads it by. */
	EnergyBuffer getEnergyStorage();

	/** The segment's grade; a network runs at its strongest (MOD-219). */
	CableType cableType();

	/** The level the segment stands in, or {@code null} before it has one. */
	@Nullable
	Level getLevel();

	/** Where the segment stands. */
	BlockPos getBlockPos();
}
