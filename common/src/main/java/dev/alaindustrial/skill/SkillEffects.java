package dev.alaindustrial.skill;

import java.util.EnumMap;
import java.util.Map;

/**
 * The register of what each skill actually does, and whether it is wired up yet (MOD-483).
 *
 * <p>It exists because of the failure mode this system is most prone to: a node the player buys that
 * changes nothing. The tree, the screen and the purchase packet are all happy to ship a skill whose
 * effect was never connected to a machine — no compiler and no test notices, and the player finds out
 * by spending a point on a lie.
 *
 * <p>So every node names, in one place, the single spot in the mod its effect belongs to, and whether
 * that spot reads it yet. {@link Status#PENDING} nodes are drawn with a warning in their tooltip, and
 * {@code docs/tools/skill_wiring_check.py} fails the build if this register stops covering every node.
 *
 * <p>Free of Minecraft types on purpose — it is a table, and a table is worth checking without a game.
 */
public final class SkillEffects {

	/** Whether a node's effect is connected to the machine it claims to change. */
	public enum Status {
		/** The effect is read at its connection point: buying this node changes the game. */
		WIRED,
		/** Nothing reads it yet. The node is bought and stored, but nothing happens. */
		PENDING
	}

	/**
	 * One node's entry: where its effect belongs and whether it is there.
	 *
	 * @param status whether the connection point reads the skill
	 * @param connection the one place in the mod that must read it — a class or config knob, written
	 *     out so the next person does not have to re-derive it
	 */
	public record Effect(Status status, String connection) {
	}

	private static final Map<SkillBranch, Map<SkillSlot, Effect>> EFFECTS = build();

	private SkillEffects() {
	}

	private static Map<SkillBranch, Map<SkillSlot, Effect>> build() {
		Map<SkillBranch, Map<SkillSlot, Effect>> all = new EnumMap<>(SkillBranch.class);

		Map<SkillSlot, Effect> energy = new EnumMap<>(SkillSlot.class);
		energy.put(SkillSlot.IN, wired("ItemEnergy.spend — the mod's only debit of a carried item"));
		energy.put(SkillSlot.A1, wired("PlayerEuDistributor.distribute — the pack's includeEquipped flag"));
		energy.put(SkillSlot.B1, wired("FluxweaveArmorItem.secondStep — whole seconds, not a percentage"));
		energy.put(SkillSlot.MID, wired("PlayerEuDistributor — the per-tick inputRate ceiling"));
		energy.put(SkillSlot.A2, wired("ItemEnergy.spend + PlayerEuDistributor.refundToPack"));
		energy.put(SkillSlot.B2, wired("FluxweaveArmorItem.secondStep — horizontal movement check"));
		energy.put(SkillSlot.CAP, wired("EnergyPackItem.inventoryTick — the worn-slot guard"));
		all.put(SkillBranch.ENERGY, energy);

		Map<SkillSlot, Effect> hazard = new EnumMap<>(SkillSlot.class);
		hazard.put(SkillSlot.IN, wired("RadiationTicker — the dose added, after suit wear is charged"));
		hazard.put(SkillSlot.A1, wired("CableBlock.insulatedShockDamage — at the point of harm"));
		hazard.put(SkillSlot.B1, wired("RadiationTicker.wearSuit — dose per durability point"));
		hazard.put(SkillSlot.MID, wired("RadiationEffect — damage only, dose and symptoms stay"));
		hazard.put(SkillSlot.A2, wired("CableBlock.insulatedShockDamage — never the shock predicate"));
		hazard.put(SkillSlot.B2, wired("RadiationTicker — the Geiger counter's own radius"));
		hazard.put(SkillSlot.CAP, wired("RadiationEffect — the interval between hits"));
		all.put(SkillBranch.HAZARD, hazard);

		Map<SkillSlot, Effect> mech = new EnumMap<>(SkillSlot.class);
		mech.put(SkillSlot.IN, wired("MachineBlockEntity.effectiveDuration"));
		mech.put(SkillSlot.A1, wired("GeneratorBlockEntity — the fuel's burn duration"));
		mech.put(SkillSlot.B1, wired("ProcessingCycle — one drain tick in ten is skipped"));
		mech.put(SkillSlot.MID, wired("MachineBlockEntity.effectiveDuration"));
		mech.put(SkillSlot.A2, wired("MachineBlockEntity.overclockerCap"));
		mech.put(SkillSlot.B2, wired("MachineBlockEntity.hasStatsChip"));
		mech.put(SkillSlot.CAP, wired("ProcessingCycle — coasting on the machine's own charge"));
		all.put(SkillBranch.MECH, mech);

		Map<SkillSlot, Effect> agro = new EnumMap<>(SkillSlot.class);
		agro.put(SkillSlot.IN, wired("SprinklerBlockEntity.solutionPerSpray"));
		agro.put(SkillSlot.A1, wired("SprinklerBlockEntity — radius and solution cost together"));
		agro.put(SkillSlot.B1, wired("GardenDroneStationBlockEntity — flight ticks per block"));
		agro.put(SkillSlot.MID, wired("CrystalFarmControllerBlockEntity — via the sprinkler's owner"));
		agro.put(SkillSlot.A2, wired("IncubatorBlockEntity — base chance, under the mod's cap"));
		agro.put(SkillSlot.B2, wired("FermenterBlockEntity.waterPerOperation"));
		agro.put(SkillSlot.CAP, wired("GardenDroneStationBlockEntity — serviced radius"));
		all.put(SkillBranch.AGRO, agro);

		return all;
	}

	private static Effect pending(String connection) {
		return new Effect(Status.PENDING, connection);
	}

	private static Effect wired(String connection) {
		return new Effect(Status.WIRED, connection);
	}

	/** The entry for one node. Never null — the register covers every node, and a gate keeps it that way. */
	public static Effect of(SkillBranch branch, SkillSlot slot) {
		return EFFECTS.get(branch).get(slot);
	}

	/** Whether buying this node changes anything in the game yet. */
	public static boolean wired(SkillBranch branch, SkillSlot slot) {
		return of(branch, slot).status() == Status.WIRED;
	}

	/** How many nodes actually do something — what the wiring gate counts. */
	public static int wiredCount() {
		int count = 0;
		for (SkillBranch branch : SkillBranch.values()) {
			for (SkillSlot slot : SkillSlot.values()) {
				if (wired(branch, slot)) {
					count++;
				}
			}
		}
		return count;
	}

	/** Total nodes in the tree — branches times slots, so a fifth branch counts itself. */
	public static int total() {
		return SkillBranch.values().length * SkillSlot.values().length;
	}
}
