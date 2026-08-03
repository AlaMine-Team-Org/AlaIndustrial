package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * L2 suite for the point list and the screens' server side (MOD-093).
 *
 * <p>The rules here are what stands between a crafted packet and a player's data: index bounds, the
 * name cap, the list limit, and who may flip a station's privacy. The screens themselves (widgets,
 * layout) are checked by hand in the client — what is tested here is everything a malicious client
 * could aim at.
 *
 * <p><b>MOD-310 — the scenario bodies live in {@link TeleporterGuiScenarios}
 * ({@code common/src/gametest}).</b>
 * What stays here is the Fabric wiring only: the {@code @GameTest} annotation and a delegation, so
 * the SAME scenario runs on BOTH loaders — NeoForge registers it in {@code NeoForgeGameTests}.
 */
public class TeleporterGuiGameTest {

	@GameTest
	public void tcTele003Sec01_indexBoundsAreExact(GameTestHelper helper) {
		TeleporterGuiScenarios.tcTele003Sec01_indexBoundsAreExact(helper);
	}

	@GameTest
	public void tcTele003Sec02_renameClampsName(GameTestHelper helper) {
		TeleporterGuiScenarios.tcTele003Sec02_renameClampsName(helper);
	}

	@GameTest
	public void tcTele003Sec04_wireCarriesLegacyLengthNames(GameTestHelper helper) {
		TeleporterGuiScenarios.tcTele003Sec04_wireCarriesLegacyLengthNames(helper);
	}

	@GameTest
	public void tcTele003Fun01_listIsImmutable(GameTestHelper helper) {
		TeleporterGuiScenarios.tcTele003Fun01_listIsImmutable(helper);
	}

	@GameTest
	public void tcTele003Fun02_dedupeByDimAndPos(GameTestHelper helper) {
		TeleporterGuiScenarios.tcTele003Fun02_dedupeByDimAndPos(helper);
	}

	@GameTest
	public void tcTele003Fun03_limitIsTheConfiguredOne(GameTestHelper helper) {
		TeleporterGuiScenarios.tcTele003Fun03_limitIsTheConfiguredOne(helper);
	}

	@GameTest
	public void tcTele003Fun05_autoNamesTakeTheLowestFreeNumber(GameTestHelper helper) {
		TeleporterGuiScenarios.tcTele003Fun05_autoNamesTakeTheLowestFreeNumber(helper);
	}

	@GameTest
	public void tcTele003Sec03_onlyOwnerTogglesPrivacy(GameTestHelper helper) {
		TeleporterGuiScenarios.tcTele003Sec03_onlyOwnerTogglesPrivacy(helper);
	}

	@GameTest
	public void tcTele003Fun04_menuReadsTheHeldRemote(GameTestHelper helper) {
		TeleporterGuiScenarios.tcTele003Fun04_menuReadsTheHeldRemote(helper);
	}
}
