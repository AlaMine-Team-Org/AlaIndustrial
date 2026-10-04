package dev.alaindustrial.gametest;

import java.util.List;

/**
 * Test-side window onto the reviewed creative-tab reference (MOD-711, batch 0b).
 *
 * <p><b>Why this class exists, and why it sits in the {@code gametest} package.</b> The reference
 * {@link CreativeTabSnapshot} lives in the {@code gametest} source set and is package-private on purpose:
 * its update command rewrites the file from a template that spells those modifiers out, so widening them
 * would mean editing the writer too. {@code :common}'s test source set already sees the gametest output
 * (see {@code common/build.gradle}, the {@code sourceSets.test} block), so the L1 lane can read the very
 * object both loaders' scenarios compare against — no text parsing of a Java file, no second copy of the
 * 576 lines. A same-package class is the one place that can touch the package-private members; it only
 * re-exports them, and holds no data of its own.
 */
public final class CreativeTabSnapshotView {

	private CreativeTabSnapshotView() {}

	/** Whether the reference has ever been captured; {@code false} means {@link #lines()} is no reference yet. */
	public static boolean captured() {
		return CreativeTabSnapshot.CAPTURED;
	}

	/** The reference lines, in order: {@code <tab> <id>} or {@code <tab> after <anchor id>: <ids>}. */
	public static List<String> lines() {
		return CreativeTabSnapshot.LINES;
	}
}
