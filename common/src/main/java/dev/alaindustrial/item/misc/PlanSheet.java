package dev.alaindustrial.item.misc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Which town hall plan sheet a stack is (MOD-859), the {@code alaindustrial:plan_sheet} data component: the settlement
 * style it belongs to and its number, 1..{@value PlanSheetRules#SHEETS}. Sheets of one style and number are equal
 * wherever they were found, so they stack — the plan belongs to the style, not to the town.
 */
public record PlanSheet(Identifier style, int sheet) {

	public static final Codec<PlanSheet> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("style").forGetter(PlanSheet::style),
			Codec.intRange(1, PlanSheetRules.SHEETS).fieldOf("sheet").forGetter(PlanSheet::sheet)
	).apply(i, PlanSheet::new));

	public static final StreamCodec<ByteBuf, PlanSheet> STREAM_CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, PlanSheet::style,
			ByteBufCodecs.VAR_INT, PlanSheet::sheet,
			PlanSheet::new);

	/** This sheet as the rules see it. */
	public PlanSheetRules.Sheet asRuleSheet() {
		return new PlanSheetRules.Sheet(style.toString(), sheet);
	}
}
