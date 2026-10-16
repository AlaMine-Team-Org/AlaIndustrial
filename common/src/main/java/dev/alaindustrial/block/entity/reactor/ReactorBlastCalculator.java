package dev.alaindustrial.block.entity.reactor;

import dev.alaindustrial.core.structure.RoomValidator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * How a reactor blast reads the world (MOD-736): the shell holds, the rest gives way, and every block the rays
 * decided to break is written down.
 *
 * <p><b>Terrain is softened, the shell is not.</b> In stone a vanilla ray loses about seven power per block, so
 * even the ceiling blast left a hole six blocks wide underground — the player saw a firecracker. Here every
 * breakable block outside the shell counts as at most {@code terrainCap}, obsidian included (owner decision 2 of
 * MOD-736: there is no bunker). Fluids too: with water's own 100 a reactor sunk in a box of water exploded without
 * touching anything (owner playtest), so the blast now tears the water out and carves the floor below it, and the
 * sea floods the crater afterwards. The shell keeps its own 30, which is what still lets a sealed room contain a
 * small accident; unbreakable blocks keep theirs, so bedrock still ends the world below.
 *
 * <p><b>The candidates are what the rays chose, not what was destroyed.</b> {@code shouldBlockExplode} is called
 * for every cell a ray still has power in — including air, which is skipped here — and BEFORE a protection mod
 * gets its say. The caller keeps only the candidates that are actually air afterwards; one that survived was
 * refused by something, and the aftermath must not touch it.
 */
public final class ReactorBlastCalculator extends ExplosionDamageCalculator {

	private final boolean hurtsEntities;
	private final float terrainCap;
	private final Set<BlockPos> candidates = new LinkedHashSet<>();

	/**
	 * @param hurtsEntities whether this blast damages and pushes entities — the primary blast does, the waves of
	 *                      the crater cascade do not (they break the base, they do not kill a player twice)
	 * @param terrainCap    highest resistance a breakable block outside the shell has
	 */
	public ReactorBlastCalculator(boolean hurtsEntities, float terrainCap) {
		this.hurtsEntities = hurtsEntities;
		this.terrainCap = Math.max(0.0f, terrainCap);
	}

	@Override
	public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter getter, BlockPos pos,
			BlockState state, FluidState fluid) {
		Optional<Float> vanilla = super.getBlockExplosionResistance(explosion, getter, pos, state, fluid);
		if (vanilla.isEmpty() || RoomValidator.kindOf(state).isShell()
				|| state.getDestroySpeed(getter, pos) < 0) {
			return vanilla;
		}
		return Optional.of(Math.min(vanilla.get(), terrainCap));
	}

	@Override
	public boolean shouldBlockExplode(Explosion explosion, BlockGetter getter, BlockPos pos, BlockState state,
			float power) {
		boolean explode = super.shouldBlockExplode(explosion, getter, pos, state, power);
		if (explode && !state.isAir()) {
			candidates.add(pos.immutable());
		}
		return explode;
	}

	@Override
	public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
		return hurtsEntities && super.shouldDamageEntity(explosion, entity);
	}

	@Override
	public float getKnockbackMultiplier(Entity entity) {
		return hurtsEntities ? super.getKnockbackMultiplier(entity) : 0.0f;
	}

	/** Every non-air cell a ray chose to break, once each, in the order the rays first reached them. */
	public Set<BlockPos> candidates() {
		return candidates;
	}
}
