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
 * {@code alaindustrial.mixins.json}; MixinExtras ({@code @WrapOperation}) is bundled by both.
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
		return VillagePoolInjector.withoutHouseIfPresent(original.call(pool, random), alaindustrial$housePlaced());
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
