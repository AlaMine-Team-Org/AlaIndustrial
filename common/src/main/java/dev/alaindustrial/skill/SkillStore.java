package dev.alaindustrial.skill;

import java.util.function.UnaryOperator;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side seam to a player's {@link PlayerSkills} attachment (MOD-483) — the same set-once idiom as
 * {@code PlayerStatsStore}, for the same reason: the attachment API has no loader-neutral form, and
 * common code must not import either loader.
 *
 * <p>An unbound store reads {@link PlayerSkills#EMPTY} and swallows writes rather than throwing: a
 * server whose attachment was never wired should behave as though nobody has bought anything, not
 * crash the first time a player opens the Workstation.
 */
public final class SkillStore {

	/** Loader-bound bridge to the actual attachment get/set on a {@link ServerPlayer}. */
	public interface Accessor {
		PlayerSkills get(ServerPlayer player);

		void set(ServerPlayer player, PlayerSkills skills);
	}

	private static Accessor accessor;

	private SkillStore() {
	}

	/** Called once per loader during init, before any skill is read or written. */
	public static void bind(Accessor impl) {
		accessor = impl;
	}

	/** The player's skills, or {@link PlayerSkills#EMPTY} if the accessor is unbound. */
	public static PlayerSkills get(ServerPlayer player) {
		return accessor == null ? PlayerSkills.EMPTY : accessor.get(player);
	}

	/** The player's build — what every buff site reads. */
	public static SkillBuild build(ServerPlayer player) {
		return get(player).build();
	}

	/** Replace the player's skills (triggers attachment persistence + owner sync). No-op if unbound. */
	public static void set(ServerPlayer player, PlayerSkills skills) {
		if (accessor != null) {
			accessor.set(player, skills);
		}
	}

	/** Read-modify-write in one call — the safe way to update an immutable record. */
	public static void modify(ServerPlayer player, UnaryOperator<PlayerSkills> update) {
		set(player, update.apply(get(player)));
	}
}
