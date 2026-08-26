package dev.alaindustrial.core.structure;

import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * What a reactor destroys, and how it warns first (MOD-469).
 *
 * <p>Two hazards share this code because they are the same act with different reach. A sealed room
 * that has been allowed to overheat melts its own CONTENTS — the floor, the plumbing, whatever was
 * carried inside — and keeps both its shell and its fuel racks, the containment doing exactly what it
 * was built for. A reactor running with no room at all melts the SCENERY around it, because there is
 * nothing between the rods and the world.
 *
 * <p><b>The scenery hazard radiates from the RACKS, not from the controller.</b> The controller is the
 * brain; the fuel is what is dangerous, which is also how radiation already models it. Centring on the
 * controller looked equivalent and was not: a controller sits in a wall, so its own reactor body
 * shadowed half the sphere and every melt landed on the one side it happened to face. Playtest,
 * 2026-08-26.
 *
 * <p><b>Every melt is announced at the block it will take, and never anywhere else.</b> A general mood
 * over the whole reactor — a rumble, a screen full of red — tells the player they are in trouble and
 * not what to do about it. Particles and a hiss at one specific block, a couple of seconds before it
 * goes, tell them to step off it. That is the entire difference between a hazard the player can play
 * against and a punishment they can only absorb.
 */
public final class ReactorMeltdown {

	private ReactorMeltdown() {
	}

	/**
	 * How many positions are tried before a round gives up.
	 *
	 * <p>A bare reactor standing in the open is mostly air, so a single random draw would usually find
	 * nothing and the hazard would look broken. Sixteen draws finds ground in any ordinary place and
	 * still costs nothing on the tick a reactor really is floating in a void — where skipping the round
	 * is the correct answer anyway, there being nothing to melt.
	 */
	private static final int PICK_ATTEMPTS = 16;

	/**
	 * A block the scenery-melting pass may take.
	 *
	 * <p>Air and anything holding a fluid are skipped because turning them to lava is not damage, it is
	 * landscaping. Reading the FLUID STATE rather than the deprecated {@code liquid()} also spares
	 * waterlogged blocks, which is the behaviour a player would expect anyway — water is what stops lava
	 * everywhere else in the game, and flooding a room to protect it is a tactic rather than an exploit
	 * (it costs the same buckets that would have cooled the reactor properly).
	 *
	 * <p>A negative destroy speed is the engine's own mark for "indestructible" — bedrock, barriers, the
	 * portal frame — and is read rather than matched against a list of names, so a modded unbreakable
	 * block is respected without this file ever hearing about it.
	 *
	 * <p><b>Anything built out of shielding alloy is exempt, in either hazard</b> — the reactor's own
	 * shell, its racks, its controller and button, and the shielding chest, all through
	 * {@link ModTags.Blocks#MELTPROOF}. A part crafted to survive a reactor survives this one too, and
	 * that exemption is also what lets a bare core be a long-term strategy rather than a fuse.
	 * Everything else in reach — the player's cables, their ordinary pipes, their ordinary chests, the
	 * ground under their feet — is fair game, and that is the price of the shortcut.
	 */
	public static boolean isMeltable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return false;
		}
		if (isMeltproof(state)) {
			return false;
		}
		return state.getDestroySpeed(level, pos) >= 0;
	}

	/**
	 * Whether this block survives a reactor's lava.
	 *
	 * <p><b>Read from a tag, not from the class hierarchy.</b> This began as a list of reactor classes
	 * and a playtest found the hole in minutes: a shielding chest standing beside a bare core melted,
	 * although it is crafted from the same shielding plate the reactor room is built from. From the
	 * player's side the rule is "shielding shields" — which Java class a block extends is invisible to
	 * them, and a rule they cannot see is a rule they will keep tripping over.
	 */
	public static boolean isMeltproof(BlockState state) {
		return state.is(ModTags.Blocks.MELTPROOF);
	}

	/**
	 * A block of scenery near a bare reactor, or {@code null} if this round found nothing.
	 *
	 * <p>Drawn from a cube rather than a sphere and then distance-tested, which is both the cheap way to
	 * sample a ball and the one that does not bias towards the centre the way rolling a radius does.
	 */
	@Nullable
	public static BlockPos pickSceneryVictim(ServerLevel level, BlockPos centre, int radius,
			RandomSource random) {
		if (radius <= 0) {
			return null;
		}
		BlockPos buried = null;
		for (int attempt = 0; attempt < PICK_ATTEMPTS; attempt++) {
			BlockPos at = centre.offset(
					random.nextInt(radius * 2 + 1) - radius,
					random.nextInt(radius * 2 + 1) - radius,
					random.nextInt(radius * 2 + 1) - radius);
			if (at.distSqr(centre) > (long) radius * radius || !isMeltable(level, at)) {
				continue;
			}
			// EXPOSED first — a cell with air over it, which is one the player can actually see.
			// Half of a sphere centred on a rack standing on the ground is underground, so a picker that
			// took the first meltable cell spent most of its rounds hollowing out the rock below and the
			// visible scar came out looking two blocks wide instead of five (playtest finding 4). Buried
			// hits are kept only as a fallback, so a core walled in on every side still does damage.
			if (level.getBlockState(at.above()).isAir()) {
				return at;
			}
			if (buried == null) {
				buried = at;
			}
		}
		return buried;
	}

	/**
	 * A block of the room's contents, or {@code null} if the interior is already bare.
	 *
	 * <p><b>An ordinary fluid pipe goes first, wherever it is in the room.</b> That is a teaching order,
	 * not a physical one: the pipe is the one thing in a reactor room that a player builds out of a part
	 * they already had rather than a part the reactor asked for, so it is the failure that best explains
	 * itself. Everything else is drawn at random.
	 *
	 * <p>Only the INTERIOR is offered — the caller passes the box the room last sealed, and the shell is
	 * the ring outside it. The controller, the ports, the outlets, the door and the glass all live on
	 * that ring, so the containment survives its own accident without a single name being listed here.
	 */
	@Nullable
	public static BlockPos pickContentsVictim(ServerLevel level, int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ, RandomSource random) {
		if (maxX < minX || maxY < minY || maxZ < minZ) {
			return null;
		}
		BlockPos pipe = findPipe(level, minX, minY, minZ, maxX, maxY, maxZ);
		if (pipe != null) {
			return pipe;
		}
		for (int attempt = 0; attempt < PICK_ATTEMPTS; attempt++) {
			BlockPos at = new BlockPos(
					minX + random.nextInt(maxX - minX + 1),
					minY + random.nextInt(maxY - minY + 1),
					minZ + random.nextInt(maxZ - minZ + 1));
			// The SAME meltability rule as the scenery pass, racks included. An earlier version made the
			// columns the meltdown's main course; the player's ruling is that a rack built around a
			// shielding plate does not melt anywhere, so what a runaway room eats is the floor, the
			// plumbing and whatever was carried inside — never the fuel itself.
			if (isMeltable(level, at)) {
				return at;
			}
		}
		return null;
	}

	/** The first ordinary fluid pipe standing inside the room, in a stable walk of the box. */
	@Nullable
	private static BlockPos findPipe(ServerLevel level, int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = minY; y <= maxY; y++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int x = minX; x <= maxX; x++) {
					if (level.getBlockState(cursor.set(x, y, z)).getBlock() instanceof FluidPipeBlock) {
						return cursor.immutable();
					}
				}
			}
		}
		return null;
	}

	/**
	 * Marks a block as about to go: flame, smoke and a hiss, at that block and nowhere else.
	 *
	 * <p>Runs on the tick the victim is chosen, {@code Config.reactorMeltWarnTicks} before anything
	 * actually changes.
	 */
	public static void telegraph(ServerLevel level, BlockPos at) {
		level.sendParticles(ParticleTypes.FLAME, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5,
				12, 0.3, 0.2, 0.3, 0.01);
		level.sendParticles(ParticleTypes.SMOKE, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5,
				8, 0.3, 0.2, 0.3, 0.01);
		level.playSound(null, at, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0f, 0.6f);
	}

	/**
	 * Turns a marked block into a lava source, if it is still there and still meltable.
	 *
	 * <p><b>Re-tested rather than trusted.</b> Between the warning and this moment the player has had
	 * two seconds to mine the block, and a great many of them will — that is what the warning is for.
	 * Melting the air they left behind would place lava in a spot nothing marked, which reads as the
	 * hazard cheating.
	 *
	 * @return whether a block actually changed
	 */
	public static boolean melt(ServerLevel level, BlockPos at) {
		if (!isMeltable(level, at)) {
			return false;
		}
		// A SOURCE, not a flowing block: flowing lava placed by hand vanishes on the next fluid tick, and
		// a hazard that undoes itself a moment later is no hazard at all.
		level.setBlock(at, Blocks.LAVA.defaultBlockState(), 3);
		level.playSound(null, at, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 0.9f, 0.8f);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5,
				10, 0.3, 0.3, 0.3, 0.02);
		return true;
	}
}
