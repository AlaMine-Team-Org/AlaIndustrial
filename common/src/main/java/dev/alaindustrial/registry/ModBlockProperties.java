package dev.alaindustrial.registry;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;

/**
 * Loader-neutral block-property fragments shared by the Fabric and NeoForge registration files.
 * Each loader's {@code ModBlocks} / {@code ModBlocksNeoForge} previously carried its own copy of
 * the per-state light helper and the torch property chain; the two copies drifted, which is how a
 * lit generator stayed dark on NeoForge while glowing on Fabric (MOD-157). Centralising the loader-
 * neutral fragments here makes that drift class of bug impossible by construction: there is one
 * place to edit, visible to both loaders.
 *
 * <p>What does <b>not</b> live here: the per-block {@code strength/sound/noOcclusion} chains. Those
 * stay per-loader because they are paired with the registration call ({@code setId} on Fabric,
 * {@code DeferredRegister} on NeoForge), and a shared {@code UnaryOperator<Properties>} helper would
 * still need to be applied by each loader — a cosmetic win only. The fragments below are the ones
 * that are genuinely loader-neutral AND were the actual drift source.
 */
public final class ModBlockProperties {
	private ModBlockProperties() {
	}

	/**
	 * Per-state light emission for fuel-burning blocks (MOD-013): a working block ({@code lit=true})
	 * glows at light level 13 like a lit vanilla furnace; idle ({@code lit=false}) emits none. Applied
	 * to {@code generator}, {@code geothermal_generator} and {@code iron_furnace}. The EU-powered
	 * processing machines (macerator/furnace/extractor/compressor) share the {@code lit} state but burn
	 * no fuel, so they stay dark.
	 *
	 * <p>This used to be duplicated as a private {@code litLight} helper in both {@code ModBlocks}
	 * (Fabric) and {@code ModBlocksNeoForge}; the NeoForge copy was forgotten when MOD-013 landed,
	 * leaving lit generators dark (MOD-157). One shared method reference closes the drift class.
	 */
	public static int litLight(BlockState state) {
		return state.getValue(BlockStateProperties.LIT) ? 13 : 0;
	}

	/**
	 * The vanilla-torch property chain (MOD-085), mirroring {@code Blocks.TORCH} exactly. Loader-neutral
	 * fragments only — does NOT call {@code setId} (the Fabric side adds it from its key; NeoForge's
	 * {@code DeferredRegister} applies it from the deferred key). Both loaders call this and then layer
	 * their own {@code setId} on top.
	 *
	 * <p>No collision, instant break (breaks by hand, no tool gate), light level 14 (identical to the
	 * vanilla torch), WOOD sound, {@code DESTROY} push reaction (a piston breaks it), no occlusion.
	 */
	public static BlockBehaviour.Properties torchBase() {
		return BlockBehaviour.Properties.of().noCollision().instabreak()
				.lightLevel(state -> 14).sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY).noOcclusion();
	}
}
