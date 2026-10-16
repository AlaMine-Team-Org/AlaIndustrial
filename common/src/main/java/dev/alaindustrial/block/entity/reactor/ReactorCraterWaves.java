package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.core.net.LevelStateHolder;
import dev.alaindustrial.core.net.LevelStateRegistry;
import dev.alaindustrial.core.reactor.ReactorConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/**
 * The craters still being carved (MOD-736): a cascade larger than {@code reactorCraterWavesPerTick} does not fit the
 * blast's own tick, so the rest outlives the controller that started it and is driven from the server tick.
 *
 * <p><b>Not saved, deliberately.</b> A cascade lasts seconds; a server stopped in the middle of one keeps the
 * part already carved and loses the rest, lava and rim included. Persisting a half-made hole is not worth a
 * save format. A level unload and a server stop drop the craters through {@link LevelStateRegistry}.
 */
public final class ReactorCraterWaves {

	private static final List<ReactorCrater> ACTIVE = new ArrayList<>();

	static {
		LevelStateRegistry.register(new LevelStateHolder() {
			@Override
			public String stateId() {
				return "reactor-craters";
			}

			@Override
			public void clearLevel(Object level) {
				ACTIVE.removeIf(crater -> crater.level() == level);
			}

			@Override
			public void clearAll() {
				ACTIVE.clear();
			}

			@Override
			public int retained() {
				return ACTIVE.size();
			}
		});
	}

	private ReactorCraterWaves() {
	}

	/** Hands a crater whose primary blast has gone off to the server tick, which sets off its waves. */
	public static void start(ReactorCrater crater) {
		ACTIVE.add(crater);
	}

	/** One server tick of every crater in {@code level}: a few waves each, and the aftermath once a plan is spent. */
	public static void tick(ServerLevel level) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		// A copy: a wave can take another reactor's controller with it, and nothing stops that one from
		// starting a crater of its own while this loop runs.
		for (ReactorCrater crater : List.copyOf(ACTIVE)) {
			if (crater.level() == level && crater.step(ReactorConfig.reactorCraterWavesPerTick)) {
				ACTIVE.remove(crater);
				crater.finish(true);
			}
		}
	}
}
