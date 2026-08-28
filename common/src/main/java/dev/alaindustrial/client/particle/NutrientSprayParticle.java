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
	 * Downward pull per tick. Well under vanilla's falling-dust (0.06 against 0.1): the droplets are
	 * fine mist thrown by a spinning head, so they should hang long enough to be seen crossing the
	 * plot before they land.
	 */
	private static final float SPRAY_GRAVITY = 0.06F;

	/**
	 * Air drag per tick. High enough that the outward fling decays into a fall within a second or so,
	 * which is what turns a straight line into an arc.
	 */
	private static final float SPRAY_FRICTION = 0.94F;

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
		// Varied lifetimes so a burst breaks up instead of vanishing as one block of motes.
		this.lifetime = 24 + random.nextInt(16);
		this.quadSize = 0.09F + random.nextFloat() * 0.05F;
		// Tint the whole spray toward the solution's own green, with a little spread so the mist has
		// depth rather than reading as one flat colour.
		float shade = 0.82F + random.nextFloat() * 0.18F;
		this.rCol = 0.35F * shade;
		this.gCol = 0.85F * shade;
		this.bCol = 0.48F * shade;
		this.alpha = 0.9F;
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
