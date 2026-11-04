package dev.alaindustrial.registry.neoforge;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModLootConditions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge loot-condition registration: a replay of the shared {@link ModLootConditions#CONDITIONS} list
 * (MOD-119, MOD-942), mirroring {@link ModParticlesNeoForge}.
 *
 * <p>NeoForge freezes the vanilla {@code LOOT_CONDITION_TYPE} registry before mod construction, so the
 * codecs declared in {@code common} cannot self-register there; this {@link DeferredRegister} adds them
 * on the mod bus. There is no handle to bind — a loot table names a condition by id.
 */
public final class ModLootConditionsNeoForge {

	public static final DeferredRegister<MapCodec<? extends LootItemCondition>> LOOT_CONDITION_TYPES =
			DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, Industrialization.MOD_ID);

	/** Every shared entry, queued the moment this class loads. */
	private static final List<DeferredHolder<MapCodec<? extends LootItemCondition>, ?>> REGISTERED = registerAll();

	private static List<DeferredHolder<MapCodec<? extends LootItemCondition>, ?>> registerAll() {
		List<DeferredHolder<MapCodec<? extends LootItemCondition>, ?>> registered = new ArrayList<>();
		for (ModLootConditions.ConditionDef def : ModLootConditions.CONDITIONS) {
			registered.add(LOOT_CONDITION_TYPES.register(def.id(), def::codec));
		}
		return List.copyOf(registered);
	}

	/** Class-load trigger for the {@code @Mod} ctor; also checks the replay covered the whole list. */
	public static void init() {
		if (REGISTERED.size() != ModLootConditions.CONDITIONS.size()) {
			throw new IllegalStateException("ModLootConditionsNeoForge registered " + REGISTERED.size() + " of "
					+ ModLootConditions.CONDITIONS.size() + " shared loot-condition types");
		}
	}

	private ModLootConditionsNeoForge() {
	}
}
