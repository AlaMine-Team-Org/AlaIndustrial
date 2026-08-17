package dev.alaindustrial.menu;

import dev.alaindustrial.registry.ModTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What each slot of a machine's upgrade panel accepts (MOD-080, typed again in MOD-393).
 *
 * <p>One kind per panel arm, in panel order. Typing the slots rather than letting every slot take
 * every upgrade is what makes the panel readable without a word of text: each arm carries a baked
 * hint icon in the atlas (a speaker for the mute arm, a double arrow for the overclocker arm), so the
 * player sees where a chip goes before trying it. It also caps stacking for free — one overclocker
 * per machine, because there is exactly one arm that takes one.
 *
 * <p>A kind with a {@code null} tag is reserved: visible, greyed out, accepts nothing. The last free arm
 * is held for the modules of MOD-286 (ejector, puller, energy storage, redstone inverter); adding one is
 * a tag plus a hint glyph, not a change to this mechanism.
 *
 * <p><b>MOD-125 took arm 2</b> for the statistics chip. MOD-286 planned four modules across the two arms
 * that were free, so it is now one arm short and will have to widen the panel or group its modules —
 * noted here because nothing else in the code says the arm was spoken for.
 */
public enum UpgradeSlotKind {
	/** Panel arm 0 (left): the mute chip. */
	MUTE(ModTags.Items.UPGRADE_MUTE),
	/** Panel arm 1 (top): the overclocker chips, one of the three tiers. */
	OVERCLOCK(ModTags.Items.UPGRADE_OVERCLOCK),
	/** Panel arm 2 (right): the statistics chip (MOD-125) — without it a machine keeps no statistics. */
	STATS(ModTags.Items.UPGRADE_STATS),
	/** Panel arm 3 (bottom): reserved for a future module (MOD-286). */
	RESERVED_BOTTOM(null),
	/**
	 * What arm 1 becomes on a machine the chip does nothing to — a generator, a store, a pipe.
	 *
	 * <p>Locked rather than absent: the panel keeps the same four arms everywhere, so the mute chip's
	 * arm never moves, and a greyed arm reads as "not here" instead of "you missed". Before this, the
	 * arm accepted the chip and the machine ignored it, which reads as a broken chip.
	 *
	 * <p>Declared LAST so the ordinals of the four real arms stay put; {@link #byIndex} maps positions
	 * explicitly and never returns this one.
	 */
	OVERCLOCK_UNSUPPORTED(null);

	@Nullable
	private final TagKey<Item> accepted;

	UpgradeSlotKind(@Nullable TagKey<Item> accepted) {
		this.accepted = accepted;
	}

	/**
	 * The kind of panel arm {@code index} (0-based, panel order); out-of-range indices read as reserved.
	 *
	 * <p>An explicit map, not {@code values()[index]}: the enum now carries a constant that is not a
	 * panel position at all ({@link #OVERCLOCK_UNSUPPORTED}), and ordinal indexing would have started
	 * handing it out the moment it was appended.
	 */
	public static UpgradeSlotKind byIndex(int index) {
		return switch (index) {
			case 0 -> MUTE;
			case 1 -> OVERCLOCK;
			case 2 -> STATS;
			default -> RESERVED_BOTTOM;
		};
	}

	/** Whether this arm takes anything at all — false for the arms held for future modules. */
	public boolean isOpen() {
		return accepted != null;
	}

	/** Whether {@code stack} belongs in this arm. A reserved arm refuses everything. */
	public boolean accepts(ItemStack stack) {
		return accepted != null && stack.is(accepted);
	}
}
