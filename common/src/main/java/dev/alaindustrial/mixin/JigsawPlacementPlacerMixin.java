package dev.alaindustrial.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.alaindustrial.worldgen.VillagePoolInjector;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import dev.alaindustrial.Industrialization;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Caps the Industrialist house at one per village (MOD-062).
 *
 * <p>26.2 has no native "max N of this element per structure" — {@code JigsawPlacement.Placer} builds
 * a shuffled candidate list per house slot and picks the first that fits, never counting by type
 * (verified against the 26.2 sources). Our house has a small footprint, so it fits — and is picked —
 * in tight slots that reject the larger vanilla houses, appearing several times per village. This
 * wraps the {@code getShuffledTemplates} call that feeds the candidate list and, once our house is
 * already among the pieces placed so far in this village, filters it out of every later slot. The
 * decision itself lives in {@link VillagePoolInjector#withoutHouseIfPresent} (unit-tested).
 *
 * <p>Loader-neutral: this common mixin is loaded on both Fabric and NeoForge via the shared
 * {@code alaindustrial.worldgen.mixins.json}; MixinExtras ({@code @WrapOperation}) is bundled by both.
 *
 * <p>Best-effort by design (MOD-176): the config is {@code required: false} with
 * {@code defaultRequire: 0}, so if another mod transforms {@code tryPlacingChildren} and this wrap
 * cannot bind, the game keeps loading and only the one-house-per-village cap degrades — a hard
 * startup crash for the whole modpack is never an acceptable cost of a cosmetic cap.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
public abstract class JigsawPlacementPlacerMixin {

	@Shadow
	@Final
	private List<? super PoolElementStructurePiece> pieces;

	/** Fires the "house placed" info log at most once per village assembly (diagnostic aid). */
	@Unique
	private boolean alaindustrial$housePlacementLogged;

	@WrapOperation(
			method = "tryPlacingChildren",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/levelgen/structure/pools/StructureTemplatePool;getShuffledTemplates(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"))
	private List<StructurePoolElement> alaindustrial$capIndustrialistHouse(
			StructureTemplatePool pool, RandomSource random, Operation<List<StructurePoolElement>> original) {
		List<StructurePoolElement> candidates = original.call(pool, random);
		// This wrap fires for EVERY jigsaw structure of every mod; only the five vanilla village
		// house pools ever carry our element. Skip the placed-pieces scan for everything else so
		// foreign structures pay one cheap candidate-list check, not O(pieces) per expansion step.
		if (!VillagePoolInjector.containsHouse(candidates)) {
			return candidates;
		}
		return VillagePoolInjector.withoutHouseIfPresent(candidates, alaindustrial$housePlaced());
	}

	private boolean alaindustrial$housePlaced() {
		for (Object placed : this.pieces) {
			if (placed instanceof PoolElementStructurePiece piece && VillagePoolInjector.isHouse(piece.getElement())) {
				if (!this.alaindustrial$housePlacementLogged) {
					this.alaindustrial$housePlacementLogged = true;
					Industrialization.LOGGER.info("[MOD-062] Industrialist house placed in a village (further copies capped)");
				}
				return true;
			}
		}
		return false;
	}
}
