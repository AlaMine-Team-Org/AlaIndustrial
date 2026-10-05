package dev.alaindustrial.gametest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.skill.SkillBranch;
import dev.alaindustrial.skill.SkillBuild;
import dev.alaindustrial.skill.SkillSlot;
import dev.alaindustrial.stats.PlayerModStats;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * L2 round trips of the two per-player attachments (MOD-701, batch 0): the skill build and the mod
 * statistics.
 *
 * <p>The loaders write these differently. Fabric persists the attachment through its {@code Codec}
 * ({@code FabricPlayerSkills}, {@code FabricPlayerStats}: {@code persistent(CODEC)}); NeoForge through
 * the {@code MapCodec} ({@code ModAttachmentsNeoForge}: {@code serialize(MAP_CODEC)}), which stores the
 * fields straight into a value output. Both forms are driven here and must write the same tag and
 * read back the same value, so a player data file means the same thing on either loader. The packet
 * codec that syncs the value to the client is checked alongside.
 *
 * <p>The existing point tests stay where they are ({@code SkillPurchaseScenarios.buildSurvivesSaveAndLoad},
 * {@code PlayerStatsOrderTest.packetRoundTripKeepsTheOrder}); this class is the sweep over all three
 * codecs of both attachments.
 */
public final class PlayerAttachmentRoundTripScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(PlayerAttachmentRoundTripScenarios::skillsRoundTripOnBothSaveForms,
								"save_format_skills_round_trip_on_both_save_forms")
						.fabricId("SaveFormatGameTest", "skillsRoundTripOnBothSaveForms").ticks(20, 40),
				RosterEntry.of(PlayerAttachmentRoundTripScenarios::statsRoundTripOnBothSaveForms,
								"save_format_stats_round_trip_on_both_save_forms")
						.fabricId("SaveFormatGameTest", "statsRoundTripOnBothSaveForms").ticks(20, 40));

		private Roster() {}
	}

	private PlayerAttachmentRoundTripScenarios() {}

	/**
	 * @implements R-PER-01 — a skill build survives both loaders' save forms and the sync packet.
	 * @covers R-PER-01
	 */
	public static void skillsRoundTripOnBothSaveForms(GameTestHelper helper) {
		PlayerSkills skills = new PlayerSkills(SkillBuild.EMPTY
				.with(SkillBranch.ENERGY, SkillSlot.IN)
				.with(SkillBranch.ENERGY, SkillSlot.A1)
				.with(SkillBranch.MECH, SkillSlot.IN)
				.with(SkillBranch.AGRO, SkillSlot.IN));
		String problem = roundTrip(helper.getLevel().registryAccess(), skills,
				PlayerSkills.CODEC, PlayerSkills.MAP_CODEC, PlayerSkills.STREAM_CODEC);
		if (problem != null) {
			helper.fail("player skills: " + problem);
			return;
		}
		helper.succeed();
	}

	/**
	 * @implements R-PER-01 — player statistics, including the per-generator totals, survive both
	 *     loaders' save forms and the sync packet.
	 * @covers R-PER-01
	 */
	public static void statsRoundTripOnBothSaveForms(GameTestHelper helper) {
		Map<Identifier, Long> byGenerator = new LinkedHashMap<>();
		byGenerator.put(Identifier.parse("alaindustrial:generator"), 4_000L);
		byGenerator.put(Identifier.parse("alaindustrial:solar_panel"), 250L);
		PlayerModStats stats = new PlayerModStats(1_000L, 700L, 5, byGenerator, 360L, 55L);
		String problem = roundTrip(helper.getLevel().registryAccess(), stats,
				PlayerModStats.CODEC, PlayerModStats.MAP_CODEC, PlayerModStats.STREAM_CODEC);
		if (problem != null) {
			helper.fail("player stats: " + problem);
			return;
		}
		helper.succeed();
	}

	/** The first thing wrong with {@code value}'s trip through the three codecs, or null. */
	private static <T> @Nullable String roundTrip(RegistryAccess registries, T value, Codec<T> codec,
			MapCodec<T> mapCodec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {
		// Fabric: persistent(CODEC).
		Tag fabricTag = codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow();
		T fabricBack = codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), fabricTag).getOrThrow();
		if (!value.equals(fabricBack)) {
			return "the Fabric save form (CODEC) changed " + value + " into " + fabricBack + " via " + fabricTag;
		}

		// NeoForge: serialize(MAP_CODEC) — the fields go straight into a value output.
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		output.store(mapCodec, value);
		CompoundTag neoTag = output.buildResult();
		T neoBack = TagValueInput.create(ProblemReporter.DISCARDING, registries, neoTag).read(mapCodec).orElse(null);
		if (!value.equals(neoBack)) {
			return "the NeoForge save form (MAP_CODEC) changed " + value + " into " + neoBack + " via " + neoTag;
		}
		if (!fabricTag.equals(neoTag)) {
			return "the two loaders write different tags for the same value: Fabric " + fabricTag
					+ ", NeoForge " + neoTag;
		}

		RegistryFriendlyByteBuf buffer = RegistryFriendlyByteBuf.decorator(registries).apply(Unpooled.buffer());
		streamCodec.encode(buffer, value);
		T wireBack = streamCodec.decode(buffer);
		if (buffer.readableBytes() != 0 || !value.equals(wireBack)) {
			return "the sync packet changed " + value + " into " + wireBack + " (" + buffer.readableBytes()
					+ " byte(s) unread)";
		}
		return null;
	}
}
