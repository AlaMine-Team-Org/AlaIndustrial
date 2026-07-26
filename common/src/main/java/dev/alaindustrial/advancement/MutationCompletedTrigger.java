package dev.alaindustrial.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.mutation.MutationGrade;
import java.util.Optional;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Fires when a player takes a mutation result out of the incubator's output slot (MOD-118).
 *
 * <p>The moment of collection, not the moment of production, is what the player is credited for — and
 * it is the only moment with a player attached. A machine finishing a cycle has no owner: the block
 * entity knows nothing about who built it, and guessing from the nearest entity would hand the
 * advancement to whoever happened to walk past.
 *
 * <p>Two optional filters let three advancements share one trigger: an {@link ItemPredicate} for "this
 * was a creation, not a transform" (the four {@code create} outputs are mod items, so the item alone
 * identifies the mode) and a grade name for "this one came out legendary".
 */
public class MutationCompletedTrigger extends SimpleCriterionTrigger<MutationCompletedTrigger.TriggerInstance> {

	@Override
	public Codec<TriggerInstance> codec() {
		return TriggerInstance.CODEC;
	}

	/** Called with the stack the player just took and the grade written on it. */
	public void trigger(ServerPlayer player, ItemStack taken, MutationGrade grade) {
		trigger(player, instance -> instance.matches(taken, grade));
	}

	public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ItemPredicate> item,
			Optional<String> grade) implements SimpleCriterionTrigger.SimpleInstance {

		public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
				EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
				ItemPredicate.CODEC.optionalFieldOf("item").forGetter(TriggerInstance::item),
				Codec.STRING.optionalFieldOf("grade").forGetter(TriggerInstance::grade)
		).apply(i, TriggerInstance::new));

		boolean matches(ItemStack taken, MutationGrade actual) {
			if (item.isPresent() && !item.get().test(taken)) {
				return false;
			}
			// An unreadable grade name never matches, rather than silently passing everything: a typo in
			// a datapack should leave the advancement unearnable, not hand it out on the first take.
			return grade.isEmpty() || MutationGrade.byName(grade.get()) == actual && actual.isMarked();
		}
	}
}
