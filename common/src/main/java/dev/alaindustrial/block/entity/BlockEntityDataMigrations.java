package dev.alaindustrial.block.entity;

import java.util.List;
import java.util.function.Consumer;
import dev.alaindustrial.item.energy.ItemEnergy;
import net.minecraft.world.item.ItemStack;

/**
 * The save-format ladder for block entities (MOD-556) — the block-entity twin of the config's
 * {@code Config.MIGRATIONS}.
 *
 * <p><b>Why this exists.</b> Until MOD-556 no block entity wrote a version at all, so a change to a
 * saved layout had to be recognised by guesswork at the point of reading: the Battery Box worked out
 * that a save predated its discharge slot by noticing an item without an EU buffer sitting in that
 * slot. A guess like that is invisible in the code (it looks like an ordinary load), it cannot say
 * when it is finished, and it re-runs on every load forever. With a version on disk the same change
 * becomes a rung of a ladder that runs exactly once, and the risk a layout change carries for an
 * existing world is finally written down.
 *
 * <p><b>How to add the next hop</b> (deliberately three steps, exactly like the config's):
 * <ol>
 *   <li>Write a {@code private static void vNtoVN1(EnergyBlockEntity be)} that repairs the data in
 *       place. It sees the block entity as the PREVIOUS rung left it, so each step only has to know
 *       about its own hop; a step that concerns one kind of block opens with an {@code instanceof}
 *       and returns for everything else.</li>
 *   <li>Append {@code new Step(N, BlockEntityDataMigrations::vNtoVN1)} to {@link #STEPS}.</li>
 *   <li>Raise {@link #DATA_VERSION} to {@code N + 1}.</li>
 * </ol>
 * {@code PersistenceScenarios.mod556_dataVersionMatchesTheLadder} pins the pair: {@link #STEPS} must
 * hold exactly one rung per version hop, ascending, so a bump with no rung (every old save silently
 * unconverted) or a rung with no bump (it never runs) fails the build instead of a player's world.
 *
 * <p><b>Every step must be safe to run twice.</b> An older jar of this mod reads a new save fine — it
 * simply never asks for {@link #DATA_VERSION_KEY} — but when it saves again the key is gone, so the
 * next launch of a current jar sees the data as version 0 and walks the ladder over it a second time.
 * A rung that is idempotent on already-repaired data (the Battery Box one is: a correct layout has
 * nothing that looks like the old one) survives that round trip; a rung that is not would corrupt on
 * it. This is the price of downgrade tolerance, and it is worth paying — the alternative is a mod
 * that destroys a world when the player boots the previous version once.
 *
 * <p><b>Where a step is applied.</b> {@code BlockEntity#loadWithComponents} and
 * {@code #loadCustomOnly} are {@code final} in 26.2 (checked with {@code javap}), so there is no
 * after-the-whole-load hook to hang a generic call on. A block entity whose data a rung touches
 * therefore calls {@link EnergyBlockEntity#migrateLoadedData()} as the LAST statement of its own
 * {@code loadAdditional}, where its fields are populated. Today that is one call site.
 */
public final class BlockEntityDataMigrations {

	private BlockEntityDataMigrations() {}

	/**
	 * Layout version of the data a block entity of this mod writes, stored under
	 * {@link #DATA_VERSION_KEY}. Bumped when the SHAPE or the MEANING of stored data changes — never
	 * for adding a key, which is absent-safe and needs no rung. See the class doc for the recipe.
	 */
	public static final int DATA_VERSION = 1;

	/**
	 * NBT key holding {@link #DATA_VERSION}. Absent means "written before MOD-556", i.e. version 0 —
	 * the one case where a missing key is information rather than a default.
	 *
	 * <p>Namespaced on purpose, unlike the mod's other block-entity keys. {@code DataVersion} is the
	 * name vanilla already uses for a schema number (on a chunk, on a level), so a bare one here would
	 * be the single key most likely to collide with something a future version, a loader or a
	 * structure tool decides to write into a block-entity tag.
	 */
	public static final String DATA_VERSION_KEY = "AlaDataVersion";

	/**
	 * One rung: "data stored at {@code fromVersion} becomes data at {@code fromVersion + 1} once
	 * {@code apply} has repaired the block entity in place". Kept as data rather than a chain of
	 * {@code if}s so the next hop is one list entry.
	 */
	private record Step(int fromVersion, Consumer<EnergyBlockEntity> apply) {}

	/**
	 * The ladder, ascending, one rung per version hop ({@code STEPS.get(i).fromVersion() == i},
	 * {@code STEPS.size() == DATA_VERSION}).
	 */
	private static final List<Step> STEPS = List.of(
			new Step(0, BlockEntityDataMigrations::v0ToV1RenumberBatteryBoxSlots));

	/** Number of rungs on the ladder — the oracle the version pin compares against. */
	public static int stepCount() {
		return STEPS.size();
	}

	/** The version rung {@code index} converts FROM. */
	public static int stepFromVersion(int index) {
		return STEPS.get(index).fromVersion();
	}

	/**
	 * Walk {@code be} up the ladder from {@code fromVersion} to {@link #DATA_VERSION}, repairing it in
	 * place. A block entity already at (or above) the current version is left alone.
	 */
	public static void migrate(EnergyBlockEntity be, int fromVersion) {
		if (fromVersion >= DATA_VERSION) {
			return;
		}
		for (Step step : STEPS) {
			if (step.fromVersion() >= fromVersion) {
				step.apply().accept(be);
			}
		}
	}

	/**
	 * v0 → v1: a Battery Box saved before MOD-083 loads its upgrade chips one slot too low (C-20).
	 *
	 * <p>Upgrade slots are numbered <em>after</em> the machine slots, so adding the discharge slot moved
	 * them from 1…4 to 2…5. A box saved before that change therefore reads its first upgrade chip into
	 * what is now the discharge slot. The tell is unambiguous, because
	 * {@link BatteryBoxBlockEntity#canPlaceItem} has always refused anything without an EU buffer: a
	 * non-powered item sitting in the discharge slot can only have come from the old numbering. When
	 * that is what we see, shift the whole run one slot up.
	 *
	 * <p>Idempotent, as every rung must be (see the class doc): after the shift the discharge slot is
	 * empty, so a second pass over the repaired data returns immediately.
	 */
	private static void v0ToV1RenumberBatteryBoxSlots(EnergyBlockEntity be) {
		if (!(be instanceof BatteryBoxBlockEntity box)) {
			return;
		}
		ItemStack inDischarge = box.getItem(BatteryBoxBlockEntity.DISCHARGE_SLOT);
		if (inDischarge.isEmpty() || ItemEnergy.capacity(inDischarge) > 0) {
			return;
		}
		for (int slot = box.getContainerSize() - 1; slot > BatteryBoxBlockEntity.DISCHARGE_SLOT; slot--) {
			box.setItem(slot, box.getItem(slot - 1));
		}
		box.setItem(BatteryBoxBlockEntity.DISCHARGE_SLOT, ItemStack.EMPTY);
	}
}
