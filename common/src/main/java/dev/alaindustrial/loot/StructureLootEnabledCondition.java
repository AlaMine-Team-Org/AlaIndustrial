package dev.alaindustrial.loot;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Config;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * MOD-942: a no-argument loot condition that passes only when {@link Config#structureLootEnabled} is
 * {@code true}. It sits on the single pool of each {@link StructureLoot} sub-table, so the switch is
 * read at roll time on both loaders alike — a reload of the config file reaches the next chest opened,
 * and a gametest can turn it off for one call.
 *
 * <p>Registered by both loaders from {@code ModLootConditions.CONDITIONS}.
 */
public final class StructureLootEnabledCondition implements LootItemCondition {

	public static final StructureLootEnabledCondition INSTANCE = new StructureLootEnabledCondition();

	/** Singleton codec for a stateless condition. */
	public static final MapCodec<StructureLootEnabledCondition> MAP_CODEC = MapCodec.unit(INSTANCE);

	private StructureLootEnabledCondition() {
	}

	@Override
	public MapCodec<? extends LootItemCondition> codec() {
		return MAP_CODEC;
	}

	@Override
	public boolean test(LootContext context) {
		return Config.structureLootEnabled;
	}
}
