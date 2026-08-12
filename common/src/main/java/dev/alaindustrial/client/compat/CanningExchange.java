package dev.alaindustrial.client.compat;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.food.CanningMath;
import dev.alaindustrial.core.food.CanningRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Canning Machine's exchange table for the recipe viewers (MOD-383), shared by REI and JEI.
 *
 * <p><b>Why this exists at all.</b> The machine has no JSON recipes: it takes anything carrying a food
 * component and always presses the same ration, so the "recipe" is arithmetic, not data. A player
 * opening REI or JEI would therefore see nothing for the machine — no card, no hint of the rate, no way
 * to learn that eleven sweet berries buy the same can one steak does. The cards are computed here
 * instead, one per accepted item.
 *
 * <p><b>Why it lives in common.</b> No gate holds the Fabric REI plugin and the NeoForge JEI plugin to
 * each other; the parity is honour-based, and two hand-written sweeps would drift the first time the
 * exchange rate or the blacklist changed on one side only. One roster and one count, consulted by both
 * adapters, is the only place that cannot drift.
 */
public final class CanningExchange {

	/**
	 * Hard stop on the absorption replay below.
	 *
	 * <p>{@code canningFoodValuePerCan} has a config floor but no ceiling, and a value above
	 * {@link CanningMath#MAX_BUFFER} is unreachable — the buffer saturates first and the loop would spin
	 * forever. An item that cannot reach a ration within this many pieces is dropped from the table
	 * rather than shown, because a card promising 900 apples is misinformation, not information.
	 */
	private static final int MAX_ITEMS_PER_RATION = 999;

	/**
	 * The registry sweep, done once. Null until the first viewer asks.
	 *
	 * <p>Lazy on purpose: the item registry is frozen long before a recipe-viewer plugin runs, but it is
	 * emphatically not frozen while {@code common} classes are initialising, so a static initialiser
	 * here would either see a half-built registry or miss every modded food registered after us.
	 */
	private static volatile List<Item> cannable;

	private CanningExchange() {
	}

	/** One viewer card: this many of {@code food}, plus one empty can, make one ration. */
	public record Card(Item food, int itemsPerRation) {
	}

	/**
	 * Every item the machine accepts, sorted by registry id.
	 *
	 * <p>Sorted rather than registry-ordered so the category lists the same cards in the same order on
	 * both loaders and across runs — registry iteration order is an implementation detail, and REI's and
	 * JEI's are not the same.
	 */
	public static List<Item> cannableItems() {
		List<Item> cached = cannable;
		if (cached == null) {
			synchronized (CanningExchange.class) {
				cached = cannable;
				if (cached == null) {
					cached = sweep();
					cannable = cached;
				}
			}
		}
		return cached;
	}

	/**
	 * One card per accepted item at the CURRENT exchange rate.
	 *
	 * <p>Only the roster is cached, not the counts: {@code /ala config reload} can retune
	 * {@link Config#canningFoodValuePerCan} at runtime, and a cached count would then quote a rate the
	 * machine no longer honours. The roster itself depends only on item components, so it survives a
	 * reload untouched.
	 */
	public static List<Card> cards() {
		int valuePerRation = Config.canningFoodValuePerCan;
		List<Card> cards = new ArrayList<>();
		for (Item item : cannableItems()) {
			int count = itemsPerRation(CanningRules.foodValue(new ItemStack(item)), valuePerRation);
			if (count > 0) {
				cards.add(new Card(item, count));
			}
		}
		return List.copyOf(cards);
	}

	/**
	 * How many pieces of one food the press swallows before it can stamp a ration, or {@code 0} if it
	 * never can.
	 *
	 * <p>This replays {@code CanningMachineBlockEntity.absorbFood} through the same two helpers the
	 * machine ticks — {@link CanningMath#wantsMoreFood} and {@link CanningMath#addToBuffer} — rather than
	 * dividing. A ceiling division would agree today and quietly disagree the moment either helper grows
	 * a clamp, and the card would then advertise a rate the machine does not run.
	 */
	static int itemsPerRation(int foodValue, int valuePerRation) {
		if (foodValue <= 0 || valuePerRation <= 0) {
			return 0;
		}
		int buffer = 0;
		int count = 0;
		while (count < MAX_ITEMS_PER_RATION && CanningMath.wantsMoreFood(buffer, valuePerRation)) {
			buffer = CanningMath.addToBuffer(buffer, foodValue);
			count++;
		}
		return CanningMath.hasFullRation(buffer, valuePerRation) ? count : 0;
	}

	/** Ticks one ration takes on an un-upgraded machine, matching {@code effectiveDuration} at zero chips. */
	public static int ticksPerRation() {
		return Config.scaledDuration(Config.canningMachineDuration);
	}

	/**
	 * EU one ration costs on an un-upgraded machine.
	 *
	 * <p>Both factors are taken already-scaled, exactly as {@code Config.electricFurnaceVanillaSmeltEu}
	 * does and for the same reason: each rounds separately, so multiplying the raw fields would only
	 * agree with the machine at the default speed multiplier.
	 */
	public static int energyPerRation() {
		return Math.max(1, ticksPerRation() * Config.machineEuPerTickEffective());
	}

	private static List<Item> sweep() {
		List<Identifier> ids = new ArrayList<>(BuiltInRegistries.ITEM.keySet());
		ids.sort(Comparator.comparing(Identifier::toString));
		List<Item> found = new ArrayList<>();
		for (Identifier id : ids) {
			Item item = BuiltInRegistries.ITEM.getValue(id);
			if (item != null && CanningRules.isCannable(item)) {
				found.add(item);
			}
		}
		return List.copyOf(found);
	}
}
