package dev.alaindustrial.core.food;

import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What the Canning Machine will and will not swallow (MOD-383).
 *
 * <p>The filter is a <b>property</b>, not a list: anything carrying {@link DataComponents#FOOD} is
 * fair game, so food from other mods works without this class ever hearing about them. Only the
 * exclusions are enumerated, and each one is here for a reason that survives a foreign mod.
 *
 * <p><b>Why side effects vanish for free.</b> In 26.2 a food item carries two independent components:
 * {@link DataComponents#FOOD} holds nutrition and saturation, while {@code DataComponents.CONSUMABLE}
 * holds the eating behaviour <em>and</em> the effects. The ration is built with its own
 * {@code Consumable} and never copies the source's, so a canned golden apple simply has nowhere to
 * keep its regeneration. That is architecture, not a filter, and it holds for modded food too.
 */
public final class CanningRules {
	/**
	 * Food the machine refuses, and why each entry earns its place.
	 *
	 * <p>These five carry their danger in {@code CONSUMABLE} — the very component the ration does not
	 * copy. Accepting them would let the machine launder risk into safety for free: raw chicken in,
	 * salmonella-free ration out. Refusing them keeps the hazard where the player found it.
	 *
	 * <p>Deliberately a short, closed list of vanilla ids rather than a "raw meat" heuristic: this
	 * project has been bitten by hand-written lists drifting, and the defence is to keep them
	 * enumerable and finite. Suspicious stew is knowingly <em>not</em> here — its effects also vanish,
	 * but it is a single-use crafted curiosity rather than a farmable hazard.
	 */
	private static final List<Item> BLOCKED = List.of(
			Items.CHICKEN,
			Items.ROTTEN_FLESH,
			Items.POISONOUS_POTATO,
			Items.PUFFERFISH,
			Items.SPIDER_EYE);

	private CanningRules() {
	}

	/**
	 * Whether this stack may enter the food slot.
	 *
	 * <p>The ration excludes <b>itself</b>, and that is load-bearing rather than tidy: the ration is
	 * food, so without this the machine happily eats its own output — grinding rations back into
	 * fewer rations and burning a tin can on every pass. A hopper loop would do it silently.
	 */
	public static boolean isCannable(ItemStack stack) {
		if (stack.isEmpty() || foodValue(stack) <= 0) {
			return false;
		}
		if (stack.is(ModContent.CANNED_RATION.get())) {
			return false;
		}
		for (Item blocked : BLOCKED) {
			if (stack.is(blocked)) {
				return false;
			}
		}
		return true;
	}

	/** Food value of one item of this stack in tenths, or {@code 0} if it is not usable food. */
	public static int foodValue(ItemStack stack) {
		FoodProperties food = stack.get(DataComponents.FOOD);
		return food == null ? 0 : CanningMath.foodValue(food.nutrition(), food.saturation());
	}

	/**
	 * Same question as {@link #isCannable(ItemStack)} for a bare item, for the recipe viewers, which
	 * sweep the item registry rather than inspecting live stacks. Reads the item's prototype
	 * components, so a datapack that retunes nutrition server-side will not show through here.
	 */
	public static boolean isCannable(Item item) {
		return isCannable(new ItemStack(item));
	}
}
