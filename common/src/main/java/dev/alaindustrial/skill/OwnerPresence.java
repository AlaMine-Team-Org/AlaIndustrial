package dev.alaindustrial.skill;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Is a machine's owner here to benefit from it? (MOD-483)
 *
 * <p>One answer, asked by two subsystems. Career statistics have always needed it — an offline or
 * creative player must not accrue mastery — and the skill tree needs exactly the same gate, because the
 * owner's buffs work only while they are in the world (owner decision, 2026-09-04: "player there,
 * buffs work; player gone, buffs don't").
 *
 * <p>It lives here rather than staying private inside {@code PlayerStatsTracker} on purpose. Sharing
 * the tracker's own copy would tie machine balance to the rules of XP accrual: a later change to who
 * earns mastery would silently change how fast every machine in the world runs. Two subsystems, one
 * answer today, and the freedom to disagree later — explicitly, by growing a second method here.
 */
public final class OwnerPresence {

	private OwnerPresence() {
	}

	/**
	 * The owner if they are online and not in creative, else {@code null}.
	 *
	 * <p>Creative counts as absent for the same reason it does in the tracker: a creative player pays
	 * for nothing, so speeding up their machines rewards nothing.
	 */
	@Nullable
	public static ServerPlayer eligible(@Nullable MinecraftServer server, @Nullable UUID owner) {
		if (server == null || owner == null) {
			return null;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player == null || player.hasInfiniteMaterials()) {
			return null;
		}
		return player;
	}

	/**
	 * The skills a machine's owner brings to it right now — {@link SkillBuild#EMPTY} whenever they are
	 * offline, in creative, or the level has no server (a client-side call).
	 *
	 * <p>This is the entry point every owner-gated buff uses, so "whose machine is this and are they
	 * here" is answered in one place instead of once per machine.
	 */
	public static SkillBuild skillsOf(@Nullable Level level, @Nullable UUID owner) {
		if (level == null) {
			return SkillBuild.EMPTY;
		}
		ServerPlayer player = eligible(level.getServer(), owner);
		return player == null ? SkillBuild.EMPTY : SkillStore.build(player);
	}
}
