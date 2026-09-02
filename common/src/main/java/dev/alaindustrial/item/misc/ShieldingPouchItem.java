package dev.alaindustrial.item.misc;

import dev.alaindustrial.Config;
import dev.alaindustrial.item.energy.PouchContents;
import dev.alaindustrial.item.energy.PouchItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;

/**
 * Shielding Pouch (MOD-545) — the Battery Pouch lined with lead: the second tier of the same item.
 * Everything about carrying is inherited unchanged — the EU buffer and its lock, merge-first
 * insertion, LIFO extraction, the contents grid in the tooltip, the charge bar, no pouch inside a
 * pouch — and it is crafted FROM a Battery Pouch. The one thing it adds is the point of it:
 * radioactive items inside irradiate nobody, not the carrier, not the mobs around them, and not
 * from the floor or a chest it is left in.
 *
 * <p><b>The shielding is not in this class.</b> It is one type check in
 * {@code RadiationSources.countTagged}, the same doctrine the shielding chest follows
 * ({@code isExposedStorage} skipping {@code ShieldingChestBlockEntity}): one place decides what
 * radiates, and it is keyed on the item TYPE, so no datapack can hand shielding to another pouch
 * and no future pouch inherits it by accident.
 *
 * <p>The lead does not run on electricity: a discharged pouch locks its contents away exactly as
 * the Battery Pouch does, but what is already inside stays shielded either way.
 */
public class ShieldingPouchItem extends PouchItem {

	public ShieldingPouchItem(Properties properties) {
		super(properties);
	}

	@Override
	protected int capacity() {
		return Config.shieldingPouchCapacity;
	}

	/** The tier's capacity, for callers outside the item (gametests, tooltips). */
	public static int storageCapacity() {
		return Config.shieldingPouchCapacity;
	}

	/**
	 * Spill the contents when the pouch itself is destroyed — burnt in lava, blown up, killed by a
	 * cactus. Vanilla's base method is empty and only {@code BlockItem} and {@code BundleItem} opt
	 * in, so without this the uranium inside would vanish with the pouch. Overriding the vanilla
	 * one-argument method on purpose: NeoForge marks it deprecated in favour of a damage-source
	 * variant that Fabric does not have, and its own extension delegates straight back here.
	 */
	@Override
	public void onDestroyed(ItemEntity entity) {
		PouchContents contents = contentsOf(entity.getItem());
		if (contents.isEmpty()) {
			return;
		}
		setContents(entity.getItem(), PouchContents.EMPTY);
		ItemUtils.onContainerDestroyed(entity, contents.items().stream().map(ItemStack::copy));
	}
}
