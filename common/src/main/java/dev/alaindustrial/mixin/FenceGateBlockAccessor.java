package dev.alaindustrial.mixin;

import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor to the wood type of a {@link FenceGateBlock} (MOD-872): the sounds a gate makes when the town's builder
 * drone opens and shuts it on its route. The field is private with no getter (26.3 — checked with javap).
 */
@Mixin(FenceGateBlock.class)
public interface FenceGateBlockAccessor {
	@Accessor("type")
	WoodType alaindustrial$getType();
}
