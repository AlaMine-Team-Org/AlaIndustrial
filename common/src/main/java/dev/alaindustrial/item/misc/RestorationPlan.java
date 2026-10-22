package dev.alaindustrial.item.misc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * What a restoration plan points at (MOD-859), the {@code alaindustrial:restoration_plan} data component: the
 * settlement style and the id of that style's intact building template (MOD-856), and nothing else. A plan never
 * carries blocks or their contents: a plan that did would copy items (research of stage 6, §7).
 */
public record RestorationPlan(Identifier style, Identifier template) {

	public static final Codec<RestorationPlan> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("style").forGetter(RestorationPlan::style),
			Identifier.CODEC.fieldOf("template").forGetter(RestorationPlan::template)
	).apply(i, RestorationPlan::new));

	public static final StreamCodec<ByteBuf, RestorationPlan> STREAM_CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, RestorationPlan::style,
			Identifier.STREAM_CODEC, RestorationPlan::template,
			RestorationPlan::new);

	/** The plan the rules put together, as a component; {@code null} when the plan's ids do not parse. */
	public static RestorationPlan of(PlanSheetRules.Plan plan) {
		Identifier style = Identifier.tryParse(plan.style());
		Identifier template = Identifier.tryParse(plan.template());
		return style == null || template == null ? null : new RestorationPlan(style, template);
	}

	/** The building the plan restores: the last segment of its template's path ({@code town_hall}). */
	public String building() {
		String path = template.getPath();
		return path.substring(path.lastIndexOf('/') + 1);
	}
}
