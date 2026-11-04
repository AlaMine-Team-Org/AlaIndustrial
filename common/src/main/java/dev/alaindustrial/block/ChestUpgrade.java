package dev.alaindustrial.block;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * One rung of the chest ladder an upgrade item climbs in place (MOD-944): which chest it accepts, which chest
 * it turns that into, and the metal whose sparks fly when it does. There is no step down.
 *
 * <p>Blocks and the metal item are resolved by registry id when asked, never at class-init: the enum is
 * read from the item's constructor, and the shared item factories must not close over a loader handle.
 */
public enum ChestUpgrade {
	/** Any vanilla copper chest — every weathering stage, waxed or not — becomes an iron chest. */
	COPPER_TO_IRON(BlockTags.COPPER_CHESTS, null, "iron_chest", Identifier.withDefaultNamespace("iron_ingot")),
	IRON_TO_SILVER(null, "iron_chest", "silver_chest", Industrialization.id("silver_ingot")),
	SILVER_TO_GOLD(null, "silver_chest", "gold_chest", Identifier.withDefaultNamespace("gold_ingot")),
	GOLD_TO_ELECTRUM(null, "gold_chest", "electrum_chest", Industrialization.id("electrum_ingot")),
	ELECTRUM_TO_DIAMOND(null, "electrum_chest", "diamond_chest", Identifier.withDefaultNamespace("diamond"));

	private final @Nullable TagKey<Block> sourceTag;
	private final @Nullable String sourceChestId;
	private final String targetChestId;
	private final Identifier metal;

	ChestUpgrade(@Nullable TagKey<Block> sourceTag, @Nullable String sourceChestId, String targetChestId,
			Identifier metal) {
		this.sourceTag = sourceTag;
		this.sourceChestId = sourceChestId;
		this.targetChestId = targetChestId;
		this.metal = metal;
	}

	/** Whether this rung upgrades the chest in {@code state}. */
	public boolean accepts(BlockState state) {
		return sourceTag != null ? state.is(sourceTag) : state.is(modBlock(sourceChestId));
	}

	/** The chest this rung places. */
	public Block target() {
		return modBlock(targetChestId);
	}

	/** The chest this rung accepts, named for the refusal message (the unwaxed copper chest stands for all). */
	public Component sourceName() {
		return sourceTag != null
				? BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("copper_chest")).getName()
				: modBlock(sourceChestId).getName();
	}

	/** The metal of the target tier, for the sparks. */
	public Item metal() {
		return BuiltInRegistries.ITEM.getValue(metal);
	}

	private static Block modBlock(@Nullable String id) {
		return BuiltInRegistries.BLOCK.getValue(Industrialization.id(id));
	}
}
