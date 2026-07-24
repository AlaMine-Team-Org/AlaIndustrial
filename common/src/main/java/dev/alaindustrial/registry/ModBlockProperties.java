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
 * <p><b>Per-block {@code strength/sound/noOcclusion} chains (MOD-190).</b> These used to be inlined
 * per loader, deemed a "cosmetic" duplication not worth centralising. MOD-190 revisits that: the same
 * drift that left a lit generator dark on NeoForge (MOD-157, a {@code litLight} chain divergence) can
 * silently hit any {@code strength}/{@code sound} value too, and {@code loader_parity_check} exists
 * only to catch it after the fact. So the per-block chains now live once in
 * {@link ContentManifest#BLOCK_PROPS}, applied by both loaders via {@link #applyTorch} / the map
 * operators — closing the drift class for the whole block definition, not just the light helper.
 * {@code setId} (Fabric) and {@code requiresCorrectToolForDrops} base still layer per loader.
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
	 * The vanilla-torch chain (MOD-085) as an operator over an existing {@code Properties} (MOD-190), so
	 * {@link ContentManifest#BLOCK_PROPS} can apply it to the loader-provided base (Fabric passes a base
	 * carrying {@code setId}; NeoForge a bare one). Mirrors {@code Blocks.TORCH} exactly and stays
	 * loader-neutral — it does NOT call {@code setId}.
	 *
	 * <p>No collision, instant break (breaks by hand, no tool gate), light level 14 (identical to the
	 * vanilla torch), WOOD sound, {@code DESTROY} push reaction (a piston breaks it), no occlusion.
	 *
	 * <p>Superseded {@code torchBase()}, removed in MOD-191: once both loaders went through
	 * {@code BLOCK_PROPS} it had no callers left anywhere in the repo.
	 */
	public static BlockBehaviour.Properties applyTorch(BlockBehaviour.Properties p) {
		return p.noCollision().instabreak().lightLevel(state -> 14).sound(SoundType.WOOD)
				.pushReaction(PushReaction.DESTROY).noOcclusion();
	}
}
