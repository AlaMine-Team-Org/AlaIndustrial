package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.core.energy.CableNode;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.TownPowerLinks;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.TownRoadTrace;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.TownWorksScanner;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobStatus;
import dev.alaindustrial.settlement.works.WorkJobType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The road a builder drone lays to a house (MOD-861; epic MOD-824, decisions R111, R114, R127; round 2 of 0.1.219,
 * R2-7): the way along the town's road network from the front of the town hall's inlet to the front of the house's
 * inlet, and what the drone does in each cell of it.
 *
 * <p><b>The course.</b> The town's network ({@link TownRoadNetwork}) is one tree of streets planned over all its
 * buildings ({@link TownRoadTrace}: at most one block of height between neighbours, round the buildings, along old
 * paving); a house's course is the path from the hall to its inlet. Cells laid for an earlier house are already right
 * and passed, so the road step lays only the house's own branch and what of the street before it is still missing.
 *
 * <p><b>The work.</b> Every column of the course has fixed {@link Slot slots}, worked in this order over the whole
 * road: first the cuts — natural blocks over the road block, up to {@link TownRoadTrace#MAX_CUT} above it, taken away
 * into the dispatcher's buffer like a site's spoil; then the fills — the air under a road raised over the ground,
 * filled from the buffer with earth or stone ({@code #alaindustrial:town_site_fill}), lowest first; then the road
 * blocks. Where two neighbours differ by one block, the higher column gets a second road block under its road, hidden
 * in its ground, so the wire stays one line of touching segments. What a slot wants is read from the world each time:
 * a slot already right is passed.
 *
 * <p><b>A road cell is done</b> when it holds a road or any cable: a player's own line is a line, the drone does not
 * lay over it (R114). Litter on the way — grass, flowers, bushes, a snow layer — the drone takes away by itself
 * (owner's round 4 of 0.1.219, R4-5). A block with a block entity, a protected block or anything else a player put
 * where the road or a walker's head goes is an obstacle: the job stops naming it.
 *
 * <p><b>When.</b> A house's building job raises the road first ({@link #needsRoad}): the road comes before the house
 * (R108: clearing, road, building). The schematic shows the road still to lay as ghosts.
 */
public final class RoadWork {

	/** The item a road cell takes from the dispatcher's buffer. */
	public static final String ROAD_ITEM = "alaindustrial:town_road";
	/** The item the dispatcher's bill shows for the fill: the fill tag holds earth and stone, earth stands for them. */
	public static final String FILL_ITEM = "minecraft:dirt";
	/** The air a walker needs over a road block: an obstacle there stops the job. */
	static final int HEADROOM = 2;

	/** What a slot of the course asks for. */
	public enum Kind {
		/** A natural block over the road block, taken away. */
		CUT,
		/** Air under a raised road block, filled with earth or stone. */
		FILL,
		/** A road block. */
		ROAD
	}

	/**
	 * One slot of the course.
	 *
	 * @param pos   the cell
	 * @param kind  what it asks for
	 * @param roadY the height of its column's road block; for a fill, of the lowest road block of its column
	 */
	public record Slot(BlockPos pos, Kind kind, int roadY) {

		/** Whether this is the hidden road block under a column's road, joining it to a lower neighbour. */
		public boolean joint() {
			return kind == Kind.ROAD && pos.getY() < roadY;
		}
	}

	/** What a slot wants now. */
	public enum Want {
		/** Nothing: it is right as it is. */
		NONE,
		/** The drone's work. */
		WORK,
		/** A block the drone may not touch stands where the road or a walker's head goes. */
		OBSTRUCTED
	}

	/**
	 * A traced course: its road cells, the hall's end first, or why there is none.
	 *
	 * @param cells   the road block of every column, in order
	 * @param problem a {@link BlockReason} key when there is no course
	 */
	public record Course(List<BlockPos> cells, Optional<String> problem) {

		static Course none(String problem) {
			return new Course(List.of(), Optional.of(problem));
		}
	}

	/**
	 * What the road still takes from the buffer.
	 *
	 * @param roads road blocks still to lay
	 * @param fills fill blocks still to put
	 */
	public record Bill(int roads, int fills) {

		public boolean empty() {
			return roads <= 0 && fills <= 0;
		}
	}

	private RoadWork() {
	}

	/**
	 * The course of building {@code building}'s road (round 2 of 0.1.219, R127): the way along the town's road network
	 * ({@link TownRoadNetwork}) from the hall's inlet to the building's, the hall's end first — its own branch and the
	 * street it hangs on. Laid cells stay in it: a slot already right is passed. None for the hall, or while the
	 * building is not joined to the network.
	 */
	public static Course course(ServerLevel level, int town, int building) {
		Optional<TownWorks> found = SettlementRegistry.get(level.getServer()).works().find(town);
		if (found.isEmpty() || TownHall.index(found.get()).orElse(-1) == building) {
			return Course.none(BlockReason.NO_ROAD_COURSE);
		}
		Optional<List<BlockPos>> course = TownRoadNetwork.course(level, town, building);
		if (course.isPresent()) {
			return new Course(course.get(), Optional.empty());
		}
		Optional<TownPowerLinks.Socket> house = TownWorksScanner.plan(level, town, building)
				.flatMap(TownPowerLinks::inletOf);
		Optional<TownPowerLinks.Socket> hall = TownPowerLinks.hallInlet(level, town);
		boolean unloaded = house.isPresent() && !level.isLoaded(house.get().front())
				|| hall.isPresent() && !level.isLoaded(hall.get().front());
		return Course.none(unloaded ? BlockReason.CHUNK_NOT_LOADED : BlockReason.NO_ROAD_COURSE);
	}

	/**
	 * Every slot of {@code course}, in the drone's order: the cuts over the whole road top down, the fills lowest
	 * first, then the road blocks — a column's hidden joint before its road block. The two ends, in front of an
	 * inlet, get no cut: what stands over them belongs to the building.
	 */
	public static List<Slot> slots(List<BlockPos> course) {
		List<Slot> cuts = new ArrayList<>();
		List<Slot> fills = new ArrayList<>();
		List<Slot> roads = new ArrayList<>();
		int n = course.size();
		for (int i = 0; i < n; i++) {
			BlockPos cell = course.get(i);
			int y = cell.getY();
			boolean joint = i > 0 && course.get(i - 1).getY() == y - 1
					|| i < n - 1 && course.get(i + 1).getY() == y - 1;
			int lowest = joint ? y - 1 : y;
			if (joint) {
				roads.add(new Slot(cell.below(), Kind.ROAD, y));
			}
			roads.add(new Slot(cell, Kind.ROAD, y));
			if (i > 0 && i < n - 1) {
				for (int h = TownRoadTrace.MAX_CUT; h >= 1; h--) {
					cuts.add(new Slot(cell.above(h), Kind.CUT, y));
				}
			}
			for (int d = TownRoadTrace.MAX_FILL; d >= 1; d--) {
				fills.add(new Slot(new BlockPos(cell.getX(), lowest - d, cell.getZ()), Kind.FILL, lowest));
			}
		}
		List<Slot> out = new ArrayList<>(cuts);
		out.addAll(fills);
		out.addAll(roads);
		return List.copyOf(out);
	}

	/**
	 * Whether the road block at {@code pos} is an end of {@code course} — in front of an inlet — standing one block
	 * over its neighbour: the wire's rise into the inlet, the road at the door lying under it, flush with the floor
	 * (R3-5).
	 */
	public static boolean riser(List<BlockPos> course, BlockPos pos) {
		int n = course.size();
		if (n < 2) {
			return false;
		}
		return course.getFirst().equals(pos) && course.get(1).getY() == pos.getY() - 1
				|| course.getLast().equals(pos) && course.get(n - 2).getY() == pos.getY() - 1;
	}

	/** What {@code slot} wants now, read from the world; the caller has checked its chunk is loaded. */
	public static Want want(ServerLevel level, Slot slot) {
		BlockPos pos = slot.pos();
		BlockState world = level.getBlockState(pos);
		return switch (slot.kind()) {
			case CUT -> {
				if (open(world)) {
					yield Want.NONE;
				}
				if (cuttable(world)) {
					yield Want.WORK;
				}
				yield pos.getY() <= slot.roadY() + HEADROOM ? Want.OBSTRUCTED : Want.NONE;
			}
			case FILL -> {
				if (!open(world)) {
					yield Want.NONE;
				}
				// Only the air right under the road: a solid block between belongs to the ground, the hole under it is
				// no business of the road's.
				for (int y = pos.getY() + 1; y < slot.roadY(); y++) {
					if (!open(level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())))) {
						yield Want.NONE;
					}
				}
				yield Want.WORK;
			}
			case ROAD -> done(level, pos, world) ? Want.NONE : fillable(world) ? Want.WORK : Want.OBSTRUCTED;
		};
	}

	/** What the road of {@code course} still takes from the buffer. Slots in unloaded chunks count as wanting. */
	public static Bill bill(ServerLevel level, List<BlockPos> course) {
		int roads = 0;
		int fills = 0;
		for (Slot slot : slots(course)) {
			if (slot.kind() == Kind.CUT) {
				continue;
			}
			boolean wants = !level.isLoaded(slot.pos()) || want(level, slot) != Want.NONE;
			if (wants && slot.kind() == Kind.ROAD) {
				roads++;
			} else if (wants) {
				fills++;
			}
		}
		return new Bill(roads, fills);
	}

	/** How many fill items {@code buffer} holds, of every kind of earth and stone. */
	public static int fillHeld(DispatcherBuffer buffer) {
		return SiteWork.fillCount(buffer);
	}

	/**
	 * Whether the ground at {@code pos} holds up a road or cable a few blocks over it — the earth a road stands on, or
	 * the fill under a raised stretch: a clearing's ring around a building leaves it (owner's round 1 of 0.1.219).
	 */
	public static boolean carriesLine(ServerLevel level, BlockPos pos) {
		for (int h = 1; h <= TownRoadTrace.MAX_FILL + 1; h++) {
			if (level.getBlockEntity(pos.above(h)) instanceof CableNode) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code world} already carries the line: a road, or any cable a player laid. */
	public static boolean done(ServerLevel level, BlockPos pos, BlockState world) {
		return world.is(ModContent.TOWN_ROAD.get()) || level.getBlockEntity(pos) instanceof CableNode;
	}

	/** Whether the drone may put a road in place of {@code world}: air, natural ground, rubble, what goes anyway. */
	public static boolean fillable(BlockState world) {
		if (world.isAir()) {
			return true;
		}
		if (!world.getFluidState().isEmpty() || world.hasBlockEntity()
				|| world.is(ModTags.Blocks.TOWN_DRONE_PROTECTED)) {
			return false;
		}
		return natural(world) || world.canBeReplaced() || DroneCellSurvey.litter(world);
	}

	/**
	 * A natural block of the ground: earth, stone, sand, snow, rubble, an old street's paving — not a tree, and not the
	 * litter growing on it (a flower or a bush is no ground a road stands on; owner's round 4 of 0.1.219, R4-5).
	 */
	static boolean natural(BlockState state) {
		return (state.is(ModTags.Blocks.TOWN_RING_CLEAR) || state.is(ModTags.Blocks.TOWN_DEBRIS)
				|| state.is(ModTags.Blocks.TOWN_SITE_CLEAR) || state.is(ModTags.Blocks.TOWN_PAVING))
				&& !state.is(BlockTags.LOGS)
				&& !state.is(BlockTags.LEAVES) && !state.hasBlockEntity() && !state.canBeReplaced()
				&& !state.is(ModTags.Blocks.TOWN_DRONE_LITTER) && state.getFluidState().isEmpty();
	}

	/** Whether a cut may take {@code state} away: natural ground, leaves or litter, nothing with data or protected. */
	static boolean cuttable(BlockState state) {
		return !open(state) && !state.hasBlockEntity() && !state.is(ModTags.Blocks.TOWN_DRONE_PROTECTED)
				&& state.getFluidState().isEmpty()
				&& (natural(state) || state.is(BlockTags.LEAVES) || DroneCellSurvey.litter(state));
	}

	/** Air or what placing replaces: nothing a walker bumps into. */
	static boolean open(BlockState state) {
		return state.isAir() || state.canBeReplaced();
	}

	/**
	 * Whether building {@code building}'s inlet is on the town's line already — by a road, or a player's cable: then
	 * its road job has nothing left to do.
	 */
	public static boolean wired(ServerLevel level, int town, int building) {
		Optional<TownPowerLinks.Socket> inlet = TownWorksScanner.plan(level, town, building)
				.flatMap(TownPowerLinks::inletOf);
		return inlet.isPresent() && TownPowerLinks.link(level, town, inlet.get()) == TownPowerLinks.Link.CONNECTED;
	}

	/**
	 * Whether a building job on building {@code building} must wait for a road (R108, R127): the building is a house
	 * whose inlet is cut off from the core, it has no open road job, the owner did not call its last one off, and its
	 * way along the network still wants a road block — or it is not joined to the network yet, which the road job will
	 * name. A road already laid with the inlet still cut (a line not settled yet) raises nothing again.
	 */
	public static boolean needsRoad(ServerLevel level, int town, int building) {
		TownWorks works = SettlementRegistry.get(level.getServer()).works().town(town);
		OptionalInt hall = TownHall.index(works);
		if (hall.isPresent() && hall.getAsInt() == building
				|| works.queue().open(WorkJobType.ROAD, building).isPresent()) {
			return false;
		}
		WorkJob last = null;
		for (WorkJob job : works.queue().jobs()) {
			boolean road = job.type() == WorkJobType.ROAD && job.building() == building;
			if (road && (last == null || job.id() > last.id())) {
				last = job;
			}
		}
		if (last != null && last.status() == WorkJobStatus.CANCELLED) {
			return false; // the owner called the road off: it is not raised again and again
		}
		Optional<TownPowerLinks.Socket> inlet = TownWorksScanner.plan(level, town, building)
				.flatMap(TownPowerLinks::inletOf);
		if (inlet.isEmpty() || TownPowerLinks.hallInlet(level, town).isEmpty()
				|| TownPowerLinks.link(level, town, inlet.get()) != TownPowerLinks.Link.CUT) {
			return false;
		}
		Optional<List<BlockPos>> course = TownRoadNetwork.course(level, town, building);
		if (course.isEmpty()) {
			return true;
		}
		for (Slot slot : slots(course.get())) {
			if (slot.kind() == Kind.ROAD && (!level.isLoaded(slot.pos()) || want(level, slot) != Want.NONE)) {
				return true;
			}
		}
		return false;
	}

	/** Whether building {@code building} of town {@code town} has an open road job: planned or under way. */
	public static boolean roadOpen(ServerLevel level, int town, int building) {
		return SettlementRegistry.get(level.getServer()).works().town(town).queue()
				.open(WorkJobType.ROAD, building).isPresent();
	}
}
