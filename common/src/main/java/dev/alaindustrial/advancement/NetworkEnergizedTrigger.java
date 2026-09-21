package dev.alaindustrial.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Fires the first time a player places a cable that leaves the resulting
 * {@code dev.alaindustrial.core.energy.EnergyNetwork} awake (a producer and a consumer are both reachable).
 * No vanilla trigger observes mod-internal network state, so this is the one custom criterion the
 * progression advancements need — see task MOD-015 and {@code dev.alaindustrial.block.CableBlock}.
 */
public class NetworkEnergizedTrigger extends SimpleCriterionTrigger<NetworkEnergizedTrigger.TriggerInstance> {
	@Override
	public Codec<TriggerInstance> codec() {
		return TriggerInstance.CODEC;
	}

	public void trigger(ServerPlayer player) {
		trigger(player, instance -> true);
	}

	public record TriggerInstance(Optional<Holder<LootItemCondition>> player)
			implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
				LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player)
		).apply(i, TriggerInstance::new));
	}
}
