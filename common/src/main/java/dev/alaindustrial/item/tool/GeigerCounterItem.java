package dev.alaindustrial.item.tool;

import net.minecraft.world.item.Item;

/**
 * Geiger counter (MOD-475) — the instrument that makes radiation audible.
 *
 * <p><b>It has no behaviour of its own, and that is the design.</b> Carrying it anywhere in the
 * inventory is the whole interaction: no right-click, no toggle, no screen. The reading is produced by
 * {@code GeigerTicker}, which spends a step decided once a second by the radiation sweep — the sweep
 * already knows the field, so the instrument costs no second pass over the world.
 *
 * <p><b>Sound is the only channel</b>, which makes the subtitle load-bearing: it is the one thing that
 * tells a player whose clicking they are hearing. A player with subtitles off gets the warning but not
 * the attribution — an accepted limit, recorded here so it is a decision rather than a surprise.
 *
 * <p>The counter is deliberately more sensitive than harm. It hears uranium ore through rock, which
 * cannot build a dose at all, so occasional clicks mean "there is something here" rather than "you are
 * in danger"; silence means "there is nothing at all". Above {@code geigerOffScaleThreshold} it stops
 * telling levels apart and simply rattles — a cheap instrument that saturates is honest, and that
 * ceiling is what the dosimeter (MOD-567) exists to read past.
 */
public class GeigerCounterItem extends Item {

	public GeigerCounterItem(Properties properties) {
		super(properties);
	}
}
