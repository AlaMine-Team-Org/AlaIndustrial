package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.energy.ItemEnergy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Loader-neutral roster of the mod's powered items, derived from the registry rather than from a
 * hand-written list (MOD-372).
 *
 * <p>The cross-mod energy bridge (MOD-084) is wired per loader by naming every powered item in a
 * literal list — {@code StackAsEnergyStorage.register()} on Fabric,
 * {@code registerItem(Capabilities.Energy.ITEM, …)} on NeoForge. Twice those lists silently fell
 * behind the item roster: the Electric Chainsaw, Shovel, Hoe and Saber shipped with specs promising
 * foreign charging and no capability behind it. A test written against the same literal list could
 * never have caught that — it would only ever assert what the list already says.
 *
 * <p>So the roster here is computed from the one place that cannot drift: {@link ItemEnergy}, which
 * every powered item must teach about its buffer to be chargeable at all. Anything in the
 * {@code alaindustrial} namespace with a non-zero {@link ItemEnergy#capacity} is a powered item, and
 * the loader guards ({@code tcXmod001Reg01_everyPoweredItemExposesCapability} and its NeoForge twin)
 * hold the loader lists to it.
 */
public final class PoweredItemCatalog {

	/**
	 * Floor on the roster size — the guard against a vacuous pass.
	 *
	 * <p>A registry filter that silently matches nothing would let both loader guards go green while
	 * proving nothing at all. As of MOD-372 the roster holds 15 items (pouch, battery, energy pack,
	 * drill + diamond tip, chainsaw, shovel, hoe, saber, electromagnet, jetpack, four Fluxweave
	 * pieces), so the floor equals the real count.
	 *
	 * <p>Adding a powered item needs no change here. <b>Lower</b> this number only when an item is
	 * deliberately removed — that is the only case where the floor legitimately stops matching reality.
	 * Raising it does not make the guard stricter; it only makes both lanes red.
	 */
	public static final int MIN_POWERED_ITEMS = 15;

	private PoweredItemCatalog() {
	}

	/**
	 * Every item of the mod that owns an EU buffer, sorted by id so a failure message reads the same on
	 * both loaders and across runs.
	 */
	public static List<Item> poweredItems() {
		List<Item> powered = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (!Industrialization.MOD_ID.equals(id.getNamespace())) {
				continue;
			}
			Item item = BuiltInRegistries.ITEM.getValue(id);
			if (item != null && ItemEnergy.capacity(new ItemStack(item)) > 0L) {
				powered.add(item);
			}
		}
		powered.sort(Comparator.comparing(PoweredItemCatalog::idOf));
		return powered;
	}

	/** Registry id of an item as a string, for failure messages. */
	public static String idOf(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** Ids of a roster, comma-separated — so a failure names the items instead of only counting them. */
	public static String idsOf(List<Item> items) {
		List<String> ids = new ArrayList<>(items.size());
		for (Item item : items) {
			ids.add(idOf(item));
		}
		return String.join(", ", ids);
	}

	/**
	 * EU a foreign charger must be able to push into a freshly crafted {@code item} in one operation:
	 * the item's own input rate, or its whole buffer if that is somehow smaller.
	 *
	 * <p><b>Zero means broken, not "nothing expected".</b> {@link ItemEnergy#capacity} and
	 * {@link ItemEnergy#inputRate} are two independent {@code instanceof} cascades and can drift apart
	 * exactly the way the two registration lists did. An item that kept its capacity branch but lost its
	 * input-rate branch is chargeable by nothing at all — not a foreign mod, not the Battery Box slot,
	 * not the Charging Station. The loader guards must therefore treat a zero here as a defect; asserting
	 * "inserted 0, expected 0" would pass green over a fully bricked item.
	 *
	 * <p>Note what this number does and does not prove. It is derived from the same two methods the
	 * loader handlers use to clamp, so it cannot catch a wrong per-operation ceiling — the independent
	 * literal for that is {@code POUCH_INPUT_RATE} in the FUN01 case. What the guards do prove
	 * independently is that the capability resolves at all and that a positive amount really lands in the
	 * item's {@code pouch_energy} component.
	 */
	public static long expectedFirstInsert(Item item) {
		ItemStack stack = new ItemStack(item);
		return Math.min(ItemEnergy.inputRate(stack), ItemEnergy.capacity(stack));
	}
}
