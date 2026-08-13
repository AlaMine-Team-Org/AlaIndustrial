package dev.alaindustrial.registry;

import dev.alaindustrial.block.ChargePadBlock;
import dev.alaindustrial.block.ChargePadState;
import net.minecraft.world.level.block.Block;
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
	 * Light emission of a running Mob Repeller (MOD-278): <b>14</b> — the vanilla torch level, one
	 * above the machines' 13. The block is base lighting as much as it is defence: a player who wires
	 * one up expects the yard around it to stop being pitch dark, and 14 is the number every player
	 * already has a feel for from torches.
	 */
	public static int repellerLight(BlockState state) {
		return state.getValue(BlockStateProperties.LIT) ? 14 : 0;
	}

	/**
	 * Per-state light emission for the Charging Station (MOD-274). Unlike {@link #litLight} this reads a
	 * four-valued {@code state} property, so the levels live on {@link ChargePadState} itself rather than
	 * as magic numbers here — the enum is where "what is the station telling the player" is decided.
	 *
	 * <p>Lives in this shared file for the same reason {@code litLight} does: a per-loader copy is exactly
	 * how MOD-157 left a lit generator dark on NeoForge.
	 */
	public static int chargePadLight(BlockState state) {
		return state.getValue(ChargePadBlock.STATE).lightLevel();
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

	/**
	 * The wall variant of the Enriched Uranium Torch (MOD-085): the torch chain plus the vanilla
	 * {@code wallVariant} mirroring — it drops and is named as the STANDING torch, so it needs no item
	 * and no lang key of its own.
	 *
	 * <p><b>Why it is here and not in the loader files (MOD-403).</b> Both loaders used to spell this
	 * override out themselves, each reading its own handle for the standing torch
	 * ({@code ENRICHED_URANIUM_TORCH.getLootTable()} on Fabric, {@code ENRICHED_URANIUM_TORCH.get()....}
	 * on NeoForge) — two copies of one rule, exactly the drift class MOD-190 removed everywhere else.
	 * Reading it through {@link ModContent} makes the rule loader-neutral, at the cost of one ordering
	 * requirement that was already load-bearing on both sides: the standing torch must be registered
	 * first. {@code ContentManifest.BLOCKS} states that order once, for both loaders.
	 */
	public static BlockBehaviour.Properties applyWallTorch(BlockBehaviour.Properties p) {
		Block standing = ModContent.ENRICHED_URANIUM_TORCH.get();
		return applyTorch(p)
				.overrideLootTable(standing.getLootTable())
				.overrideDescription(standing.getDescriptionId());
	}
}
