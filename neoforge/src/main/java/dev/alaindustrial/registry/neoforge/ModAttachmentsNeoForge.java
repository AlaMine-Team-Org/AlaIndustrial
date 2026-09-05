package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.skill.SkillStore;
import dev.alaindustrial.stats.PlayerModStats;
import dev.alaindustrial.stats.PlayerStatsStore;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge registration of the {@link PlayerModStats} player attachment (MOD-133) and its binding
 * into the common {@link PlayerStatsStore} seam. The attachment registry freezes before mod init, so
 * (like data components) it goes through a {@link DeferredRegister} on the mod bus. Serialized via
 * {@link PlayerModStats#MAP_CODEC} (persists across relog), {@code copyOnDeath} (NeoForge copies it
 * automatically on the death clone), and synced only to its owner — the sync predicate sends the
 * attachment to a player only when that player <em>is</em> the holder.
 */
public final class ModAttachmentsNeoForge {
	public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
			DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Industrialization.MOD_ID);

	public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerModStats>> PLAYER_STATS =
			ATTACHMENTS.register("player_stats", () -> AttachmentType
					.builder(() -> PlayerModStats.EMPTY)
					.serialize(PlayerModStats.MAP_CODEC)
					.copyOnDeath()
					.sync((holder, player) -> holder == player, PlayerModStats.STREAM_CODEC)
					.build());

	/**
	 * MOD-483: the Workstation's upgrade tree. Same four choices as the stats attachment above and for
	 * the same reasons — persisted, kept across death (a tree bought with levels is career progress, not
	 * carried inventory), and mirrored only to its owner, which is what lets the purchase packet be
	 * one-way.
	 */
	public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerSkills>> PLAYER_SKILLS =
			ATTACHMENTS.register("player_skills", () -> AttachmentType
					.builder(() -> PlayerSkills.EMPTY)
					.serialize(PlayerSkills.MAP_CODEC)
					.copyOnDeath()
					.sync((holder, player) -> holder == player, PlayerSkills.STREAM_CODEC)
					.build());

	private ModAttachmentsNeoForge() {
	}

	/** Bind the server-side store seam to the deferred attachment holder. Called from the {@code @Mod} ctor. */
	public static void init() {
		SkillStore.bind(new SkillStore.Accessor() {
			@Override
			public PlayerSkills get(ServerPlayer player) {
				// Read without creating (MOD-483). NeoForge's getData INSTALLS the default value when none
				// exists and syncs it — so a plain read writes state and puts a packet on the wire, and on
				// a player with no connection (a vanilla gametest mock) it throws outright. The mod asks a
				// player for this several times a second, so the read has to be a read. Same rule as
				// ADR-010 for containers, one layer up.
				PlayerSkills stored = player.getExistingDataOrNull(PLAYER_SKILLS);
				return stored != null ? stored : PlayerSkills.EMPTY;
			}

			@Override
			public void set(ServerPlayer player, PlayerSkills skills) {
				player.setData(PLAYER_SKILLS, skills);
			}
		});
		PlayerStatsStore.bind(new PlayerStatsStore.Accessor() {
			@Override
			public PlayerModStats get(ServerPlayer player) {
				// Same rule as the skills accessor above: a read must not install and sync a default.
				PlayerModStats stored = player.getExistingDataOrNull(PLAYER_STATS);
				return stored != null ? stored : PlayerModStats.EMPTY;
			}

			@Override
			public void set(ServerPlayer player, PlayerModStats stats) {
				player.setData(PLAYER_STATS, stats);
			}
		});
	}
}
