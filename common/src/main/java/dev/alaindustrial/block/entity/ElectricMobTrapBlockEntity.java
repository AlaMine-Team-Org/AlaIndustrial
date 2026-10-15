package dev.alaindustrial.block.entity;

import com.mojang.authlib.GameProfile;
import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.machine.SyncChannels;
import dev.alaindustrial.compat.LineMobs;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.core.fluid.FluidHolder;
import dev.alaindustrial.core.fluid.FluidPort;
import dev.alaindustrial.core.fluid.FluidPortHost;
import dev.alaindustrial.core.fluid.FluidTank;
import dev.alaindustrial.core.machine.StatusLine;
import dev.alaindustrial.core.world.DropSeek;
import dev.alaindustrial.core.world.FakePlayers;
import dev.alaindustrial.core.world.StuckDropTracker;
import dev.alaindustrial.entity.MobRepellerField;
import dev.alaindustrial.fluid.ExperienceFluid;
import dev.alaindustrial.menu.ElectricMobTrapMenu;
import dev.alaindustrial.network.ZonePreviewPayload;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModDamageTypes;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// size-justified: one machine's whole job — who it acts on, the pull, the shock, the drop capture, the buffer and
// the tank — and the nine-slot buffer copied from the Vacuum Collector until a shared helper is extracted.
/**
 * The Electric Mob Trap, tier I (MOD-818): pulls hostile mobs in weakly, shocks the ones that reach it, and
 * keeps their drops in a nine-slot buffer and their experience as liquid in a tank. It never spawns a mob —
 * the player builds the dark platform and the trap collects what appears there.
 *
 * <p><b>A pulse, not a stream.</b> Every {@link Config#electricMobTrapPulseTicks} ticks the trap looks for
 * trappable mobs in the cube of {@link Config#electricMobTrapRadius} blocks round its centre (the pulse is
 * a schedule, as in the Vacuum Collector). Mobs outside the strike radius are set walking towards it, at
 * most {@link Config#electricMobTrapPullsPerPulse} of them; mobs inside it are shocked, at most
 * {@link Config#electricMobTrapStrikesPerPulse}, the nearest first (equal distances by UUID, so the order
 * never depends on the one the world returns them in).
 *
 * <p><b>Who is trappable.</b> Every monster ({@link Enemy}), calm neutral ones included — an enderman, a
 * zombified piglin or a piglin is caught whether or not it is angry (owner decision 2026-10-15; the trap sets
 * no target, so shocking a calm one alerts no herd) — minus the bosses ({@link #isBoss}: vanilla has no boss
 * tag in 26.3) and the shulker, and minus any mob standing in the field of a live Mob Repeller
 * ({@link MobRepellerField#isRepelled}): the repeller wins.
 *
 * <p><b>The pull.</b> A mob chasing a player forgets the player while it is pulled — a farm whose owner
 * stands nearby would otherwise never pull anything. A goal-driven walker is given a path to the centre
 * ({@code navigation.moveTo}) at most once per {@link #PULL_REPEAT_TICKS} ticks; a brain-driven one
 * (piglins, hoglins, the breeze, the creaking) is given the centre as its walk target. A mob with no
 * navigation to speak of (slimes, fliers) gets a small nudge instead ({@link #NUDGE}). The pull is still
 * weak: a mob that never arrives within the strike radius is never shocked.
 *
 * <p><b>A shock, not a kill.</b> {@link Config#electricMobTrapDamage} half-hearts of
 * {@code electric_shock} damage per hit, armour counting, each hit costing
 * {@link Config#electricMobTrapEuPerStrike} EU — paid only for a hit that landed, and with no energy the
 * trap deals none and pulls nothing. The damage source carries no entity, so no Soul Vessel counts the kill
 * and the death message reads "electrocuted" — except for an enderman, which teleports away from any hurt
 * that no living attacker dealt: its shock names the trap's fake player as the attacker (no vessel either,
 * the fake player carries none), and the anger that leaves it is cleared at once. The loot is the ordinary
 * loot of a mob killed by a player, because the mob is first credited to the trap's own fake player
 * ({@code setLastHurtByPlayer}, the credit that {@code killed_by_player} loot conditions and experience
 * read — the credit holds the player object, so a fake player that is not in the world still counts); there
 * is no Looting. No mastery experience is credited: like the repeller, the trap runs unattended.
 *
 * <p><b>Players are shocked too</b> (owner decision 2026-10-15): a survival or adventure player standing in
 * the strike radius takes the same shock, nearest first with the mobs and paid the same; a player is never
 * pulled, and a creative or spectator player is left alone.
 *
 * <p><b>The drops fall, then the trap draws them in.</b> The mob dies the ordinary way, so equipment, heads
 * and every loader's death events work, and its loot and experience orbs fall as on any death. Every
 * {@link #SWEEP_TICKS} ticks the trap acts like the Vacuum Collector on the loose drops and orbs in its zone:
 * one that touches the block is taken — items into the buffer, as many as fit, orbs into the tank at
 * {@link ExperienceFluid#MB_PER_POINT} mB a point when the whole orb fits — and one further out is set moving
 * towards it ({@link DropSeek}, the electromagnet's pull); one that stops getting closer is let go for a while
 * ({@link StuckDropTracker}). The sweep is free: it is part of the trap's work. What the buffer or the tank
 * cannot hold stays in the world, and a buffer with no room stops the shocks.
 *
 * <p><b>Faces.</b> Cables join the four sides. The lid and the floor each expose the nine extract-only
 * slots and the extract-only tank; nothing can be put in by hand, hopper or pipe.
 */
public class ElectricMobTrapBlockEntity extends MachineBlockEntity
		implements MenuProvider, NoUpgradePanel, FluidPortHost {

	/** The buffer: nine slots, all of them output. */
	public static final int SLOT_COUNT = 9;
	private static final int[] BUFFER_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
	private static final int[] NO_SLOTS = new int[0];

	/** A mob is not given a new path more often than this: the pull is meant to be weak, and paths cost. */
	static final int PULL_REPEAT_TICKS = 20;
	/** Walking speed asked of a pulled mob: its own normal pace. */
	private static final double PULL_SPEED = 1.0;
	/** Horizontal nudge per pulse for mobs navigation cannot move (slimes, fliers). */
	private static final double NUDGE = 0.08;
	/** Ticks between two sweeps of the drops in reach: the Vacuum Collector's cadence, so a pull reads the same. */
	public static final int SWEEP_TICKS = 5;
	/** Most drops and orbs one sweep acts on, the nearest first. */
	private static final int SWEEP_LIMIT = 16;
	/** How close the block's box counts as "touching": a drop resting against a face is taken. */
	private static final double TOUCH_MARGIN = 0.25;
	/** Pull speeds: the electromagnet's and the collector's, which read as a real magnet. */
	private static final double SEEK_SPEED = 0.55;
	private static final double SEEK_GAIN = 0.45;
	private static final double SEEK_SNAP = 0.1;
	/** Pulls without getting closer before a drop is let go, and for how long: two seconds each. */
	private static final int STALL_PULLS = 8;
	private static final int RELEASE_TICKS = 40;
	/** Ticks the trap stays the mob's last attacker: the experience rule reads it as "recently hit". */
	private static final int CREDIT_TICKS = 100;
	/** The fake player that stands for the trap in loot and experience rules; never in the world. */
	private static final GameProfile CREDIT = new GameProfile(
			UUID.nameUUIDFromBytes("alaindustrial:electric_mob_trap".getBytes(StandardCharsets.UTF_8)),
			"[Electric Mob Trap]");

	/** Block event id of a shock: the server sends it, the client's renderer draws the discharge. */
	public static final int EVENT_SHOCK = 1;

	/** The base channels, the experience gauge (permille and fluid id, as the other tank screens), the status. */
	public enum Channel { ENERGY, CAPACITY, PROGRESS, MAX_PROGRESS, XP_PERMILLE, XP_FLUID_ID, STATUS }

	public static final int DATA_COUNT = Channel.values().length;

	/** Sentinel for an empty tank on the fluid-id channel. */
	public static final int FLUID_ID_NONE = FluidTank.FLUID_ID_NONE;

	/** What the last pulse did, shown on the screen. */
	public enum Status implements StatusLine {
		/** It shocked a mob. */
		WORKING(false),
		/** No trappable mob in reach, or none close enough yet. */
		IDLE(false),
		/** Mobs are in reach but the buffer has no room for their drops. */
		BUFFER_FULL(true),
		/** Mobs are in reach but the buffer cannot pay for a shock. */
		NO_POWER(true);

		private final boolean blocking;

		Status(boolean blocking) {
			this.blocking = blocking;
		}

		@Override
		public String translationKey() {
			return "gui.alaindustrial.electric_mob_trap.status." + name().toLowerCase(Locale.ROOT);
		}

		@Override
		public boolean isBlocking() {
			return blocking;
		}

		public static Status byOrdinal(int ordinal) {
			Status[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : IDLE;
		}
	}

	/** The liquid-experience tank: filled only by the trap itself, drained by neighbours, never poured into. */
	public final FluidTank xpTank = new FluidTank(Config.electricMobTrapTankMb,
			fluid -> false,
			fluid -> true,
			() -> {
				setChanged();
				wake();
			});

	/** When each pulled mob was last given a path, by UUID; pruned every pulse. */
	private final Map<UUID, Long> pulledAt = new HashMap<>();
	private Status status = Status.IDLE;
	/**
	 * Game time of the next pulse — a schedule, not a countdown, so a sleep cut short by an energy delivery
	 * does not pulse early. Not saved: after a reload the trap pulses on its first tick.
	 */
	private long nextPulseTick;
	/** Game time of the next sweep of the drops in reach; a schedule, like the pulse. */
	private long nextSweepTick;
	private final StuckDropTracker stuck = new StuckDropTracker(STALL_PULLS, RELEASE_TICKS);
	/** Server side: the game tick of the last block event sent, so a pulse of four shocks sends one. */
	private long eventSentAt = Long.MIN_VALUE;
	/** Client side only, never saved: the game time the last shock event arrived, or {@link Long#MIN_VALUE}. */
	private long shockedAt = Long.MIN_VALUE;

	public ElectricMobTrapBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.ELECTRIC_MOB_TRAP_BE.get(), pos, state, EnergyTier.LV, SLOT_COUNT,
				Config.electricMobTrapBuffer, EnergyTier.LV.maxVoltage(), 0L);
	}

	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		long now = level.getGameTime();
		if (level instanceof ServerLevel serverLevel) {
			if (now >= nextSweepTick) {
				nextSweepTick = now + SWEEP_TICKS;
				sweep(serverLevel);
			}
			if (now >= nextPulseTick) {
				nextPulseTick = now + pulsePeriod();
				setStatus(pulse(serverLevel));
			}
		}
		// Mobs and drops arriving do not wake a block, so the trap never sleeps past its next sweep or pulse.
		return (int) Math.max(0, Math.min(nextSweepTick, nextPulseTick) - now - 1);
	}

	private static int pulsePeriod() {
		return Math.max(1, Config.electricMobTrapPulseTicks);
	}

	private void setStatus(Status next) {
		if (next != status) {
			status = next;
			setChanged();
		}
		// Lit means ready: powered for at least one shock, whether or not a mob is in reach. The pulse of the
		// lit model then runs whenever the trap could strike, and the discharge itself is drawn by the renderer.
		updateLit(energy.getAmount() >= strikeCost());
	}

	/**
	 * One pulse, outside the schedule: find the trappable mobs and the players in reach, pull the far mobs and
	 * shock everything near. Public for the gametests, which drive pulses one by one.
	 */
	public Status pulse(ServerLevel level) {
		long now = level.getGameTime();
		pulledAt.values().removeIf(at -> now - at > 4L * PULL_REPEAT_TICKS);
		Vec3 centre = centre();
		double strikeSqr = strikeRadius() * strikeRadius();
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, zone(), mob -> isTrappable(level, mob));
		List<Player> players = level.getEntitiesOfClass(Player.class, zone(),
				player -> isShockable(player) && player.distanceToSqr(centre) <= strikeSqr);
		if (mobs.isEmpty() && players.isEmpty()) {
			recordEuRate(0);
			return Status.IDLE;
		}
		long cost = strikeCost();
		if (energy.getAmount() < cost) {
			recordEuRate(0);
			return Status.NO_POWER;
		}
		if (players.isEmpty() && !bufferHasRoom()) {
			recordEuRate(0);
			return Status.BUFFER_FULL;
		}
		List<LivingEntity> near = new ArrayList<>(players);
		List<Mob> far = new ArrayList<>();
		for (Mob mob : mobs) {
			if (mob.distanceToSqr(centre) <= strikeSqr) {
				near.add(mob);
			} else {
				far.add(mob);
			}
		}
		Comparator<LivingEntity> nearestFirst = Comparator.<LivingEntity>comparingDouble(e -> e.distanceToSqr(centre))
				.thenComparing(LivingEntity::getUUID);
		near.sort(nearestFirst);
		far.sort(nearestFirst);
		int strikeLimit = Math.max(1, Config.electricMobTrapStrikesPerPulse);
		int struck = 0;
		for (LivingEntity target : near) {
			if (struck < strikeLimit && energy.getAmount() >= cost && (target instanceof Player || bufferHasRoom())
					&& shock(level, target, cost)) {
				struck++;
			}
		}
		int pullLimit = Math.max(1, Config.electricMobTrapPullsPerPulse);
		int pulled = 0;
		for (Mob mob : far) {
			if (pulled < pullLimit && pull(mob, centre, now)) {
				pulled++;
			}
		}
		if (struck == 0) {
			recordEuRate(0);
			return !bufferHasRoom() && near.stream().anyMatch(Mob.class::isInstance) ? Status.BUFFER_FULL : Status.IDLE;
		}
		recordEuRate((int) Math.max(1, (cost * struck + pulsePeriod() - 1) / pulsePeriod()));
		setChanged();
		return Status.WORKING;
	}

	// --- who the trap acts on ---

	/**
	 * Whether the trap acts on {@code mob} at all: a living monster, calm or angry, not a boss or a shulker, not
	 * under a repeller.
	 * Public for the gametests.
	 */
	public static boolean isTrappable(ServerLevel level, Mob mob) {
		return mob instanceof Enemy && mob.isAlive() && !isBoss(mob) && !(mob instanceof Shulker)
				&& !MobRepellerField.isRepelled(level, mob);
	}

	/** The three vanilla bosses. 26.3 has no boss tag, so they are named; another mod's boss is its own affair. */
	public static boolean isBoss(Entity entity) {
		return entity instanceof EnderDragon || entity instanceof WitherBoss || entity instanceof Warden;
	}

	/**
	 * A player the trap shocks: alive, in survival or adventure. A creative (invulnerable) or spectator player is
	 * left alone.
	 */
	public static boolean isShockable(Player player) {
		return player.isAlive() && !player.getAbilities().invulnerable && !player.isSpectator();
	}

	// --- the pull ---

	/**
	 * Draw {@code mob} one step towards the centre. Returns whether anything was done to it. A mob chasing a
	 * player forgets the player; a brain-driven one is given the centre as its walk target, since its brain
	 * overwrites any path the navigation is handed.
	 */
	private boolean pull(Mob mob, Vec3 centre, long now) {
		Long last = pulledAt.get(mob.getUUID());
		if (last != null && now - last < PULL_REPEAT_TICKS) {
			return false;
		}
		if (mob.getTarget() instanceof Player) {
			mob.setTarget(null);
		}
		if (MobRepellerField.isBrainMover(mob)) {
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(centre, (float) PULL_SPEED, 1));
			pulledAt.put(mob.getUUID(), now);
			return true;
		}
		if (mob instanceof PathfinderMob pathfinder) {
			if (pathfinder.getNavigation().moveTo(centre.x, centre.y, centre.z, PULL_SPEED)) {
				pulledAt.put(mob.getUUID(), now);
				return true;
			}
			return false;
		}
		Vec3 towards = centre.subtract(mob.position());
		if (towards.horizontalDistanceSqr() < 1.0E-4) {
			return false;
		}
		Vec3 dir = new Vec3(towards.x, 0, towards.z).normalize();
		mob.push(new Vec3(dir.x * NUDGE, 0, dir.z * NUDGE));
		return true;
	}

	// --- the shock ---

	/**
	 * Shock one mob or player. Returns whether the shock landed; only then is it paid for. A mob is first
	 * credited to the trap's fake player, so it drops the loot of a player's kill. Public for the gametests,
	 * which drive single shocks.
	 */
	public boolean shock(ServerLevel level, LivingEntity target, long cost) {
		ServerPlayer credit = FakePlayers.get().get(level, CREDIT);
		if (target instanceof Mob mob) {
			mob.setLastHurtByPlayer(credit, CREDIT_TICKS);
		}
		// An enderman teleports away from a hurt no living attacker dealt, so its shock names the fake player.
		boolean enderman = LineMobs.isEnderman(target);
		DamageSource source = enderman
				? ModDamageTypes.electricShock(level, credit)
				: ModDamageTypes.electricShock(level);
		if (!target.hurtServer(level, source, Math.max(1, Config.electricMobTrapDamage))) {
			return false;
		}
		if (enderman && target.isAlive()) {
			((Mob) target).setTarget(null);
			target.setLastHurtByMob(null);
		}
		energy.drainInternal(cost);
		announceShock(level);
		spark(level, target);
		return true;
	}

	// --- drawing the drops in ---

	/**
	 * One sweep, outside the schedule: act on the nearest loose drops and orbs in reach, as the Vacuum Collector
	 * does. Returns how many it pulled or took. Public for the gametests.
	 */
	public int sweep(ServerLevel level) {
		long now = level.getGameTime();
		Vec3 centre = centre();
		List<Entity> loose = new ArrayList<>();
		loose.addAll(level.getEntitiesOfClass(ItemEntity.class, zone(),
				item -> isLooseDrop(item) && roomFor(item.getItem()) > 0 && !stuck.isReleased(item, now)));
		loose.addAll(level.getEntitiesOfClass(ExperienceOrb.class, zone(),
				orb -> orb.isAlive() && orbFits(orb) && !stuck.isReleased(orb, now)));
		if (loose.isEmpty()) {
			return 0;
		}
		loose.sort(Comparator.<Entity>comparingDouble(e -> e.distanceToSqr(centre)).thenComparing(Entity::getUUID));
		int acted = 0;
		for (Entity drop : loose.subList(0, Math.min(SWEEP_LIMIT, loose.size()))) {
			if (collect(drop, centre, now)) {
				acted++;
			}
		}
		if (acted > 0) {
			setChanged();
		}
		return acted;
	}

	/** Take {@code drop} if it touches the block, else pull it towards the centre. Returns whether it acted. */
	private boolean collect(Entity drop, Vec3 centre, long now) {
		if (drop.getBoundingBox().intersects(new AABB(getBlockPos()).inflate(TOUCH_MARGIN))) {
			if (drop instanceof ItemEntity item) {
				takeItem(item);
			} else if (drop instanceof ExperienceOrb orb) {
				takeOrb(orb);
			}
			stuck.forget(drop);
			return true;
		}
		double distance = Math.sqrt(drop.distanceToSqr(centre));
		if (!DropSeek.seek(drop, centre, SEEK_SPEED, SEEK_GAIN, SEEK_SNAP)) {
			return false;
		}
		stuck.recordPull(drop, distance, now);
		return true;
	}

	/** A live, non-empty drop past its pickup delay and not in lava: what the trap draws in. */
	private static boolean isLooseDrop(ItemEntity item) {
		return item.isAlive() && !item.getItem().isEmpty() && !item.hasPickUpDelay() && !item.isInLava();
	}

	/** Whether the tank can hold the whole of {@code orb}: an orb is taken whole or not at all. */
	private boolean orbFits(ExperienceOrb orb) {
		long mb = (long) orb.getValue() * ExperienceFluid.MB_PER_POINT;
		return mb > 0 && xpTank.amount + mb <= xpTank.capacity;
	}

	/** Move as many of the drop's items into the buffer as fit; the rest stays in the world as the same entity. */
	private void takeItem(ItemEntity item) {
		ItemStack stack = item.getItem();
		int moved = insert(stack);
		if (moved <= 0) {
			return;
		}
		stack.shrink(moved);
		if (stack.isEmpty()) {
			item.discard();
		} else {
			item.setItem(stack);
		}
	}

	/** Turn a whole orb into liquid experience; an orb the tank cannot hold whole stays in the world. */
	private void takeOrb(ExperienceOrb orb) {
		if (!orbFits(orb)) {
			return;
		}
		// The machine making its own product bypasses the tank's insert guard (false: nothing may be poured in).
		xpTank.fluid = FluidHolder.of(ModContent.EXPERIENCE.get());
		xpTank.amount += (long) orb.getValue() * ExperienceFluid.MB_PER_POINT;
		orb.discard();
		wake();
	}

	/** One block event per tick with a shock, however many mobs were shocked in it: the discharge is per block. */
	private void announceShock(ServerLevel level) {
		long now = level.getGameTime();
		if (now != eventSentAt) {
			eventSentAt = now;
			level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_SHOCK, 0);
		}
	}

	/** Client side: remember when the shock arrived; the renderer draws the discharge from this. */
	@Override
	public boolean triggerEvent(int id, int param) {
		if (id == EVENT_SHOCK) {
			if (level != null && level.isClientSide()) {
				shockedAt = level.getGameTime();
			}
			return true;
		}
		return super.triggerEvent(id, param);
	}

	/** Client side: whether a shock has been seen since the block entity was created. */
	public boolean hasShocked() {
		return shockedAt != Long.MIN_VALUE;
	}

	/** Client side: the game time of the last shock seen; only meaningful when {@link #hasShocked()}. */
	public long shockedAt() {
		return shockedAt;
	}

	/** A line of sparks from the ring on top of the block to the target, and a short crack. */
	private void spark(ServerLevel level, LivingEntity target) {
		Vec3 from = centre().add(0, 0.6, 0);
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
		for (int i = 0; i <= 5; i++) {
			Vec3 p = from.lerp(to, i / 5.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.05);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 8, 0.25, 0.35, 0.25, 0.08);
		level.playSound(null, worldPosition, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.35f, 1.8f);
	}

	// --- geometry and cost ---

	private static long strikeCost() {
		return Math.max(1, Config.electricMobTrapEuPerStrike);
	}

	private static double strikeRadius() {
		return Math.max(1, Config.electricMobTrapStrikeRadius);
	}

	/** The reach: half the side of the zone cube, from the centre of the block — also what the preview draws. */
	public static double reach() {
		return Math.max(1, Config.electricMobTrapRadius);
	}

	private AABB zone() {
		double r = reach();
		return AABB.ofSize(centre(), r * 2.0, r * 2.0, r * 2.0);
	}

	private Vec3 centre() {
		return Vec3.atCenterOf(getBlockPos());
	}

	// --- the buffer ---

	/** The cap of one buffer slot for {@code stack}: its own stack size, never above the machine family's. */
	private static int slotLimit(ItemStack stack) {
		return Math.min(OUTPUT_MAX, stack.getMaxStackSize());
	}

	/** Whether any slot is empty or not yet at its cap: the trap shocks only while there is somewhere to put drops. */
	boolean bufferHasRoom() {
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			ItemStack held = items.get(slot);
			if (held.isEmpty() || held.getCount() < slotLimit(held)) {
				return true;
			}
		}
		return false;
	}

	/** How many items of {@code stack} the buffer has room for, up to its count. */
	int roomFor(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		int room = 0;
		int limit = slotLimit(stack);
		for (int slot = 0; slot < SLOT_COUNT && room < stack.getCount(); slot++) {
			ItemStack held = items.get(slot);
			if (held.isEmpty()) {
				room += limit;
			} else if (ItemStack.isSameItemSameComponents(held, stack)) {
				room += Math.max(0, limit - held.getCount());
			}
		}
		return Math.min(room, stack.getCount());
	}

	/** Put as many of {@code stack}'s items as fit into the buffer, matching stacks first; returns how many. */
	private int insert(ItemStack stack) {
		int left = roomFor(stack);
		int moved = 0;
		int limit = slotLimit(stack);
		for (int slot = 0; slot < SLOT_COUNT && left > 0; slot++) {
			ItemStack held = items.get(slot);
			if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, stack)) {
				int add = Math.min(left, limit - held.getCount());
				if (add > 0) {
					held.grow(add);
					left -= add;
					moved += add;
				}
			}
		}
		for (int slot = 0; slot < SLOT_COUNT && left > 0; slot++) {
			if (items.get(slot).isEmpty()) {
				int add = Math.min(left, limit);
				items.set(slot, stack.copyWithCount(add));
				left -= add;
				moved += add;
			}
		}
		return moved;
	}

	/** Nothing goes in by hand, hopper or pipe: the buffer is filled only by the trap. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return false;
	}

	/** Every buffer slot gives its items to automation, through the faces of {@link #getSlotsForFace}. */
	@Override
	protected boolean isOutputSlot(int slot) {
		return slot >= 0 && slot < SLOT_COUNT;
	}

	/** Items leave through the lid and the floor only; the sides are for cables. */
	@Override
	public int[] getSlotsForFace(Direction side) {
		return side.getAxis().isVertical() ? BUFFER_SLOTS : NO_SLOTS;
	}

	/** Liquid experience leaves through the same two faces as the items. */
	@Override
	public FluidPort fluidPort(Direction side) {
		return side.getAxis().isVertical() ? xpTank : null;
	}

	/** Energy comes in through the four sides; the lid and the floor are the output lines. */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace.getAxis().isHorizontal() ? EnergyRole.IN : EnergyRole.NONE;
	}

	// --- sync, menu, persistence ---

	/** The status the last pulse left, as the screen reads it. */
	public Status status() {
		return status;
	}

	@Override
	protected SyncChannels createChannels() {
		return channels(Channel.class)
				.read(Channel.XP_PERMILLE, () -> SyncChannels.permille(xpTank.amount, xpTank.capacity))
				.read(Channel.XP_FLUID_ID, () -> xpTank.fluidSyncId())
				.read(Channel.STATUS, () -> status.ordinal())
				.build();
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		// Tell the opening player which block the screen belongs to, so the preview button shows its state.
		ZonePreviewPayload.send(player, level, worldPosition, reach(), false);
		return new ElectricMobTrapMenu(syncId, inventory, this,
				ContainerLevelAccess.create(getLevel(), getBlockPos()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		xpTank.save(output, "Experience");
	}

	@Override
	protected void loadMachineData(ValueInput input) {
		super.loadMachineData(input);
		xpTank.load(input, "Experience");
	}

	/** Its per-tick need for the network (MOD-789, ADR-049): the peak draw, every shock of a pulse paid. */
	@Override
	public long perTickDemand() {
		long perPulse = strikeCost() * Math.max(1, Config.electricMobTrapStrikesPerPulse);
		return Math.max(1, (perPulse + pulsePeriod() - 1) / pulsePeriod());
	}
}
