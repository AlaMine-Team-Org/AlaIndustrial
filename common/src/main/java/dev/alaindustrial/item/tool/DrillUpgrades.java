package dev.alaindustrial.item.tool;

import com.mojang.serialization.Codec;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * The set of permanent upgrades installed on one drill (MOD-482) — the value behind the
 * {@code alaindustrial:drill_upgrades} component.
 *
 * <h2>Why a list of strings and not a boolean</h2>
 * The first upgrade to exist is the column bore, and one boolean would have carried it. The Upgrade
 * Table is built as a frame for more, though, and a boolean per upgrade means a new component per
 * upgrade — each one a fresh registry entry that old saves do not have. A list carries every future
 * upgrade in the same component, so adding the second one is a constant, not a migration.
 *
 * <p>Ids are stored as <b>strings, not an enum</b>, for the reason {@code PlayerSkills} states about
 * the skill tree: a save written by a different build of the mod may name an upgrade this build no
 * longer has, and refusing to decode there would cost the player the whole drill. An unknown id is
 * simply carried along and never matches a query, so a tool from an older or newer build keeps
 * whatever it can and loses only what genuinely went away.
 *
 * <p>Immutable and a record on purpose: a component value that does not implement {@code equals}/
 * {@code hashCode} throws at runtime on NeoForge (Fabric passes it silently), and the list is copied
 * on construction so nothing outside can mutate a value already written onto a stack.
 */
public record DrillUpgrades(List<String> installed) {

	/** The column bore (MOD-482): the drill breaks the block above and below the one it hits. */
	public static final String COLUMN_BORE = "column_bore";

	/** A drill with nothing installed. Also what a stack without the component reads as. */
	public static final DrillUpgrades NONE = new DrillUpgrades(List.of());

	/** Upper bound on one id, so a hostile payload cannot make the component grow without limit. */
	private static final int MAX_ID_LENGTH = 64;
	/** Upper bound on the set. Far above the number of upgrades this mod will ever ship. */
	private static final int MAX_ENTRIES = 32;

	public DrillUpgrades {
		installed = List.copyOf(installed);
	}

	public static final Codec<DrillUpgrades> CODEC = Codec.STRING
			.sizeLimitedListOf(MAX_ENTRIES)
			.xmap(DrillUpgrades::new, DrillUpgrades::installed);

	public static final StreamCodec<RegistryFriendlyByteBuf, DrillUpgrades> STREAM_CODEC =
			ByteBufCodecs.stringUtf8(MAX_ID_LENGTH)
					.apply(ByteBufCodecs.list(MAX_ENTRIES))
					.map(DrillUpgrades::new, DrillUpgrades::installed)
					.cast();

	public boolean has(String id) {
		return installed.contains(id);
	}

	/** This set plus {@code id}, or this set unchanged when it already holds it. */
	public DrillUpgrades with(String id) {
		if (has(id) || installed.size() >= MAX_ENTRIES) {
			return this;
		}
		List<String> grown = new ArrayList<>(installed);
		grown.add(id);
		return new DrillUpgrades(grown);
	}

	// --- stack access: the one place the component is read and written ------------------------

	public static DrillUpgrades of(ItemStack stack) {
		return stack.getOrDefault(ModDataComponents.DRILL_UPGRADES.get(), NONE);
	}

	public static boolean has(ItemStack stack, String id) {
		return of(stack).has(id);
	}

	/** Installs {@code id} on the stack. Returns false when it was already there — the Upgrade Table
	 * uses that to refuse the job instead of eating a module for nothing. */
	public static boolean install(ItemStack stack, String id) {
		DrillUpgrades before = of(stack);
		DrillUpgrades after = before.with(id);
		if (after.equals(before)) {
			return false;
		}
		stack.set(ModDataComponents.DRILL_UPGRADES.get(), after);
		return true;
	}
}
