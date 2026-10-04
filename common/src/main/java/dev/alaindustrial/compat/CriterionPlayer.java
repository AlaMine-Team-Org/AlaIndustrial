package dev.alaindustrial.compat;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * The optional {@code "player"} condition every advancement-criterion instance of the mod carries — a
 * version facade (MOD-704, ADR-036): every Minecraft line has a twin of this class with the same member,
 * {@link #FIELD}, and only its body differs. The three triggers ({@code NetworkEnergizedTrigger},
 * {@code MutationCompletedTrigger}, {@code ReactorMilestoneTrigger}) build their {@code TriggerInstance}
 * codec from it, so their codec lines are the same on every line; what still differs there is the type of
 * the {@code player} record component (a separate line of each record header) and its imports, because
 * {@code SimpleCriterionTrigger.SimpleInstance#player()} fixes that type per line.
 *
 * <p><b>This twin: Minecraft 26.3.</b> {@code SimpleInstance#player()} returns
 * {@code Optional<Holder<LootItemCondition>>} (javap of the 26.3 {@code minecraft-merged.jar}), and the
 * field is read with {@link LootItemCondition#CODEC} — one loot condition, inline or by reference, e.g.
 * {@code {"type": "minecraft:killed_by_player"}}. The 26.2 twin reads a {@code ContextAwarePredicate}
 * through {@code EntityPredicate.ADVANCEMENT_CODEC} (a list of conditions). The trigger's own codec lines
 * never name either.
 */
public final class CriterionPlayer {

	/** The {@code "player"} field, optional, under the key the advancement JSON uses on this line. */
	public static final MapCodec<Optional<Holder<LootItemCondition>>> FIELD =
			LootItemCondition.CODEC.optionalFieldOf("player");

	private CriterionPlayer() {
	}
}
