package dev.alaindustrial.client.screen.shiftworker;

import dev.alaindustrial.network.ShiftWorkerCardPayload;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfile;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/**
 * The lines of a shift worker's card (MOD-854), built from the card's plain data and the viewer's lang file — the
 * server sends numbers and ids, never text, so every line reads in the language of whoever looks.
 *
 * <p>Kept apart from the screen so the screen only places what this class words.
 */
public final class ShiftWorkerCardText {

	/** Game ticks in one day; a service mark's day is its game time divided by this. */
	public static final long TICKS_PER_DAY = 24000L;

	static final String UI = "gui.alaindustrial.shift_worker.";

	private ShiftWorkerCardText() {
	}

	/** The header: the number, and the name after it when there is one. */
	public static Component title(ShiftWorkerCardPayload card) {
		return card.name().isEmpty() ? Component.literal(card.number())
				: Component.translatable(UI + "title_named", card.number(), card.name());
	}

	/** The day a game time falls on. */
	public static long day(long gameTime) {
		return Math.max(0L, gameTime) / TICKS_PER_DAY;
	}

	/** "day N". */
	public static Component dayLabel(long gameTime) {
		return Component.translatable(UI + "day", day(gameTime));
	}

	/** The status word of a record: working, file closed, or waiting to be brought back. */
	public static Component status(ShiftWorkerRecord.Status status) {
		return Component.translatable(UI + "status." + status.getSerializedName());
	}

	/** "Town: …" — the name the server resolved, or the town's number when it has no name, or none. */
	public static Component town(ShiftWorkerCardPayload card) {
		if (card.settlement() <= 0) {
			return Component.translatable(UI + "town_none");
		}
		Component name = card.settlementName().getString().isEmpty()
				? Component.translatable(UI + "town_number", card.settlement())
				: card.settlementName();
		return Component.translatable(UI + "town", name);
	}

	/**
	 * "Assigned to: settlement No. N", or none. A worker belongs to its settlement, not to a player (the lore of the
	 * towns, owner's decision 2026-10-21): the card names no player, whoever may change it.
	 */
	public static Component assignment(ShiftWorkerCardPayload card) {
		return card.settlement() <= 0 ? Component.translatable(UI + "assigned_none")
				: Component.translatable(UI + "assigned", card.settlement());
	}

	/** "Home: house at x, y, z", or "Homeless" (MOD-862). */
	public static Component home(ShiftWorkerCardPayload card) {
		return card.home().map(ShiftWorkerCardText::house)
				.map(house -> (Component) Component.translatable(UI + "home", house))
				.orElseGet(() -> Component.translatable(UI + "homeless"));
	}

	/** A house of the worker's town by where its bed stands: "house at x, y, z". */
	public static Component house(ShiftWorkerCardPayload.House house) {
		return Component.translatable(UI + "house", house.x(), house.y(), house.z());
	}

	/** "Died: day N, <who>", or empty for a living record. */
	public static Optional<Component> death(ShiftWorkerCardPayload card) {
		return card.death().map(death -> Component.translatable(UI + "death", day(death.gameTime()),
				death.killer().map(ShiftWorkerCardText::entityName)
						.orElseGet(() -> Component.translatable(UI + "death.unknown"))));
	}

	/** An entity type's name by id; the id itself when this client does not know the type. */
	public static Component entityName(Identifier id) {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(EntityType::getDescription)
				.orElseGet(() -> Component.literal(id.toString()));
	}

	/** The rank and level heading: "rank 2 · level 6". */
	public static Component rankAndLevel(ShiftWorkerCardPayload card) {
		int rank = Math.max(0, Math.min(7, card.rankIndex())) + 1;
		return Component.translatable(UI + "rank_level",
				Component.translatable("shift_worker.alaindustrial.rank." + rank), card.level());
	}

	/** One service mark as a line in the viewer's language, without its day. */
	public static Component mark(ShiftWorkerProfile.ServiceMark mark) {
		List<String> args = mark.args();
		String key = mark.kind().langKey();
		return switch (mark.kind()) {
			case ISSUED -> Component.translatable(key);
			case NAMED, LEVEL, RANK, RETURNED -> Component.translatable(key, arg(args, 0));
			case WOUNDED -> Component.translatable(key, attacker(arg(args, 0)), arg(args, 1), arg(args, 2));
			case RENUMBERED -> Component.translatable(key, arg(args, 0), arg(args, 1));
		};
	}

	/** The attacker of a wound mark: the entity type's name, or "unknown" for an empty id. */
	private static Component attacker(String id) {
		Identifier parsed = id.isEmpty() ? null : Identifier.tryParse(id);
		return parsed == null ? Component.translatable(ShiftWorkerProfile.ServiceMark.Kind.WOUNDED.langKey()
				+ ".unknown") : entityName(parsed);
	}

	private static String arg(List<String> args, int index) {
		return index < args.size() ? args.get(index) : "?";
	}

	/** A whole number with a thin space between thousands, the way the cards print EU. */
	public static String amount(long value) {
		String digits = Long.toString(Math.abs(value));
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < digits.length(); i++) {
			if (i > 0 && (digits.length() - i) % 3 == 0) {
				out.append(' ');
			}
			out.append(digits.charAt(i));
		}
		return value < 0 ? "-" + out : out.toString();
	}

	/** A health value without a needless ".0". */
	public static String health(float value) {
		float rounded = Math.round(value * 10.0F) / 10.0F;
		return rounded == Math.floor(rounded) ? Integer.toString((int) rounded) : Float.toString(rounded);
	}
}
