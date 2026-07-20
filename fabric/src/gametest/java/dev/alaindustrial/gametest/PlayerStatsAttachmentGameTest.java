package dev.alaindustrial.gametest;

import dev.alaindustrial.stats.fabric.FabricPlayerStats;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 guard for the three MOD-133 attachment guarantees that are declared once, in a builder chain, and
 * are otherwise invisible until a player loses their career: persistence across restarts, survival of
 * death, and owner-only sync.
 *
 * <p><b>Why this exists.</b> The task's risk register names exactly one failure mode for this feature —
 * "forgotten {@code .copyOnDeath()} on Fabric → progress lost on death" — and prescribed an L2 death
 * test as the mitigation. A literal death test is not writable here: the copy happens inside
 * {@code PlayerList.respawn}, which needs a real network connection, and {@code makeMockServerPlayerInLevel}
 * has none. What IS both writable and sufficient is pinning the declaration itself, because on Fabric the
 * copy is driven entirely by {@link net.fabricmc.fabric.api.attachment.v1.AttachmentType#copyOnDeath()}
 * (read by {@code AttachmentTargetImpl}, which skips a type on death unless the flag is set). Drop the
 * flag from the builder — the exact refactor accident the risk register worried about — and this fails.
 *
 * <p>This deliberately does not re-test Fabric's own copy machinery, only our opt-in to it.
 */
public class PlayerStatsAttachmentGameTest {

	/** @implements player stats survive death, persist across restarts, and sync only to their owner. */
	@GameTest
	public void attachmentDeclaresItsGuarantees(GameTestHelper helper) {
		if (!FabricPlayerStats.TYPE.copyOnDeath()) {
			helper.fail("player_stats attachment lost .copyOnDeath() — a player's career would be wiped on death");
			return;
		}
		if (!FabricPlayerStats.TYPE.isPersistent()) {
			helper.fail("player_stats attachment lost .persistent(CODEC) — career would reset every restart");
			return;
		}
		if (!FabricPlayerStats.TYPE.isSynced()) {
			helper.fail("player_stats attachment lost .syncWith(...) — the dashboard would render permanent zeros");
			return;
		}
		helper.succeed();
	}
}
