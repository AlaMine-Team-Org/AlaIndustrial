package dev.alaindustrial.loot;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Config;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * MOD-119: a no-argument loot condition that passes only when {@link Config#bonusChestEnabled} is
 * {@code true}. Used to gate the NeoForge {@code neoforge:add_table} Global Loot Modifier on the mod
 * config flag — NeoForge's bonus-chest injection is data-driven, so it cannot check the flag
 * imperatively the way the Fabric {@code LootTableEvents.MODIFY} handler does.
 *
 * <p>Both loaders register this {@link #MAP_CODEC} into the vanilla {@code LOOT_CONDITION_TYPE} from the
 * shared {@code ModLootConditions.CONDITIONS} list (MOD-942), although only the NeoForge modifier names
 * it today. There is no {@code LootItemConditionType} wrapper — the registry stores the {@link MapCodec}
 * directly, and the condition itself carries it via {@link #codec()}.
 */
public final class BonusChestEnabledCondition implements LootItemCondition {

	public static final BonusChestEnabledCondition INSTANCE = new BonusChestEnabledCondition();

	/** Singleton codec for a stateless condition. */
	public static final MapCodec<BonusChestEnabledCondition> MAP_CODEC = MapCodec.unit(INSTANCE);

	private BonusChestEnabledCondition() {
	}

	@Override
	public MapCodec<? extends LootItemCondition> codec() {
		return MAP_CODEC;
	}

	@Override
	public boolean test(LootContext context) {
		return Config.bonusChestEnabled;
	}
}
