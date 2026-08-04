package dev.alaindustrial.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * One "N of this ingredient" entry of an {@link AlloyingRecipe} — three copper ingots, one tin ingot.
 *
 * <p>Vanilla has no sized-ingredient type (the crafting grid encodes a count as repeated cells, so it
 * never needed one), and the NeoForge {@code SizedIngredient} lives loader-side where {@code common/}
 * cannot reach it. Hence this small loader-neutral record.
 *
 * <p>JSON form: <code>{"ingredient": "#c:ingots/copper", "count": 3}</code>. {@code count} is optional
 * and defaults to 1, so a single-item component stays a one-field object.
 *
 * @param ingredient the accepted item or tag
 * @param count      how many items one operation consumes, at least 1
 */
public record AlloyComponent(Ingredient ingredient, int count) {

	/** Upper bound for one component — a full vanilla stack; a machine slot holds no more. */
	public static final int MAX_COUNT = 64;

	public AlloyComponent {
		if (count < 1 || count > MAX_COUNT) {
			throw new IllegalArgumentException("alloy component count out of range: " + count);
		}
	}

	/** A component consuming a single item. */
	public AlloyComponent(Ingredient ingredient) {
		this(ingredient, 1);
	}

	/** Whether {@code stack} is the right item for this component, ignoring how many there are. */
	public boolean testItem(ItemStack stack) {
		return !stack.isEmpty() && ingredient.test(stack);
	}

	/** Whether {@code stack} is the right item AND holds enough of it for one operation. */
	public boolean testItemAndCount(ItemStack stack) {
		return testItem(stack) && stack.getCount() >= count;
	}

	public static final Codec<AlloyComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Ingredient.CODEC.fieldOf("ingredient").forGetter(AlloyComponent::ingredient),
			// Written back only when it carries information, so a one-item component round-trips as the
			// bare object it was authored as instead of growing a noise field.
			Codec.intRange(1, MAX_COUNT).optionalFieldOf("count", 1).forGetter(AlloyComponent::count)
	).apply(instance, AlloyComponent::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, AlloyComponent> STREAM_CODEC =
			StreamCodec.composite(
					Ingredient.CONTENTS_STREAM_CODEC, AlloyComponent::ingredient,
					ByteBufCodecs.VAR_INT, AlloyComponent::count,
					AlloyComponent::new);
}
