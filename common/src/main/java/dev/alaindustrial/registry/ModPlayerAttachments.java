package dev.alaindustrial.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.alaindustrial.attachment.PlayerAttachmentAccessor;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.skill.SkillClientCache;
import dev.alaindustrial.skill.SkillStore;
import dev.alaindustrial.stats.PlayerModStats;
import dev.alaindustrial.stats.PlayerStatsClientCache;
import dev.alaindustrial.stats.PlayerStatsStore;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Every per-player data attachment, declared once (MOD-708) and replayed by both loaders: Fabric through
 * its {@code AttachmentRegistry} ({@code FabricPlayerAttachments}), NeoForge through a
 * {@code DeferredRegister} of {@code AttachmentType}s ({@code ModAttachmentsNeoForge}).
 *
 * <p><b>Each loader keeps its own save form.</b> Fabric persists an attachment through its {@link Codec}
 * ({@code persistent(codec)}), NeoForge through its {@link MapCodec} ({@code serialize(mapCodec)}); the
 * two forms are what player files hold today, so the entry carries both and neither loader swaps.
 *
 * <p>A new attachment is one {@link PlayerAttachmentDef} here plus its server store and client cache; both
 * loaders implement {@link PlayerAttachmentAccessor} once for every entry.
 */
public final class ModPlayerAttachments {
	private ModPlayerAttachments() {
	}

	/**
	 * One attachment.
	 *
	 * @param id              registry path ({@code alaindustrial:<id>})
	 * @param empty           the value a player without the attachment reads
	 * @param codec           the Fabric save form
	 * @param mapCodec        the NeoForge save form
	 * @param streamCodec     the sync packet form
	 * @param copyOnDeath     whether the value survives death
	 * @param syncToOwner     whether the value is mirrored to its owner (and only to its owner)
	 * @param bindStore       hands the loader's accessor to the server-side store seam
	 * @param bindClientCache hands the loader's local-player reader to the client cache
	 */
	public record PlayerAttachmentDef<T>(String id, T empty, Codec<T> codec, MapCodec<T> mapCodec,
			StreamCodec<RegistryFriendlyByteBuf, T> streamCodec, boolean copyOnDeath, boolean syncToOwner,
			Consumer<PlayerAttachmentAccessor<T>> bindStore, Consumer<Supplier<T>> bindClientCache) {
		public PlayerAttachmentDef {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(empty, "empty");
			Objects.requireNonNull(codec, "codec");
			Objects.requireNonNull(mapCodec, "mapCodec");
			Objects.requireNonNull(streamCodec, "streamCodec");
			Objects.requireNonNull(bindStore, "bindStore");
			Objects.requireNonNull(bindClientCache, "bindClientCache");
		}
	}

	/**
	 * MOD-133: a player's career statistics — persisted, kept across death and mirrored only to its owner,
	 * so one player's numbers never reach another's client.
	 */
	public static final PlayerAttachmentDef<PlayerModStats> PLAYER_STATS = new PlayerAttachmentDef<>(
			"player_stats", PlayerModStats.EMPTY, PlayerModStats.CODEC, PlayerModStats.MAP_CODEC,
			PlayerModStats.STREAM_CODEC, true, true, PlayerStatsStore::bind, PlayerStatsClientCache::bind);

	/**
	 * MOD-483: the Workstation's upgrade tree — the same four choices for the same reasons: a tree bought
	 * with levels is career progress, not carried inventory, and the owner-only sync is what lets the
	 * purchase packet be one-way.
	 */
	public static final PlayerAttachmentDef<PlayerSkills> PLAYER_SKILLS = new PlayerAttachmentDef<>(
			"player_skills", PlayerSkills.EMPTY, PlayerSkills.CODEC, PlayerSkills.MAP_CODEC,
			PlayerSkills.STREAM_CODEC, true, true, SkillStore::bind, SkillClientCache::bind);

	/** Every player attachment, in registration order. */
	public static final List<PlayerAttachmentDef<?>> PLAYER_ATTACHMENTS = List.of(PLAYER_STATS, PLAYER_SKILLS);
}
