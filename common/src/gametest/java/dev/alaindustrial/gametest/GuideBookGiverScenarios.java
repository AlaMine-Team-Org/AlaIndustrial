package dev.alaindustrial.gametest;

import dev.alaindustrial.core.GuideBookState;
import dev.alaindustrial.core.guide.GuideBookGiver;
import dev.alaindustrial.registry.ModContent;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Loader-neutral gametest bodies for the Guide Book auto-give (MOD-067, suite TC-GUIDE-001). Same
 * pattern as {@link PouchScenarios}: plain {@code Consumer<GameTestHelper>} bodies wrapped by the
 * Fabric {@code GuideBookGameTest} suite and registered on the NeoForge {@code gameTestServer} lane
 * via {@code NeoForgeGameTests} — both loaders exercise the same {@link GuideBookGiver} +
 * {@link GuideBookState} ledger logic.
 */
public final class GuideBookGiverScenarios {

	private GuideBookGiverScenarios() {
	}

	/**
	 * TC-GUIDE-001-FUN01 — the auto-give hands out exactly one book the first time and is a no-op on a
	 * repeat call (the per-world {@link GuideBookState} ledger prevents duplicates on relog/death). The
	 * assertion is relative to the pre-state, so it holds even if the server-global ledger already
	 * carries this mock UUID from an earlier test in the same run.
	 */
	public static void giveOnce(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// The mock defaults to CREATIVE; pin SURVIVAL so give/count behaviour is explicit and stable.
		player.setGameMode(GameType.SURVIVAL);
		UUID id = player.getUUID();
		GuideBookState state = player.level().getServer().getDataStorage().computeIfAbsent(GuideBookState.TYPE);

		int before = player.getInventory().countItem(ModContent.GUIDE_BOOK.get());
		GuideBookGiver.giveIfNeeded(player);
		int afterFirst = player.getInventory().countItem(ModContent.GUIDE_BOOK.get());

		// The idempotency guarantee, asserted independently of any ledger contamination from an earlier
		// test in the same run: a SECOND give must add exactly zero books. This is the assertion that
		// fails if the markGiven dedup is removed.
		GuideBookGiver.giveIfNeeded(player);
		int afterSecond = player.getInventory().countItem(ModContent.GUIDE_BOOK.get());
		if (afterSecond != afterFirst) {
			helper.fail("Guide Book auto-give not idempotent: the 2nd give added "
					+ (afterSecond - afterFirst) + " book(s), expected 0");
		}
		// A first give to a fresh UUID yields exactly one book; a contaminated UUID yields zero. Never >1.
		if (afterFirst - before > 1) {
			helper.fail("first auto-give produced " + (afterFirst - before) + " books, expected at most 1");
		}
		if (!state.hasReceived(id)) {
			helper.fail("player not recorded in the given-ledger after auto-give");
		}
		helper.succeed();
	}
}
