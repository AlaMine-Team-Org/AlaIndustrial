package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.registry.ModProfessions;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge registration of the Industrialist villager profession + its POI (MOD-062). Mirrors the
 * Fabric eager path ({@code IndustrializationFabric#registerVillagerProfession()}) through two
 * {@link DeferredRegister}s — built-in registries are frozen before NeoForge mod init, so eager
 * {@code Registry.register} would throw {@code already frozen}.
 *
 * <p>The POI's matching states are pulled from the workbench block via the deferred supplier (the
 * block resolves before POI registration fires on the mod bus — same ordering as vanilla worldgen
 * callbacks). NeoForge fills the internal blockstate→POI map automatically through its registry
 * callback ({@code NeoForgeRegistryCallbacks.PoiTypeCallbacks} — verified 26.2 sources), so unlike
 * Fabric no helper is needed beyond the plain registration.
 */
public final class ModProfessionsNeoForge {
	private ModProfessionsNeoForge() {
	}

	public static final DeferredRegister<PoiType> POI_TYPES =
			DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Industrialization.MOD_ID);

	public static final DeferredRegister<VillagerProfession> PROFESSIONS =
			DeferredRegister.create(Registries.VILLAGER_PROFESSION, Industrialization.MOD_ID);

	public static final DeferredHolder<PoiType, PoiType> INDUSTRIALIST_POI =
			POI_TYPES.register("industrialist", () -> new PoiType(
					Set.copyOf(ModBlocksNeoForge.INDUSTRIAL_WORKBENCH.get().getStateDefinition().getPossibleStates()),
					ModProfessions.POI_MAX_TICKETS,
					ModProfessions.POI_VALID_RANGE));

	public static final DeferredHolder<VillagerProfession, VillagerProfession> INDUSTRIALIST =
			PROFESSIONS.register("industrialist", ModProfessions::createIndustrialist);
}
