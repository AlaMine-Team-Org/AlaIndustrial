package dev.alaindustrial.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fires when a reactor reaches one of the steps the nuclear advancement branch marks (MOD-473).
 *
 * <p>One trigger with a {@code milestone} field rather than three triggers: the three events differ
 * only in which line of the room's own tick raised them, and a registry id per line would be three
 * registrations, three suppliers and three loader-parity pairs for no gain.
 *
 * <p><b>Who gets credited.</b> None of these moments has a player standing in it — a room seals, a
 * buffer fills and water boils on the controller's tick, with nobody necessarily in the chunk. The
 * controller does know its {@code owner} (it {@code tracksOwner()}), so the credit goes to the player
 * who placed it, and only while they are online; see {@code ModCriteria.fireReactorMilestone}. Handing
 * it to the nearest entity instead would award a reactor to whoever walked past it.
 */
public class ReactorMilestoneTrigger extends SimpleCriterionTrigger<ReactorMilestoneTrigger.TriggerInstance> {

	@Override
	public Codec<TriggerInstance> codec() {
		return TriggerInstance.CODEC;
	}

	/** Called with the step the reactor just reached. */
	public void trigger(ServerPlayer player, ReactorMilestone milestone) {
		trigger(player, instance -> instance.matches(milestone));
	}

	public record TriggerInstance(Optional<ContextAwarePredicate> player, String milestone)
			implements SimpleCriterionTrigger.SimpleInstance {

		public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
				EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
				Codec.STRING.fieldOf("milestone").forGetter(TriggerInstance::milestone)
		).apply(i, TriggerInstance::new));

		boolean matches(ReactorMilestone actual) {
			// An unreadable name never matches, rather than matching everything: a typo in a datapack
			// should leave that advancement unearnable, not hand it out on the first reactor to run.
			return ReactorMilestone.byId(milestone) == actual;
		}
	}
}
