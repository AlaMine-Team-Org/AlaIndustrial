package dev.alaindustrial.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor to the live element list of a {@link StructureTemplatePool} (MOD-062 village injection).
 *
 * <p>Why {@code templates} and only {@code templates}: generation reads exclusively this list
 * ({@code getRandomTemplate}/{@code getShuffledTemplates}; weight = number of copies). The sibling
 * {@code rawTemplates} exists for codec round-trips only, and on a datapack-loaded pool it is an
 * immutable DFU list — writing to it would throw. Verified against the 26.2 sources.
 */
@Mixin(StructureTemplatePool.class)
public interface StructureTemplatePoolAccessor {
	@Accessor("templates")
	ObjectArrayList<StructurePoolElement> alaindustrial$getTemplates();
}
