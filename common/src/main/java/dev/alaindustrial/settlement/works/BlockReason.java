package dev.alaindustrial.settlement.works;

import java.util.List;
import java.util.Objects;

/**
 * Why a job stands still, in words a player reads (MOD-857, epic principle «no job without its reason»): a
 * translation key and its arguments. Every argument is itself shown as a translation — an item or block
 * description id reads as the item's name in the player's language, and a plain number, which no language file
 * holds, reads as itself. {@code joined} puts all the arguments, comma-separated, into the key's one {@code %s}: the
 * «no materials: 12, Bricks, Glass» shape of a list of unknown length.
 *
 * <p>Minecraft-free on purpose: the queue and its L1 test carry it.
 *
 * @param key    the translation key of the sentence
 * @param args   its arguments, each shown as a translation
 * @param joined whether the arguments go, joined, into one placeholder
 */
public record BlockReason(String key, List<String> args, boolean joined) {

	/** «No materials: …» — the arguments list what is missing (MOD-860 fills them). */
	public static final String NO_MATERIALS = "settlement.alaindustrial.works.reason.no_materials";
	/** «No restoration plan» — the building's plan is not assembled yet (MOD-859). */
	public static final String NO_PLAN = "settlement.alaindustrial.works.reason.no_plan";
	/** «Chunk not loaded» — the next cell lies in a chunk nobody keeps loaded (R-277). */
	public static final String CHUNK_NOT_LOADED = "settlement.alaindustrial.works.reason.chunk_not_loaded";
	/** «Obstructed by …» — a block the job may not touch stands where the plan wants something else (R93). */
	public static final String OBSTRUCTED = "settlement.alaindustrial.works.reason.obstructed";
	/** «No energy» — the core or the executor's battery cannot pay for the next block (R94). */
	public static final String NO_ENERGY = "settlement.alaindustrial.works.reason.no_energy";

	/** «No way for the road» — no course from the town hall's inlet goes round the buildings (MOD-861). */
	public static final String NO_ROAD_COURSE = "settlement.alaindustrial.works.reason.no_road_course";

	/**
	 * «In the way: %1$s at %2$s. Take it away and the work goes on by itself» — a block the drone may not touch stands
	 * where the plan wants something else (owner's round 5 of 0.1.219: the reason always names the block and its
	 * cell). {@link #OBSTRUCTED} stays for reasons a save holds from before.
	 */
	public static final String OBSTRUCTED_AT = "settlement.alaindustrial.works.reason.obstructed_at";
	/**
	 * «The drone cannot reach %1$s» — no route through the air reaches the only cells left to do (owner's round 5 of
	 * 0.1.219); the job goes on by itself once a way is clear.
	 */
	public static final String NO_ROUTE = "settlement.alaindustrial.works.reason.no_route";

	/**
	 * «%1$s cannot be built at %2$s» — a building job found nothing to do in its stage though a cell of it is missing
	 * (round 5 of 0.1.219): it stops naming the cell rather than ending with the building half built.
	 */
	public static final String CELL_STUCK = "settlement.alaindustrial.works.reason.cell_stuck";

	/**
	 * The suffix of a reason's hint: what the player does about it (owner's round 1 of 0.1.219, R1-5) — «put what is
	 * missing into the buffer» under «no materials». Every reason of {@link #KEYS} has one.
	 */
	public static final String HINT = ".hint";

	/** The keys of the reasons this build words; a later stage adds its own. */
	public static final List<String> KEYS = List.of(NO_MATERIALS, NO_PLAN, CHUNK_NOT_LOADED, OBSTRUCTED, NO_ENERGY,
			NO_ROAD_COURSE, CELL_STUCK, OBSTRUCTED_AT, NO_ROUTE);

	/** The reasons a stop is told in chat once (owner's round 5 of 0.1.219): what is in the way, and where. */
	public static final List<String> TOLD = List.of(OBSTRUCTED_AT, NO_ROUTE, CELL_STUCK);

	public BlockReason {
		Objects.requireNonNull(key, "key");
		args = List.copyOf(args);
	}

	/** A reason with no arguments, or with arguments that fill the key's placeholders one by one. */
	public static BlockReason of(String key, String... args) {
		return new BlockReason(key, List.of(args), false);
	}

	/** «No materials: {@code missing}», the list joined into the key's one placeholder. */
	public static BlockReason noMaterials(List<String> missing) {
		return new BlockReason(NO_MATERIALS, missing, true);
	}

	/** «In the way: {@code blockDescriptionId} at x y z» ({@link #OBSTRUCTED_AT}). */
	public static BlockReason obstructed(String blockDescriptionId, int x, int y, int z) {
		return of(OBSTRUCTED_AT, blockDescriptionId, at(x, y, z));
	}

	/** «The drone cannot reach x y z» ({@link #NO_ROUTE}). */
	public static BlockReason noRoute(int x, int y, int z) {
		return of(NO_ROUTE, at(x, y, z));
	}

	/** A cell's coordinates as a reason shows them: «12 64 -5». */
	public static String at(int x, int y, int z) {
		return x + " " + y + " " + z;
	}

	/** «{@code blockDescriptionId} cannot be built at {@code where}» ({@link #CELL_STUCK}). */
	public static BlockReason cellStuck(String blockDescriptionId, String where) {
		return of(CELL_STUCK, blockDescriptionId, where);
	}
}
