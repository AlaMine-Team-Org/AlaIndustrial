package dev.alaindustrial.registry.neoforge;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.loot.BonusChestEnabledCondition;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge loot-condition registration (MOD-022 facade, MOD-119). NeoForge freezes the vanilla
 * {@code LOOT_CONDITION_TYPE} registry before mod construction, so the loader-neutral condition in
 * {@code common} ({@link BonusChestEnabledCondition}) cannot self-register there. This
 * {@link DeferredRegister} adds its {@link MapCodec} on the mod bus.
 *
 * <p>Only NeoForge registers it, because only the NeoForge {@code neoforge:add_table} modifier JSON
 * references {@code alaindustrial:bonus_chest_enabled}; the Fabric path gates the config flag
 * imperatively in its {@code LootTableEvents.MODIFY} handler and never names this condition.
 */
public final class ModLootConditionsNeoForge {

	public static final DeferredRegister<MapCodec<? extends LootItemCondition>> LOOT_CONDITION_TYPES =
			DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, Industrialization.MOD_ID);

	/** {@code alaindustrial:bonus_chest_enabled} — gates the bonus-chest GLM on the config flag. */
	public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<BonusChestEnabledCondition>>
			BONUS_CHEST_ENABLED =
			LOOT_CONDITION_TYPES.register("bonus_chest_enabled", () -> BonusChestEnabledCondition.MAP_CODEC);

	private ModLootConditionsNeoForge() {
	}
}
