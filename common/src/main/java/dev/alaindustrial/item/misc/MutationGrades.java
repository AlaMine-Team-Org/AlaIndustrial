package dev.alaindustrial.item.misc;

import dev.alaindustrial.item.energy.ItemEnergy;

import com.mojang.serialization.Codec;
import dev.alaindustrial.mutation.MutationGrade;
import dev.alaindustrial.registry.ModDataComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

/**
 * Reading and writing the incubator's rarity grade on an item stack (MOD-118).
 *
 * <p>The single point where {@code alaindustrial:mutation_grade} and the vanilla
 * {@link DataComponents#RARITY} are kept in sync — they must never drift apart, since one drives the
 * gameplay bonus and the other the name colour.
 *
 * <p>Follows the ItemEnergy convention: an absent component reads as
 * {@link MutationGrade#COMMON}, and writing COMMON removes both components, so an ordinary outcome
 * is component-identical to a plain vanilla item and keeps stacking with it.
 *
 * <p>The grade enum itself lives in {@code dev.alaindustrial.mutation} and stays free of Minecraft
 * types so the roll logic remains unit-testable at L1; the codecs below bridge it into the game.
 */
public final class MutationGrades {

	/**
	 * Persistent codec. Written by name rather than ordinal so a future reordering of the enum
	 * cannot silently reinterpret saved items.
	 */
	public static final Codec<MutationGrade> CODEC =
			Codec.STRING.xmap(MutationGrade::byName, MutationGrade::serializedName);

	/** Network codec — ordinal is fine here, both sides ship in the same jar. */
	public static final StreamCodec<ByteBuf, MutationGrade> STREAM_CODEC =
			ByteBufCodecs.idMapper(MutationGrade::byOrdinal, MutationGrade::ordinal);

	private MutationGrades() {
	}

	/** The stack's grade; {@link MutationGrade#COMMON} when the component is absent. */
	public static MutationGrade get(ItemStack stack) {
		MutationGrade grade = stack.get(ModDataComponents.MUTATION_GRADE.get());
		return grade == null ? MutationGrade.COMMON : grade;
	}

	/**
	 * Writes the grade together with its vanilla rarity. COMMON removes both components.
	 *
	 * <p>Note that vanilla has no LEGENDARY rarity — its four steps are COMMON, UNCOMMON, RARE and
	 * EPIC — so a legendary grade maps onto {@link Rarity#EPIC} (light purple), the top colour
	 * available.
	 */
	public static void set(ItemStack stack, @Nullable MutationGrade grade) {
		if (grade == null || !grade.isMarked()) {
			// Only strip what is actually there. Calling remove() for a component the item carries by
			// default (every item has a default RARITY) records an explicit "removed" patch, which
			// makes the stack unequal to a plain one — it would then refuse to stack with itself and
			// stall the machine on a "full" output slot.
			if (stack.has(ModDataComponents.MUTATION_GRADE.get())) {
				stack.remove(ModDataComponents.MUTATION_GRADE.get());
			}
			if (stack.hasNonDefault(DataComponents.RARITY)) {
				stack.remove(DataComponents.RARITY);
			}
			return;
		}
		stack.set(ModDataComponents.MUTATION_GRADE.get(), grade);
		stack.set(DataComponents.RARITY, vanillaRarity(grade));
	}

	/** Maps a grade onto the closest vanilla rarity (drives the item name colour). */
	public static Rarity vanillaRarity(MutationGrade grade) {
		return switch (grade) {
			case COMMON -> Rarity.COMMON;
			case RARE -> Rarity.UNCOMMON;
			case EPIC -> Rarity.RARE;
			case LEGENDARY -> Rarity.EPIC;
		};
	}
}
