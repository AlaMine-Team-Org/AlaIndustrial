package dev.alaindustrial.item.misc;

import dev.alaindustrial.core.food.CanningMath;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;

/**
 * Canned Ration (MOD-383): the mod's first edible item, and the one place its food numbers are declared. Its
 * nutrition is fixed no matter what went into the machine — that is precisely what lets every ration stack with
 * every other one, which is the entire point of the machine (a ration that remembered its source would carry a
 * different component and never merge). Its Consumable is built here rather than inherited from the source food,
 * so effects like a golden apple's regeneration have nowhere to travel.
 *
 * <p>A plain {@link Item}, not a subclass: this class only builds it. The Materials domain of the content manifest
 * declares the entry ({@code item("canned_ration", CannedRation::create, …)}); the construction moved here so the
 * declarative table stays a table (MOD-711).
 */
public final class CannedRation {
	private CannedRation() {
	}

	/** The ration item for the loader-supplied {@code properties}. */
	public static Item create(Item.Properties properties) {
		return new Item(properties.food(
				new FoodProperties.Builder()
						.nutrition(CanningMath.RATION_NUTRITION)
						.saturationModifier(CanningMath.RATION_SATURATION_MODIFIER)
						// Deliberately NOT alwaysEdible: a full player cannot eat this, exactly like every
						// ordinary food. Being edible at a full bar was tried and dropped — it let the
						// player top the hunger bar off at will, which is a power vanilla reserves for the
						// golden apple, and it is not what this machine is for.
						.build(),
				Consumable.builder()
						.consumeSeconds(CanningMath.RATION_CONSUME_SECONDS)
						// DRINK rather than EAT: the drink pose tips the item up to the mouth, which is
						// what eating straight out of a tin looks like — the eat pose holds it flat and
						// reads as biting a loaf. The SOUND stays the ordinary eating one, so it is chewed
						// like a steak while being held like a can.
						.animation(ItemUseAnimation.DRINK)
						.sound(SoundEvents.GENERIC_EAT)
						// Vanilla's drink preset turns particles off; kept on here so bits still fly and
						// the act reads as a meal rather than a swig.
						.hasConsumeParticles(true)
						// A metallic clink as the emptied tin is thrown away — the one cue that this was
						// a can and not a bowl.
						.soundAfterConsume(SoundEvents.ARMOR_EQUIP_IRON)
						.build()));
	}
}
