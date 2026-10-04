package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.stats.PlayerModStats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * L2 characterization of the two player attachments as a loader defines them (MOD-708, batch 0): id,
 * starting value, save form, survival of death, and owner-only sync through the record's packet codec.
 * The definition is loader API, so each lane that runs this hands in a {@link Probe}. Today only the Fabric
 * lane does ({@code PlayerAttachmentDefinitionGameTest}); NeoForge's {@code AttachmentType} keeps its
 * definition in package-private fields and is pinned on its L1.5 lane by
 * {@code NeoForgeAttachmentDefinitionTest}.
 *
 * <p>The attachment is looked up by its id, so moving the declaration into a shared list leaves this
 * scenario untouched. The round trip of the saved data itself is {@code PlayerAttachmentRoundTripScenarios}.
 */
public final class PlayerAttachmentDefinitionScenarios {

	private PlayerAttachmentDefinitionScenarios() {}

	/** Which of the record's two codecs a loader persists the attachment with. */
	public enum SaveForm { CODEC, MAP_CODEC }

	/**
	 * One attachment as the loader declared it.
	 *
	 * @param initial           the value a player without the attachment starts from
	 * @param saveCodec         the codec the loader persists it with
	 * @param copyOnDeath       whether the value survives death
	 * @param syncCodec         the packet codec it is synced with, or {@code null} when it is not synced
	 * @param syncedToOwnerOnly whether the sync reaches its owner and nobody else
	 */
	public record Definition(Object initial, Object saveCodec, boolean copyOnDeath, @Nullable Object syncCodec,
			boolean syncedToOwnerOnly) {}

	/** A loader's view of its attachment registry. */
	@FunctionalInterface
	public interface Probe {
		/** The definition registered under {@code id}, or {@code null} when there is none. */
		@Nullable Definition definition(Identifier id);
	}

	/**
	 * Both attachments keep their definition: starting from {@code EMPTY}, persisted through the record's
	 * {@code form} codec, copied on death and synced to the owner only through {@code STREAM_CODEC}.
	 */
	public static void attachmentsKeepTheirDefinition(GameTestHelper helper, Probe probe, SaveForm form) {
		List<String> problems = new ArrayList<>();
		check(problems, probe, "player_stats", PlayerModStats.EMPTY,
				form == SaveForm.CODEC ? PlayerModStats.CODEC : PlayerModStats.MAP_CODEC, PlayerModStats.STREAM_CODEC);
		check(problems, probe, "player_skills", PlayerSkills.EMPTY,
				form == SaveForm.CODEC ? PlayerSkills.CODEC : PlayerSkills.MAP_CODEC, PlayerSkills.STREAM_CODEC);
		if (!problems.isEmpty()) {
			helper.fail("MOD-708: " + String.join("; ", problems));
			return;
		}
		helper.succeed();
	}

	private static void check(List<String> problems, Probe probe, String path, Object empty, Object saveCodec,
			Object syncCodec) {
		Identifier id = Industrialization.id(path);
		Definition definition = probe.definition(id);
		if (definition == null) {
			problems.add(id + " is not registered");
			return;
		}
		if (definition.initial() != empty) {
			problems.add(id + " does not start from EMPTY");
		}
		if (definition.saveCodec() != saveCodec) {
			problems.add(id + " is not persisted through the expected codec");
		}
		if (!definition.copyOnDeath()) {
			problems.add(id + " is not copied on death");
		}
		if (definition.syncCodec() != syncCodec) {
			problems.add(id + " is not synced through the record's STREAM_CODEC");
		}
		if (!definition.syncedToOwnerOnly()) {
			problems.add(id + " is not synced to its owner only");
		}
	}
}
