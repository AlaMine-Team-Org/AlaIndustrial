package dev.alaindustrial.core.net;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The roster of everything that holds transient server state, and the single sweep the loaders run
 * over it (MOD-401).
 *
 * <p>Before this class, {@code IndustrializationFabric} and {@code IndustrializationNeoForge} each
 * listed the managers to clear by name — and each listed two of the three. The fluid manager's
 * {@code clear}/{@code clearAll} existed and were called from nowhere, so an unloaded {@code
 * ServerLevel} stayed reachable as a map key for the rest of the process. The loaders now call
 * {@link #clearLevel} and {@link #clearAll} once each and name nobody, so a fourth network cannot be
 * added and left out: enrolment is a side effect of constructing the holder
 * ({@link GraphNetworkManager}'s constructor calls {@link #register}), not a line somebody has to
 * remember to add in two other files.
 *
 * <p><b>Why "not loaded" is safe.</b> A holder only appears here once its class is initialised — and
 * a class that was never initialised cannot be holding state either, because the state lives in its
 * own fields. So "absent from the roster" and "has nothing to clear" are the same condition.
 */
public final class LevelStateRegistry {

	/**
	 * Copy-on-write because enrolment happens on whichever thread first touches a manager class while
	 * the sweep runs on the server thread. The roster is a handful of entries written once each, so
	 * the copy cost is irrelevant and the alternative — a lock around the per-unload sweep — is not.
	 */
	private static final List<LevelStateHolder> HOLDERS = new CopyOnWriteArrayList<>();

	private LevelStateRegistry() {
	}

	/** Enrol a holder. Called from the holder's own constructor / static initialiser. */
	public static void register(LevelStateHolder holder) {
		HOLDERS.add(holder);
	}

	/** Drop {@code level} from every holder — the level-unload sweep, run by both loaders. */
	public static void clearLevel(Object level) {
		for (LevelStateHolder holder : HOLDERS) {
			holder.clearLevel(level);
		}
	}

	/** Drop everything from every holder — the server-stop sweep, run by both loaders. */
	public static void clearAll() {
		for (LevelStateHolder holder : HOLDERS) {
			holder.clearAll();
		}
	}

	/** The enrolled holders, for diagnostics and for the lifecycle L1 test. */
	public static List<LevelStateHolder> holders() {
		return List.copyOf(HOLDERS);
	}
}
