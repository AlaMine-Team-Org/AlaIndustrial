package dev.alaindustrial.item.misc;

import dev.alaindustrial.core.radiation.RadiationConfig;
import dev.alaindustrial.core.radiation.RadiationCore;
import dev.alaindustrial.core.radiation.RadiationDose;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;

/**
 * Milk "Antirad" bar (MOD-914): eaten in four bites, each bite takes a quarter of the radiation scale off — the
 * whole bar works like a bucket of milk on radiation, and leaves every other effect alone.
 *
 * <p>Each bite is a stage of its own item, joined by vanilla's use remainder: eating the whole bar leaves the
 * three-quarter bar in hand, and so on; the last quarter leaves nothing. Every stage is ordinary food (so it
 * stacks, and hunger mods read it as food), edible on a full bar because it is a medicine first.
 */
public class AntiradBarItem extends HintItem {

	/** Hunger points per bite: a light snack, four bites make a modest meal. */
	public static final int NUTRITION = 2;
	/** Saturation modifier per bite (saturation = nutrition × modifier × 2 = 1.2). */
	public static final float SATURATION_MODIFIER = 0.3F;
	/** One bite: quicker than a full meal, as quick as the ration. */
	public static final float CONSUME_SECONDS = 1.0F;
	/** A bar is carried in a pocket, not by the stack. */
	public static final int STACK_SIZE = 16;

	public AntiradBarItem(Item.Properties properties) {
		super(properties.stacksTo(STACK_SIZE).food(new FoodProperties.Builder()
						.nutrition(NUTRITION)
						.saturationModifier(SATURATION_MODIFIER)
						.alwaysEdible()
						.build(),
				Consumable.builder().consumeSeconds(CONSUME_SECONDS).build()),
				"item.alaindustrial.antirad_bar.hint", "item.alaindustrial.antirad_bar.hint2");
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack left = super.finishUsingItem(stack, level, entity);
		if (!level.isClientSide()) {
			bite(entity);
		}
		return left;
	}

	/** One bite's worth of radiation off: {@link RadiationConfig#antiradBiteDosePercent} of the scale. */
	public static void bite(LivingEntity entity) {
		int capacity = RadiationConfig.radiationDoseCapacity;
		int next = RadiationCore.afterBite(RadiationDose.of(entity), capacity, RadiationConfig.antiradBiteDosePercent);
		// Cleared first, always: RadiationDose.apply only ever has to RAISE a dose, and vanilla keeps the longer of
		// two instances at the same level, so writing a smaller dose over one in the same band would be ignored.
		RadiationDose.clear(entity);
		if (next > 0) {
			RadiationDose.apply(entity, next, capacity, !(entity instanceof Player));
		}
	}
}
