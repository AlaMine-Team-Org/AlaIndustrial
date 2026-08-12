package dev.alaindustrial.core.net;

/**
 * A holder of transient server state that must be dropped when the world it belongs to goes away
 * (MOD-401). Everything implementing this interface enrols itself in {@link LevelStateRegistry}, and
 * the two loader entry points clear the whole roster with a single call instead of naming holders
 * one by one.
 *
 * <p><b>Why the contract exists at all.</b> The three network managers each kept a
 * {@code Map<ServerLevel, …>} and each needed a matching pair of calls in
 * {@code IndustrializationFabric} and {@code IndustrializationNeoForge}. Fluid pipes had the map and
 * the clearing methods but nobody ever called them, so every unloaded world stayed reachable through
 * the map key — in single player, leaving to the menu leaked a whole level with its chunks and block
 * entities. The by-name list in the loaders is what made that possible: adding a manager and
 * forgetting the two lines was a silent, invisible defect. Enrolment happens in the holder's own
 * constructor / static initialiser, so it cannot be forgotten.
 *
 * <p><b>Levels are not the only key.</b> {@code TeleportWarmupManager} keys its state by player UUID
 * rather than by level; its {@link #clearLevel} is a no-op and only {@link #clearAll} does work. The
 * contract is "transient state with a server lifetime", and the level is simply the most common key.
 */
public interface LevelStateHolder {

	/** Short stable name for diagnostics and test failure messages (e.g. {@code "energy"}). */
	String stateId();

	/**
	 * Drop the state belonging to one level. The key is compared the way the holder stores it (the
	 * network managers use identity), so passing a level this holder never saw is a no-op.
	 *
	 * <p>Typed as {@code Object} on purpose: the roster is heterogeneous, and every implementation
	 * simply removes the key from its own map — an operation {@link java.util.Map#remove(Object)}
	 * already accepts untyped. That keeps the registry free of a type parameter it could not carry.
	 */
	void clearLevel(Object level);

	/** Drop every level's state (server stop). After this, {@link #retained()} must report 0. */
	void clearAll();

	/**
	 * How many entries of transient state this holder currently keeps — levels for the network
	 * managers, pending warmups plus cooldowns for the teleporter. The lifecycle L1 test asserts this
	 * is non-zero before the sweep and zero after it, so a holder whose {@code clearAll} silently does
	 * nothing cannot pass.
	 */
	int retained();
}
