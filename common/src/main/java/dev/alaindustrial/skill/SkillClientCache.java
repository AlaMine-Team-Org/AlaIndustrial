package dev.alaindustrial.skill;

import java.util.function.Supplier;

/**
 * Client-side seam the skill screen reads its {@link PlayerSkills} from (MOD-483) — the mirror of
 * {@code PlayerStatsClientCache}, and for the same reason.
 *
 * <p>No packet of our own delivers this: both loaders' attachment sync (Fabric
 * {@code syncWith(targetOnly())} / NeoForge {@code sync(holder == player)}) already mirrors the owner's
 * attachment to their own client whenever it is written. So a purchase is one client-to-server payload
 * and the resulting state arrives on its own — the screen simply re-reads this cache every frame.
 *
 * <p>Unbound, or before the first sync, it yields {@link PlayerSkills#EMPTY}: the screen opens on an
 * empty tree rather than crashing.
 */
public final class SkillClientCache {

	private static Supplier<PlayerSkills> reader = () -> PlayerSkills.EMPTY;

	private SkillClientCache() {
	}

	/** Called once during client init: supplies the local player's synced skills attachment. */
	public static void bind(Supplier<PlayerSkills> impl) {
		reader = impl;
	}

	/** The local player's current build (last synced value), never null. */
	public static SkillBuild current() {
		PlayerSkills skills = reader.get();
		return skills == null ? SkillBuild.EMPTY : skills.build();
	}
}
