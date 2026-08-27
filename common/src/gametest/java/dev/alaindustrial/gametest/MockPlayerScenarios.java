package dev.alaindustrial.gametest;

import dev.alaindustrial.item.energy.ItemEnergy;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * L2 guard for the mock players every other suite is built on (MOD-500).
 *
 * <p><b>Why this suite exists.</b> {@link AlaGameTestHelper#mockPlayerInLevel} wraps a vanilla call
 * marked {@code @Deprecated(forRemoval = true)}, so it will have to be rewritten the day Mojang
 * drops it. Two properties of that mock are load-bearing for roughly a hundred scenarios, and both
 * are the kind that break silently — a suite keeps passing while its assertions stop meaning
 * anything. Nothing asserted them before this class: they lived in prose, in three javadocs.
 *
 * <p>Whoever reimplements the wrapper gets these two tests as the contract to reimplement against.
 */
public final class MockPlayerScenarios {

	private MockPlayerScenarios() {}

	/**
	 * The in-level mock is a real member of the level — the whole reason the deprecated vanilla call
	 * is kept instead of the undeprecated {@code makeMockServerPlayer(GameType)}.
	 *
	 * <p>That neighbour builds a detached player: no connection, never placed in the player list. Menu
	 * opening, packet sends and entity lookups all fail on it. A future migration that quietly swaps
	 * one for the other would not break compilation — it would break {@code openMenu} in a dozen
	 * suites at once, far from the edit. This reddens instead.
	 */
	public static void inLevelMockIsWiredIntoTheLevel(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.mockPlayerInLevel(helper);

		if (player.connection == null) {
			helper.fail("the in-level mock has no connection — openMenu and every packet send would "
					+ "NPE; it was built detached instead of placed in the level");
		}
		if (!helper.getLevel().players().contains(player)) {
			helper.fail("the in-level mock is not in the level's player list — entity queries and "
					+ "owner lookups will not find it");
		}
		helper.succeed();
	}

	/**
	 * A survival mock reports CREATIVE and is billed anyway — the trap documented in MOD-081, asserted.
	 *
	 * <p>The vanilla mock overrides {@code gameMode()} to an unconditional CREATIVE that
	 * {@code setGameMode} cannot undo, so {@code isCreative()} stays true forever. The half the mod
	 * controls is {@code Abilities}, and every rule that matters — EU spend, tool wear, block drops —
	 * reads {@code instabuild} through {@code hasInfiniteMaterials()}. If somebody ever "fixes" a
	 * replacement mock to report SURVIVAL honestly, that is fine; if instead they let {@code instabuild}
	 * stay on, every EU assertion in the repository goes vacuous while staying green.
	 *
	 * <p>The third block is the anti-vacuity proof: flipping {@code instabuild} back on must flip the
	 * verdict. Without it this test would still pass against an {@code ItemEnergy.free} that had been
	 * hardwired to {@code false} and stopped reading abilities at all.
	 */
	public static void survivalMockIsBilledDespiteReportingCreative(GameTestHelper helper) {
		ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);

		if (!player.isCreative()) {
			helper.fail("the survival mock reports non-creative, so the vanilla gameMode() override is "
					+ "gone. That is not a failure by itself — but ItemEnergy and every drop/wear rule "
					+ "were written around it, so re-check them before pinning the new behaviour here");
		}
		if (ItemEnergy.free(player)) {
			helper.fail("the survival mock is treated as creative for EU: abilities.instabuild is still "
					+ "on, which silently disables every EU, durability and drop assertion in the suite");
		}

		player.getAbilities().instabuild = true;
		if (!ItemEnergy.free(player)) {
			helper.fail("ItemEnergy.free ignored abilities.instabuild — the check above proves nothing, "
					+ "because it would pass no matter what the mock's abilities said");
		}
		player.getAbilities().instabuild = false;
		helper.succeed();
	}
}
