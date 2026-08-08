package dev.alaindustrial.gametest.visual;

import dev.alaindustrial.menu.MachineMenu;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A machine screen's state, described by name instead of by argument position.
 *
 * <p>Before MOD-362 a frame was requested like this:
 *
 * <pre>shootSolarPanel(context, "gui_solar_panel_partial", 2000, 8000, 1, 3, 0, 33600);</pre>
 *
 * Only the author knew that {@code 1} is "the sun dot is on" and {@code 3} is "partial shade", and
 * nothing stopped two of those seven numbers from being swapped — the frame would still render, just
 * showing the wrong thing, and a human reviewing 148 screenshots would never notice. The same state
 * now reads:
 *
 * <pre>MenuState.of().energy(2000, 8000).channel(2, 1).channel(3, 3)</pre>
 *
 * <p>Values still go in through the {@code injectTestData} / {@code injectTestChannel} hooks on
 * {@link MachineMenu}: a client-side menu has no block entity behind it, so its {@code ContainerData}
 * has to be filled directly.
 */
public final class MenuState {

    private int energy;
    private int capacity;
    private int progress;
    private int maxProgress;
    /** Extra channels past the shared four, in insertion order so failures read predictably. */
    private final Map<Integer, Integer> extraChannels = new LinkedHashMap<>();

    private MenuState() {
    }

    public static MenuState of() {
        return new MenuState();
    }

    /** An idle machine: no charge, no work in progress. */
    public static MenuState empty(int capacity) {
        return of().energy(0, capacity);
    }

    /** A machine mid-job at the given fraction of both its buffer and its progress bar. */
    public static MenuState working(int capacity, int maxProgress, double fraction) {
        if (fraction < 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("fraction must be 0..1, got " + fraction);
        }
        return of()
                .energy((int) Math.round(capacity * fraction), capacity)
                .progress((int) Math.round(maxProgress * fraction), maxProgress);
    }

    /** Everything at maximum — the state where bars must render full without overflowing their track. */
    public static MenuState full(int capacity, int maxProgress) {
        return of().energy(capacity, capacity).progress(maxProgress, maxProgress);
    }

    public MenuState energy(int energy, int capacity) {
        this.energy = energy;
        this.capacity = capacity;
        return this;
    }

    public MenuState progress(int progress, int maxProgress) {
        this.progress = progress;
        this.maxProgress = maxProgress;
        return this;
    }

    /**
     * One machine-specific {@code ContainerData} channel past the shared four.
     *
     * <p>Channel meanings are per-machine and documented on each menu class (the incubator's mode,
     * charge and multiblock flag; the pump's fluid permille and registry id; the wind mill's wind mode).
     */
    public MenuState channel(int index, int value) {
        if (index < 4) {
            throw new IllegalArgumentException("channels 0..3 are energy/capacity/progress/maxProgress — "
                    + "set them with energy()/progress(), not channel(" + index + ")");
        }
        extraChannels.put(index, value);
        return this;
    }

    /** Pushes this state into a live client-side menu. */
    public void applyTo(MachineMenu menu) {
        menu.injectTestData(energy, capacity, progress, maxProgress);
        extraChannels.forEach(menu::injectTestChannel);
    }

    public int energy() {
        return energy;
    }

    public int capacity() {
        return capacity;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("energy=").append(energy).append('/').append(capacity)
                .append(" progress=").append(progress).append('/').append(maxProgress);
        extraChannels.forEach((index, value) -> sb.append(" ch").append(index).append('=').append(value));
        return sb.toString();
    }
}
