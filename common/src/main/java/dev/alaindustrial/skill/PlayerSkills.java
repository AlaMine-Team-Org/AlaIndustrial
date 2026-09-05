package dev.alaindustrial.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The persisted and synced form of one player's {@link SkillBuild} (MOD-483) — the player attachment
 * both loaders store, save and mirror to its owner.
 *
 * <p>Split from {@link SkillBuild} on purpose: the rules of the tree are arithmetic worth unit-testing
 * without a game, and only this thin wrapper knows about codecs. Everything interesting is delegated.
 *
 * <p><b>Wire and disk form is a flat list of {@code "branch/SLOT"} strings.</b> Not ordinals: a
 * reordered enum would silently turn one player's capstone into another's entry. Not a nested map
 * either — a list of short strings is smaller on the wire than a map of lists, and an entry naming a
 * branch or slot this build no longer has is dropped on read rather than failing the whole decode, so
 * a save from a version that had an extra node still loads.
 */
public record PlayerSkills(SkillBuild build) {

	/** Nothing bought — the attachment's initial value on both loaders. */
	public static final PlayerSkills EMPTY = new PlayerSkills(SkillBuild.EMPTY);

	private static final String SEPARATOR = "/";

	/**
	 * Longest {@code "branch/SLOT"} string that can ever be legitimate, used to cap the wire codec so an
	 * oversized entry never even decodes. Computed rather than written down: a longer branch key added
	 * later must not silently start failing to send.
	 */
	private static final int MAX_ENTRY_LENGTH = maxEntryLength();

	private static int maxEntryLength() {
		int branch = 0;
		for (SkillBranch value : SkillBranch.values()) {
			branch = Math.max(branch, value.key().length());
		}
		int slot = 0;
		for (SkillSlot value : SkillSlot.values()) {
			slot = Math.max(slot, value.name().length());
		}
		return branch + SEPARATOR.length() + slot;
	}

	/** Total entries a build can possibly hold — the wire list is capped at this, not left unbounded. */
	private static final int MAX_ENTRIES = SkillBranch.values().length * SkillSlot.values().length;

	/** Map-codec form; drives both {@link #CODEC} (Fabric persistent) and NeoForge {@code serialize}. */
	public static final MapCodec<PlayerSkills> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.listOf().optionalFieldOf("taken", List.of())
					.forGetter(PlayerSkills::encodeEntries))
			.apply(instance, PlayerSkills::decodeEntries));

	public static final Codec<PlayerSkills> CODEC = MAP_CODEC.codec();

	/** The entry list on the wire: short strings, both the string and the list length capped. */
	private static final StreamCodec<io.netty.buffer.ByteBuf, List<String>> ENTRY_LIST =
			ByteBufCodecs.stringUtf8(MAX_ENTRY_LENGTH).apply(ByteBufCodecs.list(MAX_ENTRIES));

	/**
	 * Wire form, assembled with {@code of} rather than {@code composite} or {@code map}: composite's
	 * shortest overload takes two fields and this record carries one, while {@code map} would keep the
	 * buffer type of {@link #ENTRY_LIST} ({@code ByteBuf}) instead of the {@code RegistryFriendlyByteBuf}
	 * an attachment codec has to be.
	 */
	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerSkills> STREAM_CODEC = StreamCodec.of(
			(buffer, value) -> ENTRY_LIST.encode(buffer, value.encodeEntries()),
			buffer -> decodeEntries(ENTRY_LIST.decode(buffer)));

	/** Canonical constructor guards against a null build so an unbound store can never poison a read. */
	public PlayerSkills {
		build = build == null ? SkillBuild.EMPTY : build;
	}

	private List<String> encodeEntries() {
		List<String> entries = new ArrayList<>();
		build.taken().forEach((branch, slots) -> {
			for (SkillSlot slot : slots) {
				entries.add(branch.key() + SEPARATOR + slot.name());
			}
		});
		return entries;
	}

	/**
	 * Rebuild from wire/disk entries, skipping anything unrecognised.
	 *
	 * <p>Skipping rather than failing is deliberate: an entry naming a node that no longer exists comes
	 * from a save written by a different build of the mod, and refusing the whole decode there would
	 * cost the player their entire tree over one removed node.
	 */
	private static PlayerSkills decodeEntries(List<String> entries) {
		if (entries.isEmpty()) {
			return EMPTY;
		}
		EnumMap<SkillBranch, Set<SkillSlot>> taken = new EnumMap<>(SkillBranch.class);
		for (String entry : entries) {
			int cut = entry.indexOf(SEPARATOR);
			if (cut <= 0) {
				continue;
			}
			SkillBranch branch = SkillBranch.byKey(entry.substring(0, cut));
			SkillSlot slot = SkillSlot.byKey(entry.substring(cut + SEPARATOR.length()));
			if (branch != null && slot != null) {
				taken.computeIfAbsent(branch, b -> EnumSet.noneOf(SkillSlot.class)).add(slot);
			}
		}
		return new PlayerSkills(SkillBuild.of(taken));
	}

	/** Convenience for the common "one skill added" write. */
	public PlayerSkills with(SkillBranch branch, SkillSlot slot) {
		return new PlayerSkills(build.with(branch, slot));
	}

	/** The wiped build — what a paid reset stores. */
	public PlayerSkills cleared() {
		return EMPTY;
	}

	/** Map form for anything that wants the raw contents without going through {@link SkillBuild}. */
	public Map<SkillBranch, Set<SkillSlot>> taken() {
		return build.taken();
	}
}
