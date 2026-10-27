package dev.alaindustrial.item.misc;

import dev.alaindustrial.effect.FreshBreathEffect;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;

/**
 * Rubber chewing gum (MOD-918): chewed in a moment, it clears Nausea and keeps it away for a while — the
 * effect and its guard are {@link FreshBreathEffect}.
 *
 * <p>A {@link Consumable} with no {@code FoodProperties}: it is not food, fills nothing, and so is chewable on
 * a full bar — vanilla only asks the hunger bar of an item that has food. The effect is applied here rather than
 * as a {@code ConsumeEffect} on the component, because its length is a config knob read at the moment of
 * chewing, and a component is built once at registration.
 */
public class ChewingGumItem extends HintItem {

	/** Faster than any vanilla food: ordinary food takes 1.6 s, dried kelp — the quickest — 0.8 s. */
	public static final float CONSUME_SECONDS = 0.6F;

	public ChewingGumItem(Properties properties) {
		super(properties.component(DataComponents.CONSUMABLE, Consumable.builder()
						.consumeSeconds(CONSUME_SECONDS)
						.animation(ItemUseAnimation.EAT)
						.sound(SoundEvents.GENERIC_EAT)
						.hasConsumeParticles(true)
						.build()),
				"item.alaindustrial.chewing_gum.hint", "item.alaindustrial.chewing_gum.hint2");
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack left = super.finishUsingItem(stack, level, entity);
		if (!level.isClientSide()) {
			FreshBreathEffect.chew(entity);
		}
		return left;
	}
}
