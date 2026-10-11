package dev.alaindustrial.client.blueprint;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One blueprint (MOD-797): an animated sheet that explains a few blocks before the player crafts them.
 * Pure data with no Minecraft type in it, so the catalogue is checked on the L1 lane; the screen and the
 * scene renderer turn it into pictures.
 *
 * <p>Every step is a full snapshot of what stands on the sheet — not a diff — so jumping to any step, or
 * stepping back, shows exactly what that step declares. Text is never stored here: each step's title and
 * body are lang keys derived from the blueprint id and the step number.
 *
 * @param id       the blueprint's own id, part of every lang key ({@code blueprint.alaindustrial.<id>.…})
 * @param subjects item ids ({@code alaindustrial:generator}) whose tooltip and guide page lead here
 * @param steps    the sheet's steps in playing order
 */
public record BlueprintDef(String id, List<String> subjects, List<Step> steps) {

	public BlueprintDef {
		Objects.requireNonNull(id, "id");
		subjects = List.copyOf(subjects);
		steps = List.copyOf(steps);
		if (steps.isEmpty()) {
			throw new IllegalArgumentException("blueprint " + id + " has no steps");
		}
	}

	/** The blueprint's title on the sheet and in the tooltip. */
	public String titleKey() {
		return "blueprint.alaindustrial." + id + ".title";
	}

	/** The callout title of step {@code index} (0-based). */
	public String stepTitleKey(int index) {
		return "blueprint.alaindustrial." + id + ".step" + (index + 1) + ".title";
	}

	/** The callout body of step {@code index} (0-based). */
	public String stepTextKey(int index) {
		return "blueprint.alaindustrial." + id + ".step" + (index + 1) + ".text";
	}

	/**
	 * Whether block {@code blockIndex} of step {@code stepIndex} appears on the sheet in that step — no block
	 * of the same id stood on the same cell one step earlier. The first step's blocks are all new. A block
	 * that only changes its state (a generator lighting up) is not new.
	 */
	public boolean isNewInStep(int stepIndex, int blockIndex) {
		Placement placed = steps.get(stepIndex).blocks().get(blockIndex);
		if (stepIndex == 0) {
			return true;
		}
		for (Placement before : steps.get(stepIndex - 1).blocks()) {
			if (before.blockId().equals(placed.blockId()) && before.x() == placed.x() && before.y() == placed.y()
					&& before.z() == placed.z()) {
				return false;
			}
		}
		return true;
	}

	/** Share of a step after which a block switches to its {@link Placement#lateProps} (fuel went in, it lit up). */
	public static final float LATE_AT = 0.55f;

	/**
	 * One block standing on the sheet.
	 *
	 * @param blockId   block registry id ({@code alaindustrial:copper_cable})
	 * @param props     block state properties to set over the default state ({@code lit=true}, {@code east=true})
	 * @param lateProps properties laid over {@code props} from {@link #LATE_AT} of the step on — a generator that
	 *                  lights up once its fuel has gone in
	 * @param contents  item ids put into the block's slots, slot 0 first — what its block-entity renderer draws
	 *                  (a wind turbine's rotor, a block breaker's tool)
	 */
	public record Placement(String blockId, int x, int y, int z, Map<String, String> props,
			Map<String, String> lateProps, List<String> contents) {

		public Placement {
			Objects.requireNonNull(blockId, "blockId");
			props = Map.copyOf(props);
			lateProps = Map.copyOf(lateProps);
			contents = List.copyOf(contents);
		}

		/** A block with no contents that may change state late in the step. */
		public Placement(String blockId, int x, int y, int z, Map<String, String> props,
				Map<String, String> lateProps) {
			this(blockId, x, y, z, props, lateProps, List.of());
		}

		/** A block that keeps one state for the whole step. */
		public Placement(String blockId, int x, int y, int z, Map<String, String> props) {
			this(blockId, x, y, z, props, Map.of());
		}

		/** This block with {@code itemIds} in its slots, slot 0 first. */
		public Placement holding(String... itemIds) {
			return new Placement(blockId, x, y, z, props, lateProps, List.of(itemIds));
		}
	}

	/**
	 * An item icon that flies into a block (its input: fuel, ore) or out of it (its output: an ingot). A
	 * step's motions play one after another in a loop.
	 *
	 * @param itemId item registry id ({@code minecraft:coal})
	 * @param block  index into the step's {@code blocks}
	 * @param into   true for an input, false for an output
	 */
	public record ItemMotion(String itemId, int block, boolean into) {

		public ItemMotion {
			Objects.requireNonNull(itemId, "itemId");
		}
	}

	/** What a step says about the arrangement it shows: nothing, "this is how it works", or "this is wrong". */
	public enum Verdict { NONE, GOOD, BAD }

	/** What moves along a flow: energy dots, a fluid's drops, or an item's icon. */
	public enum FlowKind { ENERGY, LAVA, WATER, STEAM, ITEM }

	/**
	 * Something moving from one block to another during a step.
	 *
	 * @param from   index of the block it leaves
	 * @param to     index of the block it reaches
	 * @param kind   what moves
	 * @param itemId the item whose icon travels, for {@link FlowKind#ITEM}; null otherwise
	 */
	public record Flow(int from, int to, FlowKind kind, String itemId) {

		public Flow {
			Objects.requireNonNull(kind, "kind");
			if (kind == FlowKind.ITEM) {
				Objects.requireNonNull(itemId, "itemId of an item flow");
			}
		}

		/** Energy from {@code from} to {@code to}. */
		public static Flow energy(int from, int to) {
			return new Flow(from, to, FlowKind.ENERGY, null);
		}

		/** Water drops from {@code from} to {@code to}. */
		public static Flow water(int from, int to) {
			return new Flow(from, to, FlowKind.WATER, null);
		}

		/** Steam puffs from {@code from} to {@code to}. */
		public static Flow steam(int from, int to) {
			return new Flow(from, to, FlowKind.STEAM, null);
		}

		/** Lava drops from {@code from} to {@code to}. */
		public static Flow lava(int from, int to) {
			return new Flow(from, to, FlowKind.LAVA, null);
		}

		/** Item {@code itemId} travelling from {@code from} to {@code to}. */
		public static Flow item(int from, int to, String itemId) {
			return new Flow(from, to, FlowKind.ITEM, itemId);
		}
	}

	/**
	 * One step of the sheet.
	 *
	 * @param blocks    everything standing on the sheet during this step
	 * @param highlight index into {@code blocks} the callout points at, or -1 for none
	 * @param position  the item number printed in the callout balloon, as on a drawing
	 * @param flows     what moves between blocks during the step
	 * @param verdict   whether the step shows a right or a wrong arrangement
	 * @param items     item icons going into or out of blocks during the step
	 */
	public record Step(List<Placement> blocks, int highlight, int position, List<Flow> flows, Verdict verdict,
			List<ItemMotion> items) {

		/** A step whose only flow is energy from {@code flowFrom} to {@code flowTo} (-1 for none). */
		public Step(List<Placement> blocks, int highlight, int position, int flowFrom, int flowTo, Verdict verdict,
				List<ItemMotion> items) {
			this(blocks, highlight, position,
					flowFrom >= 0 && flowTo >= 0 ? List.of(Flow.energy(flowFrom, flowTo)) : List.of(), verdict, items);
		}

		/** A step with energy from {@code flowFrom} to {@code flowTo} (-1 for none) and no item motion. */
		public Step(List<Placement> blocks, int highlight, int position, int flowFrom, int flowTo, Verdict verdict) {
			this(blocks, highlight, position, flowFrom, flowTo, verdict, List.of());
		}

		public Step {
			blocks = List.copyOf(blocks);
			flows = List.copyOf(flows);
			items = List.copyOf(items);
			for (ItemMotion motion : items) {
				checkIndex(motion.block(), blocks.size(), "item motion block");
			}
			for (Flow flow : flows) {
				checkBlock(flow.from(), blocks.size(), "flow from");
				checkBlock(flow.to(), blocks.size(), "flow to");
			}
			checkIndex(highlight, blocks.size(), "highlight");
			Objects.requireNonNull(verdict, "verdict");
		}

		private static void checkIndex(int index, int size, String what) {
			if (index < -1 || index >= size) {
				throw new IllegalArgumentException(what + " " + index + " outside 0.." + (size - 1));
			}
		}

		private static void checkBlock(int index, int size, String what) {
			if (index < 0 || index >= size) {
				throw new IllegalArgumentException(what + " " + index + " outside 0.." + (size - 1));
			}
		}
	}
}
