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

	/** The keys of the reasons this build words; a later stage adds its own. */
	public static final List<String> KEYS = List.of(NO_MATERIALS, NO_PLAN, CHUNK_NOT_LOADED, OBSTRUCTED, NO_ENERGY);

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

	/** «Obstructed by {@code blockDescriptionId}». */
	public static BlockReason obstructed(String blockDescriptionId) {
		return of(OBSTRUCTED, blockDescriptionId);
	}
}
