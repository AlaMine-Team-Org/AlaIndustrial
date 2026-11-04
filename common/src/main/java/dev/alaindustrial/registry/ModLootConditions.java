package dev.alaindustrial.registry;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.loot.BonusChestEnabledCondition;
import dev.alaindustrial.loot.StructureLootEnabledCondition;
import java.util.List;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Every loot-condition type the mod registers, declared once for both loaders (MOD-942, the MOD-555
 * pattern). The conditions are stateless {@code MapCodec.unit} singletons, so, as with
 * {@link ModParticles}, there is no handle to bind: each loader only publishes the same codec into
 * {@code LOOT_CONDITION_TYPE} — Fabric eagerly in {@code onInitialize}, NeoForge through a
 * {@code DeferredRegister} (see {@code ModLootConditionsNeoForge}).
 *
 * <p>Both loaders must register a condition named by a loot table they load: an unknown {@code type}
 * fails the table, and an injected sub-table that fails to load is a pool that is silently missing.
 */
public final class ModLootConditions {

	/**
	 * One loot-condition type.
	 *
	 * @param id    registry path ({@code alaindustrial:<id>})
	 * @param codec the condition's codec — the SAME object on both loaders
	 */
	public record ConditionDef(String id, MapCodec<? extends LootItemCondition> codec) {
	}

	/** Every loot-condition type, in one shared registration order. Both loaders replay this list. */
	public static final List<ConditionDef> CONDITIONS = List.of(
			new ConditionDef("bonus_chest_enabled", BonusChestEnabledCondition.MAP_CODEC),
			new ConditionDef("structure_loot_enabled", StructureLootEnabledCondition.MAP_CODEC));

	private ModLootConditions() {
	}
}
