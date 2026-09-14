package dev.alaindustrial.core;

import org.jspecify.annotations.Nullable;

/**
 * When a snapshot for one open screen goes out (MOD-620): at most once per interval, and only if it differs
 * from the one sent last.
 *
 * <p>Riding a menu's {@code broadcastChanges} already keeps a closed screen silent — vanilla stops calling it
 * the moment the screen closes. This is the other half of the promise, and it is Minecraft-free so it can be
 * tested rather than trusted: a snapshot is not even built between two due ticks, and an unchanged one is not
 * sent.
 *
 * <p>The first tick is due, so an opened screen is filled at once instead of an interval later.
 */
public final class ThrottledSnapshot<T> {

	private final int interval;
	private int ticks;
	private @Nullable T last;

	public ThrottledSnapshot(int interval) {
		this.interval = Math.max(1, interval);
		this.ticks = this.interval - 1;
	}

	/** Called once per tick: whether a snapshot is due now. Nothing needs building when it is not. */
	public boolean due() {
		if (++ticks < interval) {
			return false;
		}
		ticks = 0;
		return true;
	}

	/** Whether {@code next} differs from the snapshot last accepted; remembers it when it does. */
	public boolean changed(T next) {
		if (next.equals(last)) {
			return false;
		}
		last = next;
		return true;
	}
}
