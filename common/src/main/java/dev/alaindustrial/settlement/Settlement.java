package dev.alaindustrial.settlement;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One ALA town as the server knows it (MOD-831): the record {@link SettlementRegistry} keeps. Immutable — every
 * change is a new record the registry stores in place of the old one.
 *
 * @param id         the town's number, unique in the save and never reused while the town exists; the core in the
 *                   world stores only this (its item stores nothing — epic decision R43)
 * @param dimension  the dimension the town stands in
 * @param corePos    where the core stands — or, while it is taken away, where it last stood; the tier's square of
 *                   the territory is centred on it
 * @param corePlaced whether the core stands in the world; false from the moment its owner breaks it until a player
 *                   places a core inside the territory (epic decisions R36, R43) — the town hibernates meanwhile
 * @param owner      who claimed the town, or empty until somebody does (R37; the clan roles come later, R16)
 * @param ownerName  the owner's name as it was when they claimed, for screens that show it while they are offline;
 *                   empty without an owner
 * @param tier       the core's tier, which sets the territory and the limits ({@link SettlementTier})
 * @param style      the id of the town's style ({@code settlement_style}, set by the town structure of MOD-830), or
 *                   empty for a core placed by hand or found before the style was known
 * @param active     whether the core is lit — holds its territory — as the core last reported it; false while the
 *                   core is taken away
 * @param footprint  the footprint of all the pieces of the structure that built the town (MOD-848, epic decision R59),
 *                   which its territory covers; empty for a town no structure built — the demo stand's, a command's —
 *                   and for a town of an older save until its core finds its structure again
 */
public record Settlement(int id, ResourceKey<Level> dimension, BlockPos corePos, boolean corePlaced,
		Optional<UUID> owner, String ownerName, SettlementTier tier, String style, boolean active,
		Optional<SettlementTerritory.Area> footprint) {

	public Settlement {
		corePos = corePos.immutable();
		ownerName = owner.isPresent() ? ownerName : "";
	}

	/** A town nobody has claimed yet, its core in place at the first tier, with no style and the core dark. */
	public static Settlement unclaimed(int id, ResourceKey<Level> dimension, BlockPos corePos) {
		return new Settlement(id, dimension, corePos, true, Optional.empty(), "", SettlementTier.FIRST, "", false,
				Optional.empty());
	}

	/** The tier's territory radius: the square around the core every town holds, the least of its territory. */
	public int radius() {
		return tier.radius();
	}

	/**
	 * The territory around {@link #corePos} at the current tier ({@link SettlementTerritory#of}): the tier's square
	 * around the core, and for a town a structure built also its buildings' footprint with the margin — the one rule
	 * every reader of the territory goes through.
	 */
	public SettlementTerritory.Area territory() {
		return SettlementTerritory.of(corePos.getX(), corePos.getZ(), tier, footprint);
	}

	/**
	 * How far the territory reaches from the core along both axes: every block within this many columns of the core
	 * is the town's. Never less than {@link #radius()}, since the territory holds the tier's square around the core.
	 */
	public int territoryReach() {
		return territory().reach(corePos.getX(), corePos.getZ());
	}

	/** Whether {@code player} owns this town. */
	public boolean isOwnedBy(UUID player) {
		return owner.isPresent() && owner.get().equals(player);
	}

	/**
	 * Whether {@code pos} of {@code level} lies inside this town's territory ({@link #territory()}), lit or not — the
	 * test a core placed by a player must pass to continue a town without its core (R43).
	 */
	public boolean territoryContains(ResourceKey<Level> level, BlockPos pos) {
		return dimension.equals(level) && territory().contains(pos.getX(), pos.getZ());
	}

	/**
	 * Whether this town holds the block at {@code pos} of {@code level} right now: the core is lit and the block
	 * lies inside its territory. A dark core holds nothing (epic decision R5).
	 */
	public boolean holds(ResourceKey<Level> level, BlockPos pos) {
		return active && territoryContains(level, pos);
	}

	Settlement withOwner(UUID player, String name) {
		return new Settlement(id, dimension, corePos, corePlaced, Optional.of(player), name, tier, style, active,
				footprint);
	}

	Settlement withActive(boolean lit) {
		return new Settlement(id, dimension, corePos, corePlaced, owner, ownerName, tier, style, lit, footprint);
	}

	Settlement withStyle(String newStyle) {
		return new Settlement(id, dimension, corePos, corePlaced, owner, ownerName, tier, newStyle, active, footprint);
	}

	/** The core placed at {@code pos} (true), or taken away from where it stood (false, and the town goes dark). */
	Settlement withCore(BlockPos pos, boolean placed) {
		return new Settlement(id, dimension, pos, placed, owner, ownerName, tier, style, placed && active, footprint);
	}

	Settlement withFootprint(SettlementTerritory.Area area) {
		return new Settlement(id, dimension, corePos, corePlaced, owner, ownerName, tier, style, active,
				Optional.of(area));
	}
}
