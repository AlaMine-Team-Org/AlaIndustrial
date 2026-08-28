package dev.alaindustrial.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * A droplet of nutrient solution thrown by the Sprinkler (MOD-525).
 *
 * <p><b>Why not a vanilla particle.</b> The first version used {@code HAPPY_VILLAGER} — bone meal's
 * own sparkle — and it read wrong: those motes hang in place and drift upward, so a sprinkler looked
 * like it was leaking magic rather than throwing liquid. What a spray needs is an arc: flung
 * outward, slowed by air, pulled down, and landing. That is three lines of physics vanilla has no
 * particle for, so this one owns them.
 *
 * <p>The behaviour is entirely in the fields the base class already ticks — {@code gravity},
 * {@code friction} and {@code hasPhysics} — rather than in an overridden {@code tick()}. Vanilla's
 * own move-and-collide is what makes a droplet stop on the ground instead of sinking through a
 * field, and reimplementing it here would be a second, worse copy.
 */
public class NutrientSprayParticle extends SingleQuadParticle {

	/**
	 * Downward pull per tick.
	 *
	 * <p>Above vanilla's falling-dust (0.13 against 0.1), and deliberately so. Vanilla scales this by
	 * 0.04 per tick and then applies {@link #SPRAY_FRICTION} to the vertical speed too, so the two
	 * settle at a terminal fall of {@code 0.627 × gravity} blocks per tick: at the 0.06 this started
	 * with, that is 0.75 blocks a second — slower than the droplets expired, so the whole spray died
	 * in mid-air at head height and never reached the crop. At 0.13 it falls ~1.6 blocks a second and
	 * lands inside its own flight, which is the entire point of a sprinkler.
	 */
	private static final float SPRAY_GRAVITY = 0.13F;

	/**
	 * Air drag per tick. High enough that the outward fling decays into a fall within a second or so,
	 * which is what turns a straight line into an arc.
	 */
	private static final float SPRAY_FRICTION = 0.94F;

	/** Opacity while in flight; the fade below takes it down from here. */
	private static final float PEAK_ALPHA = 0.9F;

	/**
	 * Ticks of fade at the end of life. A droplet lands well before its lifetime is up and then lies
	 * on the ground — which is what makes the plot read as fertilised rather than as rained on — so
	 * without a fade the settled mist would blink out all at once.
	 */
	private static final int FADE_TICKS = 20;

	protected NutrientSprayParticle(ClientLevel level, double x, double y, double z,
			double xd, double yd, double zd, SpriteSet sprites, RandomSource random) {
		super(level, x, y, z, xd, yd, zd, sprites.get(random));
		this.gravity = SPRAY_GRAVITY;
		this.friction = SPRAY_FRICTION;
		// Collide with the world: a droplet must come to rest on the crop it was aimed at rather than
		// falling through the ground and on into the void.
		this.hasPhysics = true;
		// The fling comes from the caller; the base class only ever slows it down from here.
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		// Varied lifetimes so a burst breaks up instead of vanishing as one block of motes. Long enough
		// to outlive the flight by a wide margin: a droplet reaches the ground around tick 30, and the
		// second or two it then spends lying there is what a player reads as fertiliser settling.
		this.lifetime = 55 + random.nextInt(25);
		this.quadSize = 0.09F + random.nextFloat() * 0.05F;
		// Tint the whole spray toward the solution's own green, with a little spread so the mist has
		// depth rather than reading as one flat colour.
		float shade = 0.82F + random.nextFloat() * 0.18F;
		this.rCol = 0.35F * shade;
		this.gCol = 0.85F * shade;
		this.bCol = 0.48F * shade;
		this.alpha = PEAK_ALPHA;
	}

	/**
	 * Vanilla's physics, plus a fade at the end.
	 *
	 * <p>The movement itself is still entirely the base class's — this override adds no motion of its
	 * own, only opacity, so the "do not reimplement move-and-collide" note above still holds.
	 */
	@Override
	public void tick() {
		super.tick();
		int remaining = this.lifetime - this.age;
		if (remaining < FADE_TICKS) {
			setAlpha(PEAK_ALPHA * Math.max(0.0F, (float) remaining / FADE_TICKS));
		}
	}

	/**
	 * The particle atlas, translucent.
	 *
	 * <p>Not {@code TRANSLUCENT_TERRAIN}: that name selects the BLOCK atlas, and a particle sprite's
	 * UVs read against it land on unrelated block art — which is exactly what shipped first, as green
	 * and purple squares that looked like broken pixels. The sprite comes from
	 * {@code assets/.../particles/nutrient_spray.json}, so the layer has to be a particle one.
	 */
	@Override
	public Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	/** Binds the particle type to this class; the sprite set comes from the loader's registration. */
	public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level,
				double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
			return new NutrientSprayParticle(level, x, y, z, xd, yd, zd, sprites, random);
		}
	}
}
