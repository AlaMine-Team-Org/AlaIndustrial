package dev.alaindustrial.registry;

import dev.alaindustrial.block.ChargePadBlock;
import dev.alaindustrial.block.ChargePadState;
import dev.alaindustrial.block.ElectricHeaterBlock;
import dev.alaindustrial.compat.LineBlockProps;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

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
 * only to catch it after the fact. So the per-block chains now live once in the manifest — since MOD-711
 * each in its own {@link ContentManifest.BlockDef} declaration — applied by both loaders via
 * {@link #applyTorch} / the declared operators — closing the drift class for the whole block definition,
 * not just the light helper. {@code setId} (Fabric) and {@code requiresCorrectToolForDrops} base still
 * layer per loader.
 */
public final class ModBlockProperties {
	private ModBlockProperties() {
	}

	/**
	 * The transport lines flowing fluid washes away (MOD-661): every cable, pipe and the monitoring wire,
	 * whatever shape it has taken. {@code ContentManifest}'s {@code block(...)} builder applies
	 * {@link LineBlockProps#washedAwayByFluids} to each of them, so the list is the one place a new
	 * transport line is declared washable.
	 *
	 * <p>The lines decide washing differently, which is why this is a list and not a property value: 26.2
	 * washes whatever does not block motion, and reads that from the collision box — a straight pipe is
	 * thin and washed, but one with arms up and down is a full block tall and counted solid — so that line
	 * forces these blocks non-solid. 26.3 asks the {@code #minecraft:washed_away_by_fluids} tag instead,
	 * which {@code data/minecraft/tags/block/washed_away_by_fluids.json} fills.
	 *
	 * <p>The 26.3 tag lists exactly these ids (16); on 26.2 {@link LineBlockProps#washedAwayByFluids} forces each
	 * of them non-solid. {@code FluidWashScenarios} asks the line's own mechanism about every mod block and
	 * fails unless it marks exactly this list (MOD-703), and it keeps its own hand-written copy of the ids,
	 * washes every one in the world and fails when this list differs from it, so a new washed-away block is
	 * added to the test on purpose (MOD-726: {@code fluid_pipe_advanced} was missing from the 26.3 tag).
	 */
	public static final List<String> WASHED_AWAY_BY_FLUIDS = List.of(
			"copper_cable", "tin_cable", "gold_cable", "electrum_cable",
			"insulated_copper_cable", "insulated_tin_cable", "insulated_gold_cable", "insulated_electrum_cable",
			"item_pipe", "item_pipe_advanced", "fluid_pipe", "fluid_pipe_advanced", "reinforced_fluid_pipe",
			"steam_pipe", "reinforced_steam_pipe", "smart_wire");

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
	 * Per-rung light emission for the Electric Heater (MOD-418). Like {@link #chargePadLight} this reads
	 * a four-valued property instead of {@code lit}, and for the same reason the levels live on
	 * {@link dev.alaindustrial.block.HeaterGlow} rather than here: the enum is where "how hot does this
	 * look" is decided, and a heater ramps rather than switches.
	 */
	public static int heaterLight(BlockState state) {
		return state.getValue(ElectricHeaterBlock.GLOW).lightLevel();
	}

	/**
	 * The vanilla-torch chain (MOD-085) as an operator over an existing {@code Properties} (MOD-190), so
	 * a {@link ContentManifest.BlockDef} can apply it to the loader-provided base (Fabric passes a base
	 * carrying {@code setId}; NeoForge a bare one). Mirrors {@code Blocks.TORCH} exactly and stays
	 * loader-neutral — it does NOT call {@code setId}.
	 *
	 * <p>No collision, instant break (breaks by hand, no tool gate), light level 14 (identical to the
	 * vanilla torch), WOOD sound, popped by a piston (it breaks rather than moves), no occlusion.
	 *
	 * <p>Superseded {@code torchBase()}, removed in MOD-191: once both loaders went through
	 * the shared manifest it had no callers left anywhere in the repo.
	 */
	public static BlockBehaviour.Properties applyTorch(BlockBehaviour.Properties p) {
		return LineBlockProps.popsOnPush(p).noCollision().instabreak().lightLevel(state -> 14)
				.sound(SoundType.WOOD).noOcclusion();
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
