package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.AbstractMachineBlock;
import dev.alaindustrial.block.DistillationColumnSegmentBlock;
import dev.alaindustrial.block.FluidPipeBlock;
import dev.alaindustrial.block.ItemPipeBlock;
import dev.alaindustrial.block.ReactorShellBlock;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Every mod block answers a codec wherever the mod declares one (MOD-703, batch 0).
 *
 * <p><b>Why.</b> On 26.2 {@code BlockBehaviour.codec()} is abstract and every block must answer it; the mod
 * answers it ONCE, in its own bases ({@code AbstractMachineBlock}, {@code DistillationColumnSegmentBlock},
 * {@code FluidPipeBlock}, {@code ItemPipeBlock}, {@code ReactorShellBlock}), with a method whose source is the
 * same on both lines and which 26.3 never calls (26.3 removed block codecs). The compiler proves the method
 * exists; this scenario proves it answers — a {@code null} would compile on both lines and surface only in the
 * one place 26.2 reads a block codec, the {@code BlockListReport} data report.
 *
 * <p><b>Line-neutral by construction.</b> The method is looked up by name up the block's class chain and only
 * a declaration in a mod class is invoked: on 26.3 that is the own bases (a block with a vanilla parent has
 * none to find), on 26.2 also every block with a vanilla parent, which declares its own. A vanilla
 * declaration is vanilla's business and is not invoked.
 */
public final class BlockCodecScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(BlockCodecScenarios::everyModBlockAnswersItsCodec,
						"mod703_every_mod_block_answers_its_codec"));

		private Roster() {}
	}

	/** The mod's own block bases that answer {@code codec()} for their whole subtree. */
	private static final List<Class<?>> OWN_BASES = List.of(AbstractMachineBlock.class,
			DistillationColumnSegmentBlock.class, FluidPipeBlock.class, ItemPipeBlock.class, ReactorShellBlock.class);

	private BlockCodecScenarios() {}

	/**
	 * @implements MOD-703-BC01 — every mod block whose class chain declares {@code codec()} in a mod class gets
	 *     a non-null codec from it, and every block under one of the five own bases has one to find.
	 */
	public static void everyModBlockAnswersItsCodec(GameTestHelper helper) {
		TreeSet<String> ids = new TreeSet<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			if (id.getNamespace().equals(Industrialization.MOD_ID)) {
				ids.add(id.getPath());
			}
		}
		List<String> problems = new ArrayList<>();
		int answered = 0;
		int underOwnBases = 0;
		for (String path : ids) {
			Block block = BuiltInRegistries.BLOCK.getValue(Industrialization.id(path));
			boolean ownBase = OWN_BASES.stream().anyMatch(base -> base.isInstance(block));
			if (ownBase) {
				underOwnBases++;
			}
			Method codec = declaredCodec(block.getClass());
			if (codec == null || !codec.getDeclaringClass().getName().startsWith("dev.alaindustrial.")) {
				if (ownBase) {
					problems.add(path + ": under an own base but no mod-declared codec() found");
				}
				continue;
			}
			try {
				codec.setAccessible(true);
				if (codec.invoke(block) == null) {
					problems.add(path + ": " + codec.getDeclaringClass().getSimpleName() + ".codec() answered null");
				} else {
					answered++;
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				problems.add(path + ": " + codec.getDeclaringClass().getSimpleName() + ".codec() threw " + e);
			}
		}
		if (underOwnBases == 0) {
			helper.fail("MOD-703 precondition: no registered block is under an own base — the lookup saw nothing");
			return;
		}
		if (!problems.isEmpty()) {
			helper.fail("MOD-703: block codecs — " + problems + " (" + answered + " answered)");
			return;
		}
		helper.succeed();
	}

	/** The first {@code codec()} with no parameters declared up {@code type}'s class chain, or {@code null}. */
	private static Method declaredCodec(Class<?> type) {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			for (Method method : c.getDeclaredMethods()) {
				if (method.getName().equals("codec") && method.getParameterCount() == 0) {
					return method;
				}
			}
		}
		return null;
	}
}
