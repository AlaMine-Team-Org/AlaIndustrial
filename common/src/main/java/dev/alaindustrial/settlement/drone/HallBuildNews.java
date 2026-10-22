package dev.alaindustrial.settlement.drone;

import dev.alaindustrial.block.entity.TownDispatcherBlockEntity;
import dev.alaindustrial.chat.ModChat;
import dev.alaindustrial.settlement.Settlement;
import dev.alaindustrial.settlement.SettlementRegistry;
import dev.alaindustrial.settlement.works.BuildStage;
import dev.alaindustrial.settlement.works.BuildStageRules;
import dev.alaindustrial.settlement.works.MaterialSource;
import dev.alaindustrial.settlement.works.TownBuilding;
import dev.alaindustrial.settlement.works.TownWorks;
import dev.alaindustrial.settlement.works.WorkJournal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * <p><b>One line per event.</b> The last stage done on a hall that was never restored says nothing here: the restored
 * line follows at once, from the measuring pass ({@link #hallRestored}). On a hall restored before (a repair, or a
 * hall that met its restoration share before its fittings were in) the last stage says the hall stands whole.
 */
public final class HallBuildNews {

	private static final String PREFIX = "settlement.alaindustrial.chat.";
	/** A stage is done; the next one waits for its materials in the dispatcher. */
	public static final String STAGE_NEEDS = PREFIX + "stage_done.needs";
	/** A stage is done; the next one's materials are in the dispatcher already. */
	public static final String STAGE_READY = PREFIX + "stage_done.ready";
	/** The last stage is done on a hall restored before: it stands whole. */
	public static final String STAGE_LAST = PREFIX + "stage_done.last";
	/** The hall is restored for the first time. */
	public static final String HALL_RESTORED = PREFIX + "hall_restored";
	/** The hall, restored before, is damaged: a repair is queued and the drones mend it (owner's round 4). */
	public static final String HALL_DAMAGED = PREFIX + "hall_damaged";
	/** The damaged hall stands whole again. */
	public static final String HALL_MENDED = PREFIX + "hall_mended";

	/** Every key this class words, for the language gate's sake. */
	public static final List<String> KEYS = List.of(STAGE_NEEDS, STAGE_READY, STAGE_LAST, HALL_RESTORED,
			HALL_DAMAGED, HALL_MENDED);

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
		if (hall.isEmpty() || hall.getAsInt() != building) {
			return;
		}
		Optional<BuildStageRules.StageProgress> next = after.current();
		if (next.isEmpty()) {
			boolean restoredBefore = works.get().building(building).map(TownBuilding::everRestored).orElse(false);
			if (restoredBefore) {
				tell(level, town, STAGE_LAST, stageName(done));
			}
			return;
		}
		boolean ready = BuildStageRules.shortage(next.get(), buffer).isEmpty();
		tell(level, town, ready ? STAGE_READY : STAGE_NEEDS, stageName(done), stageName(next.get().stage()));
	}

	/**
	 * Building {@code building} of town {@code town} has just been restored for the first time: when it is the hall,
	 * the restorer hears it and the dispatcher's journal notes it.
	 */
	public static void hallRestored(ServerLevel level, int town, int building) {
		Optional<TownWorks> works = SettlementRegistry.get(level.getServer()).works().find(town);
		OptionalInt hall = works.map(TownHall::index).orElse(OptionalInt.empty());
		if (hall.isEmpty() || hall.getAsInt() != building) {
			return;
		}
		String name = works.get().building(building).map(TownBuilding::name).filter(n -> !n.isEmpty())
				.orElse("settlement.alaindustrial.building.town_hall");
		TownDispatcherBlockEntity.of(level, town)
				.ifPresent(dispatcher -> dispatcher.journal().restored(level.getGameTime(), name));
		tell(level, town, HALL_RESTORED);
	}

	/**
	 * Building {@code building} of town {@code town}, restored before, is damaged and a repair of it is queued (owner's
	 * round 4): when it is the hall, the restorer hears it once and the dispatcher's journal notes it. A repair's
	 * stages are no news.
	 */
	public static void hallDamaged(ServerLevel level, int town, int building) {
		repairNews(level, town, building, HALL_DAMAGED, WorkJournal.Kind.DAMAGED);
	}

	/** Building {@code building} of town {@code town}, damaged after its restoration, stands whole again. */
	public static void hallMended(ServerLevel level, int town, int building) {
		repairNews(level, town, building, HALL_MENDED, WorkJournal.Kind.MENDED);
	}

	private static void repairNews(ServerLevel level, int town, int building, String key, WorkJournal.Kind kind) {
		Optional<TownWorks> works = SettlementRegistry.get(level.getServer()).works().find(town);
		OptionalInt hall = works.map(TownHall::index).orElse(OptionalInt.empty());
		if (hall.isEmpty() || hall.getAsInt() != building) {
			return;
		}
		String name = works.get().building(building).map(TownBuilding::name).filter(n -> !n.isEmpty())
				.orElse("settlement.alaindustrial.building.town_hall");
		TownDispatcherBlockEntity.of(level, town).ifPresent(dispatcher -> dispatcher.journal()
				.add(new WorkJournal.Entry(level.getGameTime(), kind, name, 0)));
		tell(level, town, key);
	}

	/** The line {@code key} for town {@code town}: its number and its core first, then {@code args}. */
	public static Optional<Component> line(ServerLevel level, int town, String key, Component... args) {
		Optional<Settlement> found = SettlementRegistry.get(level.getServer()).find(town);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		List<Object> all = new ArrayList<>();
		all.add(white(String.valueOf(town)));
		all.add(white(coordinates(found.get().corePos())));
		all.addAll(List.of(args));
		return Optional.of(ModChat.line(Component.translatable(key, all.toArray())));
	}

	private static void tell(ServerLevel level, int town, String key, Component... args) {
		Optional<Component> body = line(level, town, key, args);
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

	private static Component stageName(BuildStage stage) {
		return Component.translatable(stage.key()).withStyle(ChatFormatting.WHITE);
	}

	private static Component white(String text) {
		return Component.literal(text).withStyle(ChatFormatting.WHITE);
	}

	private static String coordinates(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}
}
