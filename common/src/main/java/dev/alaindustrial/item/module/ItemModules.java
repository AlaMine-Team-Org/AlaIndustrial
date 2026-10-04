package dev.alaindustrial.item.module;

import com.mojang.serialization.Codec;
import dev.alaindustrial.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * The modules fitted into an item's own module slots (MOD-592) — the value behind the
 * {@code alaindustrial:modules} data component.
 *
 * <p><b>One component for every host.</b> The electromagnet is the first item with module slots; the
 * drills, the jetpack and the armour are expected to follow. A host does not get a component of its
 * own: it implements {@link ModuleHost} and says how many slots it has, and the modules sit here as
 * item stacks. A module therefore keeps its own settings on its own stack (a filter carries its list
 * with it when it moves to another magnet), and removing it hands back exactly the stack that went in.
 *
 * <p>Positional: entry {@code i} is slot {@code i}, empty slots included, so a module stays in the slot
 * the player put it in. Shorter than the host's slot count is fine (a stack from before the slots
 * existed has no entries at all); longer can only happen if a host loses slots, and those extras are
 * still carried and still dropped rather than silently deleted.
 *
 * <p>Modelled on {@code PouchContents}: a record over {@link ItemStack}s rather than the vanilla
 * container component, whose internals keep changing between 26.x versions. Equality compares stack
 * VALUES — a record over {@code ItemStack} would otherwise compare identities, and component change
 * detection would see a new value every time the list is rebuilt.
 */
public final class ItemModules {

	/** Upper bound on the list, on disk and on the wire. Far above any host's slot count. */
	public static final int MAX_SLOTS = 9;

	/** Nothing fitted. Also what a stack without the component reads as. */
	public static final ItemModules EMPTY = new ItemModules(List.of());

	public static final Codec<ItemModules> CODEC = ItemStack.OPTIONAL_CODEC
			.sizeLimitedListOf(MAX_SLOTS)
			.xmap(ItemModules::new, ItemModules::slots);

	public static final StreamCodec<RegistryFriendlyByteBuf, ItemModules> STREAM_CODEC =
			ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SLOTS))
					.map(ItemModules::new, ItemModules::slots);

	private final List<ItemStack> slots;

	public ItemModules(List<ItemStack> slots) {
		this.slots = List.copyOf(slots);
	}

	/** The fitted modules, positional, empty slots included. */
	public List<ItemStack> slots() {
		return slots;
	}

	/** The module in slot {@code index}, or empty. Never the live stack — edit through {@link #with}. */
	public ItemStack get(int index) {
		return index >= 0 && index < slots.size() ? slots.get(index).copy() : ItemStack.EMPTY;
	}

	/** Whether no slot holds anything. */
	public boolean isEmpty() {
		for (ItemStack stack : slots) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** This set with slot {@code index} replaced by {@code stack}, grown with empty slots if needed. */
	public ItemModules with(int index, ItemStack stack) {
		if (index < 0 || index >= MAX_SLOTS) {
			return this;
		}
		List<ItemStack> next = new ArrayList<>(slots);
		while (next.size() <= index) {
			next.add(ItemStack.EMPTY);
		}
		next.set(index, stack.copy());
		// Trailing empties carry nothing; dropping them keeps an emptied host component-identical to a
		// fresh one, so it stacks and compares like one.
		while (!next.isEmpty() && next.get(next.size() - 1).isEmpty()) {
			next.remove(next.size() - 1);
		}
		return next.isEmpty() ? EMPTY : new ItemModules(next);
	}

	// --- stack access: the one place the component is read and written ------------------------

	/** The modules fitted on {@code host}. */
	public static ItemModules of(ItemStack host) {
		return host.getOrDefault(ModDataComponents.ITEM_MODULES.get(), EMPTY);
	}

	/** Write {@code modules} onto {@code host}; an empty set removes the component. */
	public static void set(ItemStack host, ItemModules modules) {
		if (modules.isEmpty()) {
			host.remove(ModDataComponents.ITEM_MODULES.get());
		} else {
			host.set(ModDataComponents.ITEM_MODULES.get(), modules);
		}
	}

	/** Copies of every non-empty module on {@code host} — what falls out when the host is destroyed. */
	public static List<ItemStack> fitted(ItemStack host) {
		NonNullList<ItemStack> out = NonNullList.create();
		for (ItemStack stack : of(host).slots) {
			if (!stack.isEmpty()) {
				out.add(stack.copy());
			}
		}
		return out;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ItemModules that) || that.slots.size() != slots.size()) {
			return false;
		}
		for (int i = 0; i < slots.size(); i++) {
			if (!ItemStack.matches(slots.get(i), that.slots.get(i))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 1;
		for (ItemStack stack : slots) {
			hash = 31 * hash + ItemStack.hashItemAndComponents(stack) + stack.getCount();
		}
		return hash;
	}

	@Override
	public String toString() {
		return "ItemModules" + slots;
	}
}
