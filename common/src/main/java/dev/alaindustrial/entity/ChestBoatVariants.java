package dev.alaindustrial.entity;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.DiamondChestBlockEntity;
import dev.alaindustrial.block.entity.ElectrumChestBlockEntity;
import dev.alaindustrial.block.entity.GoldChestBlockEntity;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.block.entity.ShieldingChestBlockEntity;
import dev.alaindustrial.block.entity.SilverChestBlockEntity;
import dev.alaindustrial.menu.DiamondChestMenu;
import dev.alaindustrial.menu.ElectrumChestMenu;
import dev.alaindustrial.menu.GoldChestMenu;
import dev.alaindustrial.menu.IronChestMenu;
import dev.alaindustrial.menu.ShieldingChestMenu;
import dev.alaindustrial.menu.SilverChestMenu;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Every wood × chest pair a {@link ModChestBoat} comes in (MOD-785) — the one table both loaders
 * replay to register the entity types and renderers, and the one the content manifest's
 * {@code chestBoat("<id>")} lines are checked against. Each pair is one entity type and one item
 * sharing an id: {@code <wood>_<chest>_chest_boat}, or {@code bamboo_<chest>_chest_raft} for the
 * bamboo raft, mirroring the vanilla names.
 *
 * <p>The asset side (item models, icons, recipes, names in every language) is written from the same
 * two lists by {@code tools/gen_chest_boats.py}; a wood or a chest added here is added there too.
 */
public final class ChestBoatVariants {
	/** A vanilla boat the chest can ride on. {@code raft} = the bamboo raft (other model, other seat). */
	public enum Wood {
		OAK("oak", false),
		SPRUCE("spruce", false),
		BIRCH("birch", false),
		JUNGLE("jungle", false),
		ACACIA("acacia", false),
		CHERRY("cherry", false),
		DARK_OAK("dark_oak", false),
		PALE_OAK("pale_oak", false),
		MANGROVE("mangrove", false),
		POPLAR("poplar", false),
		BAMBOO("bamboo", true);

		private final String path;
		private final boolean raft;

		Wood(String path, boolean raft) {
			this.path = path;
			this.raft = raft;
		}

		public String path() {
			return path;
		}

		public boolean raft() {
			return raft;
		}
	}

	/** A mod chest the boat can carry: its texture name, size and the menu that opens over the boat. */
	public enum Chest {
		IRON("iron", IronChestBlockEntity.CONTAINER_SIZE, IronChestMenu::forEntity),
		SILVER("silver", SilverChestBlockEntity.CONTAINER_SIZE, SilverChestMenu::forEntity),
		GOLD("gold", GoldChestBlockEntity.CONTAINER_SIZE, GoldChestMenu::forEntity),
		ELECTRUM("electrum", ElectrumChestBlockEntity.CONTAINER_SIZE, ElectrumChestMenu::forEntity),
		DIAMOND("diamond", DiamondChestBlockEntity.CONTAINER_SIZE, DiamondChestMenu::forEntity),
		SHIELDING("shielding", ShieldingChestBlockEntity.CONTAINER_SIZE, ShieldingChestMenu::forEntity);

		private final String path;
		private final int size;
		private final ModChestBoat.ChestMenuFactory menu;

		Chest(String path, int size, ModChestBoat.ChestMenuFactory menu) {
			this.path = path;
			this.size = size;
			this.menu = menu;
		}

		/** The tier name — also the chest texture {@code textures/entity/chest/<path>.png}. */
		public String path() {
			return path;
		}
	}

	/** One pair. */
	public record Variant(Wood wood, Chest chest) {
		/** Registry path shared by the entity type and the item. */
		public String id() {
			return wood.path + "_" + chest.path + (wood.raft ? "_chest_raft" : "_chest_boat");
		}

		/** Entity factory for this pair's type. */
		public ModChestBoat create(EntityType<? extends ModChestBoat> type, Level level) {
			String id = id();
			return new ModChestBoat(type, level, () -> BuiltInRegistries.ITEM.getValue(Industrialization.id(id)),
					chest.size, chest.menu, wood.raft);
		}
	}

	/** Every pair, chest-major (all woods of the iron chest first) — the creative-tab order. */
	public static final List<Variant> ALL;
	private static final Map<String, Variant> BY_ID;

	static {
		List<Variant> all = new ArrayList<>();
		Map<String, Variant> byId = new LinkedHashMap<>();
		for (Chest chest : Chest.values()) {
			for (Wood wood : Wood.values()) {
				Variant variant = new Variant(wood, chest);
				all.add(variant);
				byId.put(variant.id(), variant);
			}
		}
		ALL = Collections.unmodifiableList(all);
		BY_ID = Collections.unmodifiableMap(byId);
	}

	private ChestBoatVariants() {
	}

	/** The pair with this id; throws on an id no pair has, so a typo in the manifest fails at startup. */
	public static Variant byId(String id) {
		Variant variant = BY_ID.get(id);
		if (variant == null) {
			throw new IllegalArgumentException("ChestBoatVariants: no wood × chest pair has the id '" + id + "'");
		}
		return variant;
	}
}
