package dev.alaindustrial.settlement.works;

/**
 * Where a builder takes the items it places (MOD-860, epic decision R84): for the town hall, the internal buffer of the
 * town's dispatcher (MOD-858) and nothing else — never a chest of the town. Items are named by their registry id
 * ({@code minecraft:bricks}), so the rules that read a source stay Minecraft-free.
 *
 * <p><b>An item leaves the source when its block is placed</b>, not when a drone loads it: the drone carries a promise,
 * and an unloaded world in mid-flight neither duplicates nor loses anything. The caller checks {@link #count}, places
 * the block and calls {@link #consume} in the same server tick ({@link BuildStageRules#consumeOnPlace}).
 */
public interface MaterialSource {

	/** How many of item {@code item} the source holds now. */
	int count(String item);

	/**
	 * Takes {@code count} of item {@code item} out — all of them or none. Returns whether it did; a source that holds
	 * fewer keeps what it has.
	 */
	boolean consume(String item, int count);
}
