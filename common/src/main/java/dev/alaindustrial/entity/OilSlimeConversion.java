package dev.alaindustrial.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.registry.ModMobs;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.cubemob.Slime;
import org.jspecify.annotations.Nullable;

/**
 * A vanilla slime that stands in crude oil long enough turns into an {@link OilSlime} of the same size
 * (MOD-767, docs/mobs/oil_slime.md).
 *
 * <p>The seat is crude oil's own {@code Fluid#entityInside}: vanilla calls it once per occupied fluid cell
 * per tick, so {@link #soak} keys on the cell under the slime's feet and counts a tick only there. The soak
 * is <em>uninterrupted</em> time: a slime out of oil for longer than {@link #GRACE_TICKS} starts again.
 *
 * <p>The count lives in server memory, not in the slime's saved data — a server restart in the middle of a
 * bath starts it again. For a soak of seconds that is invisible, and it keeps a vanilla entity's save free of
 * anything this mod would have to migrate later.
 *
 * <p>Ordinary slimes drown in oil like everything else does, and the air bar of a small one runs out before
 * the default soak ends; a slime that is soaking therefore keeps its air — the oil goes in, it does not choke.
 */
public final class OilSlimeConversion {

	/** A slime out of oil for longer than this loses its soak. */
	static final int GRACE_TICKS = 20;
	/** The last ticks of the soak, when the slime smokes and bubbles as a warning. */
	static final int TELL_TICKS = 100;

	private static final Map<Slime, Soak> SOAKING = new WeakHashMap<>();

	private OilSlimeConversion() {
	}

	/** One slime's soak: uninterrupted ticks in oil and the game time it was last seen there. */
	private static final class Soak {
		int ticks;
		long lastSeen;
	}

	/**
	 * Counts one tick of {@code entity} standing in the oil cell {@code pos}; turns it when the soak is full.
	 * Called from crude oil's {@code entityInside} on both sides — only the server counts.
	 */
	public static void soak(ServerLevel level, BlockPos pos, Entity entity) {
		if (entity instanceof Slime slime && pos.equals(slime.blockPosition())) {
			soakTick(level, slime, level.getGameTime());
		}
	}

	/**
	 * One tick of {@code slime} standing in oil at game time {@code now} — {@link #soak} with the clock made
	 * explicit, so a test can run a whole soak in one call. Returns the oil slime it turned into, or null.
	 */
	public static @Nullable OilSlime soakTick(ServerLevel level, Slime slime, long now) {
		if (!Config.oilSlimeConversion || slime.getType() != EntityTypes.SLIME || slime.isRemoved()
				|| slime.isDeadOrDying()) {
			return null;
		}
		slime.setAirSupply(slime.getMaxAirSupply());
		Soak soak = SOAKING.computeIfAbsent(slime, s -> new Soak());
		if (soak.ticks > 0 && soak.lastSeen == now) {
			return null;
		}
		if (now - soak.lastSeen > GRACE_TICKS) {
			soak.ticks = 0;
		}
		soak.lastSeen = now;
		soak.ticks++;
		int needed = soakTicks();
		if (soak.ticks >= needed) {
			SOAKING.remove(slime);
			return convert(level, slime);
		}
		if (needed - soak.ticks <= TELL_TICKS && soak.ticks % 5 == 0) {
			tell(level, slime);
		}
		return null;
	}

	/** Ticks of uninterrupted oil a slime needs, from the knob. */
	public static int soakTicks() {
		return Math.max(1, Config.oilSlimeSoakSeconds) * 20;
	}

	/** Uninterrupted ticks {@code slime} has soaked so far (0 for a slime that is not soaking) — for tests. */
	public static int soakedTicks(Slime slime) {
		Soak soak = SOAKING.get(slime);
		return soak == null ? 0 : soak.ticks;
	}

	private static void tell(ServerLevel level, Slime slime) {
		double radius = slime.getBbWidth() * 0.5;
		level.sendParticles(ParticleTypes.LARGE_SMOKE, slime.getX(), slime.getY() + slime.getBbHeight(),
				slime.getZ(), 2, radius, 0.1, radius, 0.01);
		level.sendParticles(ParticleTypes.SQUID_INK, slime.getX(), slime.getY() + slime.getBbHeight() * 0.5,
				slime.getZ(), 3, radius, slime.getBbHeight() * 0.3, radius, 0.02);
	}

	private static @Nullable OilSlime convert(ServerLevel level, Slime slime) {
		int size = slime.getSize();
		OilSlime converted = slime.convertTo(ModMobs.OIL_SLIME.type(), ConversionParams.single(slime, false, false),
				EntitySpawnReason.CONVERSION, oil -> oil.setSize(size, true));
		if (converted != null) {
			level.playSound(null, converted.getX(), converted.getY(), converted.getZ(),
					SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.6F);
			level.sendParticles(ParticleTypes.SQUID_INK, converted.getX(), converted.getY() + 0.3,
					converted.getZ(), 12 * size, converted.getBbWidth() * 0.5, 0.2, converted.getBbWidth() * 0.5,
					0.05);
		}
		return converted;
	}
}
