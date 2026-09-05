package dev.alaindustrial.skill.fabric;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.skill.SkillStore;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric registration of the {@link PlayerSkills} player attachment (MOD-483) and its binding into the
 * common {@link SkillStore} seam — the twin of {@code FabricPlayerStats}, with the same four choices
 * and the same reasons.
 *
 * <p>Persistent so a build survives relog; {@code copyOnDeath} because a tree bought with levels is
 * career progress, not carried inventory, and losing it to a creeper would be a bug report rather than
 * a rule; synced {@link AttachmentSyncPredicate#targetOnly()} so one player's build never reaches
 * another's client — and that sync is what makes the purchase packet one-way: the screen re-reads the
 * result rather than waiting for an answer.
 */
public final class FabricPlayerSkills {

	/** The player attachment holding one {@link PlayerSkills} per player. */
	public static final AttachmentType<PlayerSkills> TYPE = AttachmentRegistry.create(
			Industrialization.id("player_skills"),
			builder -> builder
					.initializer(() -> PlayerSkills.EMPTY)
					.persistent(PlayerSkills.CODEC)
					.copyOnDeath()
					.syncWith(PlayerSkills.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private FabricPlayerSkills() {
	}

	/** Register the attachment and bind the server-side store seam. Called once from Fabric init. */
	public static void init() {
		SkillStore.bind(new SkillStore.Accessor() {
			@Override
			public PlayerSkills get(ServerPlayer player) {
				// getAttachedOrCreate installs the default on a read; getAttachedOrElse does not. Kept in
				// step with the NeoForge accessor deliberately - a read that writes on one loader only is
				// how the two drift apart (MOD-483).
				return player.getAttachedOrElse(TYPE, PlayerSkills.EMPTY);
			}

			@Override
			public void set(ServerPlayer player, PlayerSkills skills) {
				player.setAttached(TYPE, skills);
			}
		});
	}
}
