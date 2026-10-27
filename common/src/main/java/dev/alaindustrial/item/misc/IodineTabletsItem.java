package dev.alaindustrial.item.misc;

import dev.alaindustrial.effect.IodineProtectionEffect;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;

/**
 * Iodine tablets (MOD-916): taken before a job near radiation, they make the dose build up slower for a while —
 * the effect is {@link IodineProtectionEffect}.
 *
 * <p>Not food, like the chewing gum: a {@link Consumable} with no {@code FoodProperties}, so it is swallowed on a
 * full stomach and fills nothing. The effect is applied here and not as a {@code ConsumeEffect}, because its
 * length is a config knob read when the tablet is taken.
 */
public class IodineTabletsItem extends HintItem {

	/** A tablet is swallowed, not chewed: as quick as the gum, faster than any meal. */
	public static final float CONSUME_SECONDS = 0.8F;

	public IodineTabletsItem(Properties properties) {
		super(properties.component(DataComponents.CONSUMABLE, Consumable.builder()
						.consumeSeconds(CONSUME_SECONDS)
						.animation(ItemUseAnimation.EAT)
						.sound(SoundEvents.GENERIC_EAT)
						.hasConsumeParticles(false)
						.build()),
				"item.alaindustrial.iodine_tablets.hint", "item.alaindustrial.iodine_tablets.hint2",
				"item.alaindustrial.iodine_tablets.hint3");
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack left = super.finishUsingItem(stack, level, entity);
		if (!level.isClientSide()) {
			IodineProtectionEffect.take(entity);
		}
		return left;
	}
}
