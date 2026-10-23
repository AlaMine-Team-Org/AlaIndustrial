package dev.alaindustrial.mixin;

import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor to the block set of a {@link TrapDoorBlock} (MOD-872): whether a hand opens it and which sounds it makes.
 * The town's builder drone opens a trapdoor on its route as a player would, and never one only redstone opens (an iron
 * trapdoor). The field is private and its getter protected (26.3 — checked with javap).
 */
@Mixin(TrapDoorBlock.class)
public interface TrapDoorBlockAccessor {
	@Accessor("type")
	BlockSetType alaindustrial$getType();
}
