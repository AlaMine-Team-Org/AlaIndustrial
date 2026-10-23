package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.chat.ModChat;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.TownChatStyle;
import dev.alaindustrial.settlement.plan.PlanSheetRules;
import dev.alaindustrial.settlement.works.BlockReason;
import dev.alaindustrial.settlement.works.BuildStage;
import dev.alaindustrial.settlement.works.BuildStageRules;
import dev.alaindustrial.settlement.works.HousePlans;
import dev.alaindustrial.settlement.works.MaterialSource;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownHouses;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.WorkJob;
import dev.alaindustrial.settlement.works.WorkJobType;
import dev.alaindustrial.settlement.works.WorkJournal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The chat lines of a town hall's restoration (owner's round 3 of stage 6): a stage of the hall is done, and the hall
 * is restored. Each line names the settlement by its number and the place of its core, says which stage is done and
 * what comes next, and whether the next stage still waits for its materials in the dispatcher. Signed with the mod's
 * chat tag ({@link ModChat#line}) and sent to the town's restorer while they are online (the clan's members come
 * later, R16).
 *
 * <p><b>A repair is no restoration</b> (owner's round 4): a hall restored before that is damaged gets one line when its
 * repair is queued and one when it stands whole again ({@link #hallDamaged}, {@link #hallMended}); the stages of a
 * repair job say nothing ({@code ConstructionDroneWork}).
 *
 * <p><b>One line per event</b> (owner's round 3 of 0.1.219, R3-2). The last stage done says nothing here. A building
 * that meets its restoration share while its first building job still has cells to put (its fittings, say) is not
 * announced yet: the drones are still at it and the order stays in the dispatcher's list. Its one line — «restored»,
 * for the hall with the house plan it teaches — comes when that job is done ({@link #buildingJobDone}); a building
 * restored with no such job open hears it from the measuring pass at once ({@link #hallRestored}).
 */
public final class HallBuildNews {

	private static final String PREFIX = "settlement.alaindustrial.chat.";
	/** «Settlement No. %1$s (core at %2$s):» — the heading every line of a town starts with (R4-7). */
	public static final String HEADER = PREFIX + "header";
	/** A stage of the hall is done. */
	public static final String STAGE_DONE = PREFIX + "stage_done";
	/** What comes next: the next stage, which waits for its materials in the dispatcher. */
	public static final String NEXT_NEEDS = PREFIX + "next.needs";
	/** What comes next: the next stage, whose materials are in the dispatcher already. */
	public static final String NEXT_READY = PREFIX + "next.ready";
	/** The hall is restored for the first time. */
	public static final String HALL_RESTORED = PREFIX + "hall_restored";
	/** A capsule house is restored for the first time (MOD-862): a home for one more worker. */
	public static final String HOUSE_RESTORED = PREFIX + "house_restored";
	/** The hall, restored before, is damaged: a repair is queued and the drones mend it (owner's round 4). */
	public static final String HALL_DAMAGED = PREFIX + "hall_damaged";
	/** The damaged hall stands whole again. */
	public static final String HALL_MENDED = PREFIX + "hall_mended";

	/** A stage of a house is done, «stage N of M» (R2-3); the next stage follows as {@link #NEXT_NEEDS} or ready. */
	public static final String HOUSE_STAGE_DONE = PREFIX + "house_stage_done";
	/** The drones have cleared a building's site and the ring round it (R2-3). */
	public static final String CLEARED = PREFIX + "cleared";
	/** A new house is laid on free ground (MOD-877; R2-3). */
	public static final String HOUSE_LAID = PREFIX + "house_laid";
	/** A house laid on free ground is built (MOD-877; R2-3). */
	public static final String HOUSE_BUILT = PREFIX + "house_built";
	/** «Town %1$s (%2$s): the town has learned %3$s» — a house plan put into the dispatcher or onto the core (Q4). */
	public static final String HOUSE_PLAN_LEARNED = PREFIX + "house_plan_learned";
	/** «Received %s.» — the house plan the restored hall teaches (Q5), said in the hall's restored line (R3-2). */
	public static final String HALL_REWARD = PREFIX + "hall_restored.reward";

	/** Every key this class words, for the language gate's sake. */
	/** «Work on %1$s has stopped: %2$s» — a stop with what is in the way and where (owner's round 5 of 0.1.219). */
	public static final String JOB_STOPPED = PREFIX + "job_stopped";

	public static final List<String> KEYS = List.of(HEADER, STAGE_DONE, NEXT_NEEDS, NEXT_READY, HALL_RESTORED,
			HALL_REWARD, HOUSE_RESTORED, HALL_DAMAGED, HALL_MENDED, HOUSE_STAGE_DONE, CLEARED, HOUSE_LAID, HOUSE_BUILT,
			HOUSE_PLAN_LEARNED, JOB_STOPPED);

	private static final List<BiConsumer<Integer, Component>> EARS = new CopyOnWriteArrayList<>();

	private HallBuildNews() {
	}

	/**
	 * A game test's ear: every line written for a town also goes to {@code listener} with the town's id, until the
	 * returned action is run.
	 */
	public static Runnable listen(BiConsumer<Integer, Component> listener) {
		EARS.add(listener);
		return () -> EARS.remove(listener);
	}

	/**
	 * Stage {@code done} of building {@code building} of town {@code town} has just closed; {@code after} is where the
	 * building stands now, {@code buffer} the dispatcher's buffer. Says nothing for a building that is not the hall.
	 */
	public static void stageDone(ServerLevel level, int town, int building, BuildStage done,
			BuildStageRules.Progress after, MaterialSource buffer) {
		Optional<TownWorks> works = SettlementRegistry.get(level.getServer()).works().find(town);
		OptionalInt hall = works.map(TownHall::index).orElse(OptionalInt.empty());
		Optional<TownBuilding> house = works.flatMap(w -> w.building(building)).filter(TownHouses::isHouse);
		if (house.isPresent() && (hall.isEmpty() || hall.getAsInt() != building)) {
			houseStageDone(level, town, house.get(), done, after, buffer);
			return;
		}
		if (hall.isEmpty() || hall.getAsInt() != building) {
			return;
		}
		Optional<BuildStageRules.StageProgress> next = after.current();
		if (next.isEmpty()) {
			return; // the restored line says it, once its job is done (R3-2)
		}
		tell(level, town, STAGE_DONE, TownChatStyle.DONE, Optional.of(next(next.get(), buffer)), stageName(done));
	}

	/**
	 * Stage {@code done} of house {@code house} has closed (owner's round 2 of 0.1.219, R2-3): «house — stage N of M
	 * done», and what comes next. The last stage says nothing: «restored» follows from the measuring pass.
	 */
	private static void houseStageDone(ServerLevel level, int town, TownBuilding house, BuildStage done,
			BuildStageRules.Progress after, MaterialSource buffer) {
		Optional<BuildStageRules.StageProgress> next = after.current();
		if (next.isEmpty()) {
			return;
		}
		int number = 0;
		for (int i = 0; i < after.stages().size(); i++) {
			if (after.stages().get(i).stage() == done) {
				number = i + 1;
			}
		}
		tell(level, town, HOUSE_STAGE_DONE, TownChatStyle.DONE, Optional.of(next(next.get(), buffer)), name(house),
				TownChatStyle.number(number), TownChatStyle.number(after.count()), stageName(done));
	}

	/**
	 * The clearing of building {@code building} of town {@code town} is done — its site and the ring round it (R2-3).
	 * A repair's clearing is no news: the repair has its own two lines.
	 */
	public static void cleared(ServerLevel level, int town, int building) {
		SettlementRegistry.get(level.getServer()).works().find(town).flatMap(w -> w.building(building))
				.ifPresent(found -> tell(level, town, CLEARED, TownChatStyle.DONE, Optional.empty(), name(found)));
	}

	/** A new house, building {@code building} of town {@code town}, is laid on free ground (R2-3). */
	public static void houseLaid(ServerLevel level, int town, int building) {
		SettlementRegistry.get(level.getServer()).works().find(town).flatMap(w -> w.building(building))
				.ifPresent(found -> tell(level, town, HOUSE_LAID, TownChatStyle.NEWS, Optional.empty(), name(found)));
	}

	/** Town {@code town} learned the house plan of intact template {@code template} (round 2 of 0.1.219, Q4). */
	public static void housePlanLearned(ServerLevel level, int town, Identifier template) {
		Optional<TownWorks> works = SettlementRegistry.get(level.getServer()).works().find(town);
		if (works.isPresent() && works.get().learnHousePlan(template.toString())) {
			tell(level, town, HOUSE_PLAN_LEARNED, TownChatStyle.DONE, Optional.empty(), planName(template));
		}
	}

	/** The plan's name as the player reads it: «House Plan (type 3)». */
	private static Component planName(Identifier template) {
		String path = template.getPath();
		int type = PlanSheetRules.houseType(path.substring(path.lastIndexOf('/') + 1));
		return TownChatStyle.name(Component.translatable("item.alaindustrial.house_plan.typed", type));
	}

	/**
	 * Building {@code building} of town {@code town} has just been restored for the first time: the restorer hears it
	 * and the dispatcher's journal notes it — at once, or, while its first building job is still open, when that job is
	 * done ({@link #buildingJobDone}).
	 */
	public static void hallRestored(ServerLevel level, int town, int building) {
		Optional<TownWorks> works = SettlementRegistry.get(level.getServer()).works().find(town);
		boolean finishing = works.flatMap(w -> w.queue().open(WorkJobType.BUILD, building))
				.filter(job -> !job.repair()).isPresent();
		if (!finishing) {
			announceRestored(level, town, building);
		}
	}

	/**
	 * A drone has finished building job {@code job} of town {@code town}. When it is the first restoration's job and
	 * the building met its restoration share while the job went on, the restored line held back so far is said now.
	 */
	public static void buildingJobDone(ServerLevel level, int town, WorkJob job) {
		if (job.type() != WorkJobType.BUILD || job.repair()) {
			return;
		}
		boolean restored = SettlementRegistry.get(level.getServer()).works().find(town)
				.flatMap(w -> w.building(job.building())).map(TownBuilding::everRestored).orElse(false);
		if (restored) {
			announceRestored(level, town, job.building());
		}
	}

	private static void announceRestored(ServerLevel level, int town, int building) {
		Optional<TownWorks> works = SettlementRegistry.get(level.getServer()).works().find(town);
		OptionalInt hall = works.map(TownHall::index).orElse(OptionalInt.empty());
		boolean isHall = hall.isPresent() && hall.getAsInt() == building;
		boolean isHouse = works.flatMap(w -> w.building(building)).map(TownHouses::isHouse).orElse(false);
		if (!isHall && !isHouse) {
			return;
		}
		String name = works.get().building(building).map(TownBuilding::name).filter(n -> !n.isEmpty())
				.orElse("settlement.alaindustrial.building.town_hall");
		TownDispatcherBlockEntity.of(level, town)
				.ifPresent(dispatcher -> dispatcher.journal().restored(level.getGameTime(), name));
		boolean laid = !isHall && works.get().building(building).map(TownBuilding::isLaid).orElse(false);
		if (laid) {
			tell(level, town, HOUSE_BUILT, TownChatStyle.DONE, Optional.empty(),
					name(works.get().building(building).get()));
		} else if (isHall) {
			// The restored hall teaches the plainest house of its style (round 2 of 0.1.219, Q5): said in the same
			// line (R3-2).
			Optional<Component> reward = HousePlans.hallReward(works.get(), hall)
					.map(template -> Component.translatable(HALL_REWARD, planName(template)));
			tell(level, town, HALL_RESTORED, TownChatStyle.DONE, reward);
		} else {
			tell(level, town, HOUSE_RESTORED, TownChatStyle.DONE, Optional.empty());
		}
	}

	/**
	 * A job on building {@code building} of town {@code town} has stopped for {@code reason}, which names what is in
	 * the way and where (owner's round 5 of 0.1.219): the restorer hears it once, with what to do.
	 */
	public static void jobStopped(ServerLevel level, int town, int building, BlockReason reason) {
		Optional<TownBuilding> found = SettlementRegistry.get(level.getServer()).works().find(town)
				.flatMap(works -> works.building(building));
		if (found.isEmpty()) {
			return;
		}
		Object[] args = reason.args().stream().map(Component::translatable).toArray();
		Component why = Component.translatable(reason.key(), args);
		Component hint = Component.translatable(reason.key() + BlockReason.HINT).withStyle(TownChatStyle.NEXT_STEP);
		tell(level, town, JOB_STOPPED, TownChatStyle.TROUBLE, Optional.of(hint), name(found.get()), why);
	}

	/**
	 * Building {@code building} of town {@code town}, restored before, is damaged and a repair of it is queued (owner's
	 * round 4): when it is the hall, the restorer hears it once and the dispatcher's journal notes it. A repair's
	 * stages are no news.
	 */
	public static void hallDamaged(ServerLevel level, int town, int building) {
		repairNews(level, town, building, HALL_DAMAGED, TownChatStyle.TROUBLE, WorkJournal.Kind.DAMAGED);
	}

	/** Building {@code building} of town {@code town}, damaged after its restoration, stands whole again. */
	public static void hallMended(ServerLevel level, int town, int building) {
		repairNews(level, town, building, HALL_MENDED, TownChatStyle.DONE, WorkJournal.Kind.MENDED);
	}

	private static void repairNews(ServerLevel level, int town, int building, String key, ChatFormatting tone,
			WorkJournal.Kind kind) {
		Optional<TownWorks> works = SettlementRegistry.get(level.getServer()).works().find(town);
		OptionalInt hall = works.map(TownHall::index).orElse(OptionalInt.empty());
		if (hall.isEmpty() || hall.getAsInt() != building) {
			return;
		}
		String name = works.get().building(building).map(TownBuilding::name).filter(n -> !n.isEmpty())
				.orElse("settlement.alaindustrial.building.town_hall");
		TownDispatcherBlockEntity.of(level, town).ifPresent(dispatcher -> dispatcher.journal()
				.add(new WorkJournal.Entry(level.getGameTime(), kind, name, 0)));
		tell(level, town, key, tone, Optional.empty());
	}

	/**
	 * The line {@code key} for town {@code town}, plain news: the town's heading, then the sentence {@code key} with
	 * {@code args}.
	 */
	public static Optional<Component> line(ServerLevel level, int town, String key, Component... args) {
		return line(level, town, key, TownChatStyle.NEWS, Optional.empty(), args);
	}

	/**
	 * The line {@code key} for town {@code town} in the colours of {@link TownChatStyle} (R4-7): the heading (its
	 * number and its core), then the sentence {@code key} in {@code tone}, then {@code more} in the same message. The
	 * sentence is the line's last part; whatever follows it hangs off the sentence.
	 */
	private static Optional<Component> line(ServerLevel level, int town, String key, ChatFormatting tone,
			Optional<Component> more, Component... args) {
		Optional<Settlement> found = SettlementRegistry.get(level.getServer()).find(town);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		MutableComponent heading = Component.translatable(HEADER, TownChatStyle.number(town),
				TownChatStyle.coordinates(found.get().corePos())).withStyle(TownChatStyle.HEADING);
		MutableComponent sentence = Component.translatable(key, (Object[]) args).withStyle(tone);
		more.ifPresent(tail -> sentence.append(" ").append(tail));
		return Optional.of(ModChat.line(heading).append(" ").append(sentence));
	}

	/** What the player is to do next: stage {@code next}, and whether its materials are in {@code buffer}. */
	private static Component next(BuildStageRules.StageProgress next, MaterialSource buffer) {
		boolean ready = BuildStageRules.shortage(next, buffer).isEmpty();
		return Component.translatable(ready ? NEXT_READY : NEXT_NEEDS, stageName(next.stage()))
				.withStyle(TownChatStyle.NEXT_STEP);
	}

	/** The line {@code key}, with {@code more} said after it in the same message. */
	private static void tell(ServerLevel level, int town, String key, ChatFormatting tone, Optional<Component> more,
			Component... args) {
		Optional<Component> body = line(level, town, key, tone, more, args);
		if (body.isEmpty()) {
			return;
		}
		for (BiConsumer<Integer, Component> ear : EARS) {
			ear.accept(town, body.get());
		}
		for (ServerPlayer player : recipients(level.getServer(), town)) {
			player.sendSystemMessage(body.get(), false);
		}
	}

	/** Who hears a town's news: its restorer while online. */
	public static List<ServerPlayer> recipients(MinecraftServer server, int town) {
		List<ServerPlayer> out = new ArrayList<>();
		SettlementRegistry.get(server).find(town).flatMap(Settlement::owner)
				.map(uuid -> server.getPlayerList().getPlayer(uuid)).ifPresent(out::add);
		return out;
	}

	/** A building's name as the player reads it, a house with its type (R2-6). */
	private static Component name(TownBuilding building) {
		return TownChatStyle.name(Component.translatable(TownHouses.displayName(building)));
	}

	private static Component stageName(BuildStage stage) {
		return TownChatStyle.name(Component.translatable(stage.key()));
	}
}
