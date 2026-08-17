package dev.alaindustrial.menu;

/**
 * The two synced readouts a wind-mill screen draws: the wind mode and the effective production rate.
 * Implemented by the T2 wind-mill menus so {@code AbstractT2WindMillScreen} can be written once against
 * this surface instead of once per menu class (MOD-439). The menu classes themselves stay distinct — the
 * menu&#8594;screen manifest pins one class per menu type on purpose (see {@code MenuScreenManifest}).
 */
public interface WindMillReadout {
	/** Wind mode code — one of {@code WindMillBlockEntity.MODE_*}. */
	int getMode();

	/** Effective production rate (EU/t) — what the buffer gains while it has room. */
	int getProductionRate();
}
