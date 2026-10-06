package dev.alaindustrial.gametest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.BlackholeTickAccess;

/**
 * A {@link WorldGenLevel} for one feature call in a gametest (MOD-774): flat synthetic terrain — stone
 * below {@code groundY}, a grass block at it, air above — that remembers what the feature writes, and
 * notes every block or height it reads or writes outside the ±1-chunk window of the chunk being
 * decorated.
 *
 * <p>This is the rule the game's own {@code WorldGenRegion} enforces at the {@code features} step: its
 * {@code getBlockState}, {@code getFluidState}, {@code getBlockEntity} and {@code getHeight} log "unsafe
 * terrain read" for a chunk past the write radius, and {@code ensureCanWrite} refuses a write there.
 * Outside that window a chunk may not have its terrain yet, so a read there answers with whatever the
 * chunk holds at that moment. A gametest world has no such region, so the probe plays its part.
 *
 * <p>The level is a {@link Proxy}: block access, heights, the biome and the tick lists are answered
 * here; the default methods of the interfaces run against the proxy itself, so they come back here
 * too; a short list of read-only facts (registries, build height, seed) is asked of the real level;
 * anything else fails the test by name rather than touch the real world.
 */
final class WriteWindowProbe implements InvocationHandler {

	/** Methods answered by the real level: facts about the world, none of which reads a chunk. */
	private static final Set<String> DELEGATED = Set.of("registryAccess", "enabledFeatures", "getMinY",
			"getSeed", "dimensionType", "isClientSide", "getSeaLevel", "getLevel", "getServer", "getLevelData",
			"getRandom", "getWorldBorder");

	/** Out-of-window accesses whose caller is looked up; past this they are only counted. */
	private static final int ATTRIBUTED_LIMIT = 200;

	private final ServerLevel real;
	private final ChunkPos center;
	private final int centerX;
	private final int centerZ;
	private final int groundY;
	private final Holder<Biome> biome;
	private final Map<BlockPos, BlockState> written = new HashMap<>();
	/** Out-of-window accesses, counted by the first caller frame worth naming. */
	private final Map<String, Integer> outside = new LinkedHashMap<>();
	private int attributed;
	private long subTicks;
	final WorldGenLevel level;

	WriteWindowProbe(ServerLevel real, ChunkPos center, int groundY, Holder<Biome> biome) {
		this.real = real;
		this.center = center;
		this.centerX = SectionPos.blockToSectionCoord(center.getMinBlockX());
		this.centerZ = SectionPos.blockToSectionCoord(center.getMinBlockZ());
		this.groundY = groundY;
		this.biome = biome;
		this.level = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
				new Class<?>[] {WorldGenLevel.class}, this);
	}

	/** Out-of-window accesses as {@code caller kind ×count}; empty when the feature kept to its window. */
	Map<String, Integer> outside() {
		return outside;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if (method.getDeclaringClass() == Object.class) {
			return switch (method.getName()) {
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				default -> "WriteWindowProbe" + center;
			};
		}
		int arity = args == null ? 0 : args.length;
		switch (method.getName()) {
			case "getBlockState":
				if (arity == 1 && args[0] instanceof BlockPos pos) {
					touch(pos.getX(), pos.getZ(), "read");
					return stateAt(pos);
				}
				break;
			case "getFluidState":
				if (arity == 1 && args[0] instanceof BlockPos pos) {
					touch(pos.getX(), pos.getZ(), "read");
					return stateAt(pos).getFluidState();
				}
				break;
			case "getBlockEntity":
				if (arity == 1 && args[0] instanceof BlockPos pos) {
					touch(pos.getX(), pos.getZ(), "read");
					return null;
				}
				break;
			case "getHeight":
				if (arity == 3 && args[0] instanceof Heightmap.Types) {
					int x = (Integer) args[1];
					int z = (Integer) args[2];
					touch(x, z, "read");
					return groundY + 1;
				}
				if (arity == 0) {
					return real.getHeight();
				}
				break;
			case "isStateAtPosition":
				if (arity == 2 && args[0] instanceof BlockPos pos) {
					touch(pos.getX(), pos.getZ(), "read");
					@SuppressWarnings("unchecked")
					Predicate<BlockState> test = (Predicate<BlockState>) args[1];
					return test.test(stateAt(pos));
				}
				break;
			case "isFluidAtPosition":
				if (arity == 2 && args[0] instanceof BlockPos pos) {
					touch(pos.getX(), pos.getZ(), "read");
					@SuppressWarnings("unchecked")
					Predicate<FluidState> test = (Predicate<FluidState>) args[1];
					return test.test(stateAt(pos).getFluidState());
				}
				break;
			case "setBlock":
				if (arity == 4 && args[0] instanceof BlockPos pos && args[1] instanceof BlockState state) {
					touch(pos.getX(), pos.getZ(), "write");
					written.put(pos.immutable(), state);
					return true;
				}
				break;
			case "getBiome":
				return biome;
			case "getBlockTicks":
			case "getFluidTicks":
				return BlackholeTickAccess.emptyLevelList();
			case "nextSubTickCount":
				return subTicks++;
			default:
				break;
		}
		if (method.isDefault()) {
			return InvocationHandler.invokeDefault(proxy, method, args);
		}
		if (DELEGATED.contains(method.getName())) {
			try {
				return method.invoke(real, args);
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		}
		throw new UnsupportedOperationException("the feature asked the probe level for " + method);
	}

	private BlockState stateAt(BlockPos pos) {
		BlockState placed = written.get(pos);
		if (placed != null) {
			return placed;
		}
		if (pos.getY() < groundY) {
			return Blocks.STONE.defaultBlockState();
		}
		return pos.getY() == groundY ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
	}

	private void touch(int x, int z, String kind) {
		int dx = Math.abs(SectionPos.blockToSectionCoord(x) - centerX);
		int dz = Math.abs(SectionPos.blockToSectionCoord(z) - centerZ);
		if (Math.max(dx, dz) > 1) {
			// Walking the stack is slow; the first accesses name the callers, the rest are only counted.
			String who = attributed++ < ATTRIBUTED_LIMIT ? caller() : "(further accesses, not attributed)";
			outside.merge(kind + " by " + who, 1, Integer::sum);
		}
	}

	/** The first frame of the mod's worldgen or of the game's structure code: who made the access. */
	private static String caller() {
		return StackWalker.getInstance().walk(frames -> frames
				.filter(f -> f.getClassName().startsWith("dev.alaindustrial.worldgen.")
						|| f.getClassName().startsWith("net.minecraft.world.level.levelgen.structure."))
				.findFirst()
				.map(f -> f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1) + "."
						+ f.getMethodName())
				.orElse("?"));
	}
}
