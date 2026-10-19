package dev.alaindustrial.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * A settlement style (MOD-830, ADR-050 §5): what a settlement of this style IS in the game — where it stands in
 * the world is its structure's business ({@code worldgen/structure/ala_town_<style>.json}). A datapack registry,
 * {@code data/<ns>/alaindustrial/settlement_style/<style>.json}, read only on the server; the built-in styles are
 * files in the same format.
 *
 * <p>The fields read today: {@code name}, the translation key of the style's name, and {@code biomes}, the biome
 * tag that is the style's home (for the built-in styles the same tag as their structure's {@code biomes}; the
 * reference gate checks it). The other fields of ADR-050 ({@code looks}, {@code buildings}, {@code clues},
 * {@code buffs}) are read by the stages that use them; until then a file may carry them and they are skipped,
 * like any field this build does not know.
 */
public record SettlementStyle(String name, TagKey<Biome> biomes) {

	public static final Codec<SettlementStyle> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(SettlementStyle::name),
			TagKey.hashedCodec(Registries.BIOME).fieldOf("biomes").forGetter(SettlementStyle::biomes)
	).apply(i, SettlementStyle::new));
}
