package dev.alaindustrial.entity;

import dev.alaindustrial.settlement.shift.ShiftMode;
import dev.alaindustrial.settlement.shift.ShiftWorkerBattery;
import dev.alaindustrial.settlement.SettlementConfig;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfiles;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import dev.alaindustrial.settlement.shift.ShiftWorkers;
import java.util.OptionalLong;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A shift worker (MOD-832, docs/mobs/shift_worker.md): a former record of this world in a working suit, with a number
 * on its back instead of a name. The second mob of the mod and the first with a will of its own.
 *
 * <p><b>The entity is a body, not the truth.</b> Everything about the worker — its number, look, town, status —
 * is its record in the towns' registry ({@code ShiftWorkerBook}, ADR-050 point 8), found by this entity's UUID. The
 * entity saves nothing of its own beyond what every mob saves; on its first server tick it looks its record up and
 * leaves the world without a drop if there is none, if the record is not alive, or if the ledger does not hold its
 * number as a living worker's ({@link ShiftWorkers#bind}). The client gets the number, the look and the body through
 * the synced data below.
 *
 * <p><b>What it does.</b> Asks the shift planner every 20 ticks, offset by its id ({@link ShiftWorkers#think}), and
 * walks within its home radius through the town's path dispatcher ({@link ShiftWorkerWalkGoal}). It looks at a
 * player near it, never at one in creative or spectator mode ({@link ShiftWorkerLookGoal}, MOD-847). It does not
 * despawn, does not use portals, cannot be leashed, and has no voice (R4).
 *
 * <p><b>Death</b> is at once and for good (R32, R10a): no flight home, no last chance. {@link #die} hands it to
 * {@link ShiftWorkers#recordDeath}, which writes the hall's log and closes the number unless the server turned
 * permadeath off (R30, R39). A player who does not own the worker's town cannot hurt it (R34). While its town's
 * core is dark the worker hibernates — goals off where it stands (R10) — and the town wakes it when the core lights.
 */
public class ShiftWorker extends PathfinderMob {

	private static final EntityDataAccessor<String> DATA_NUMBER =
			SynchedEntityData.defineId(ShiftWorker.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<String> DATA_LOOK =
			SynchedEntityData.defineId(ShiftWorker.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Boolean> DATA_SLIM =
			SynchedEntityData.defineId(ShiftWorker.class, EntityDataSerializers.BOOLEAN);

	/** Base walking speed, a villager's. */
	public static final double MOVEMENT_SPEED = 0.5;
	/** Speed factor of a stroll, a villager's. */
	static final double STROLL_SPEED = 0.6;

	/** Server only: the mode the planner gave last. */
	private ShiftMode mode = ShiftMode.IDLE;
	/** Server only: the record was looked up since the entity joined the world. */
	private boolean bound;
	/** Server only: the worker's town number, copied from its record when it is bound (0 — none). */
	private int settlement;
	/** Server only: how far it walks from its anchor — the knob, never past its town's territory. */
	private int wanderRadius = SettlementConfig.shiftWorkerWanderRadius;
	/** Server only: game time of the last battery settle, -1 — none yet (MOD-854, {@code ShiftWorkerBattery}). */
	private long batteryClock = -1L;
	/** Server only: the fraction of an EU drained but not yet taken from the battery; not saved. */
	private double drainCarry;
	/** Server only: game time of the last wound, -1 — none; one fight is one service mark. */
	private long lastHurtAt = -1L;

	public ShiftWorker(EntityType<? extends ShiftWorker> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	/**
	 * The id a body made only to be looked at carries ({@link #displayBody}). A level hands out ids from 1 upwards
	 * when it adds an entity, so a negative id is never a real one; this body is never added to any level, so nothing
	 * can look it up by this id either.
	 */
	public static final int DISPLAY_ENTITY_ID = -854_001;

	/**
	 * A worker's body for a screen to draw (MOD-854, the card of a worker this client has not loaded): made through
	 * {@code type}'s factory as the game spawns one, never added to {@code level}. Since 26.3 an entity has no id until
	 * a level adds it, and {@code getId()} throws until then; the renderer asks for it (the held items' model seed),
	 * so the body gets {@link #DISPLAY_ENTITY_ID} here. Null when the type makes nothing.
	 */
	public static @Nullable ShiftWorker displayBody(EntityType<? extends ShiftWorker> type, Level level) {
		ShiftWorker body = type.create(level, EntitySpawnReason.LOAD);
		if (body != null) {
			body.setId(DISPLAY_ENTITY_ID);
		}
		return body;
	}

	/** Default attributes: a player's health (the knob scales a new worker's), a villager's speed. */
	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_NUMBER, "");
		entityData.define(DATA_LOOK, "");
		entityData.define(DATA_SLIM, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(5, new ShiftWorkerWalkGoal(this));
		goalSelector.addGoal(8, new ShiftWorkerLookGoal(this));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	@Override
	public void tick() {
		if (!bound && level() instanceof ServerLevel serverLevel) {
			bound = true;
			if (!ShiftWorkers.bind(serverLevel, this)) {
				discard();
				return;
			}
		}
		super.tick();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		ShiftWorkers.think(level, this);
	}

	/** Shows {@code record} to the clients: the number, the look and the body. Server side. */
	public void showRecord(ShiftWorkerRecord record) {
		entityData.set(DATA_NUMBER, record.number().toString());
		entityData.set(DATA_LOOK, record.look().toString());
		entityData.set(DATA_SLIM, record.slim());
	}

	/** The number on the back, {@code <letter>-<NNN>}; empty until the record was found. */
	public String number() {
		return entityData.get(DATA_NUMBER);
	}

	/** The id of the look, as text; empty until the record was found. */
	public String lookId() {
		return entityData.get(DATA_LOOK);
	}

	/** The slim body — a woman (R3g). */
	public boolean slim() {
		return entityData.get(DATA_SLIM);
	}

	/** The mode the planner gave last (server side). */
	public ShiftMode mode() {
		return mode;
	}

	/** Server side, by the planner only. */
	public void setMode(ShiftMode mode) {
		this.mode = mode;
	}

	/** The wander radius this worker is held to — the knob, never past its town's territory. */
	public int wanderRadius() {
		return wanderRadius;
	}

	/** Game time of the last battery settle, -1 — none yet. Server side. */
	public long batteryClock() {
		return batteryClock;
	}

	/** Server side, by {@code ShiftWorkerBattery} only. */
	public void setBatteryClock(long gameTime) {
		this.batteryClock = gameTime;
	}

	/** The fraction of an EU drained but not yet taken. Server side. */
	public double drainCarry() {
		return drainCarry;
	}

	/** Server side, by {@code ShiftWorkerBattery} only. */
	public void setDrainCarry(double carry) {
		this.drainCarry = carry;
	}

	/** Game time of the last wound, -1 — none. Server side. */
	public long lastHurtAt() {
		return lastHurtAt;
	}

	/** Server side, by {@code ShiftWorkerBattery} only. */
	public void setLastHurtAt(long gameTime) {
		this.lastHurtAt = gameTime;
	}

	/** The worker's town number (0 — none), as its record says. Server side. */
	public int settlement() {
		return settlement;
	}

	/**
	 * Puts the worker under its town: its number, its anchor (the town's core) and how far it may walk. Server side,
	 * by {@code ShiftWorkers} when the worker is made or bound; nothing of this is saved with the entity.
	 */
	public void joinTown(int settlement, BlockPos anchor, int wanderRadius) {
		this.settlement = settlement;
		this.wanderRadius = Math.max(1, wanderRadius);
		setHomeTo(anchor, this.wanderRadius);
	}

	/** The number is the name, until a player gives one (R3v, a later stage). */
	@Override
	protected Component getTypeName() {
		String number = number();
		return number.isEmpty() ? super.getTypeName() : Component.literal(number);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() instanceof Player player && !ShiftWorkers.mayHurt(level, this, player)) {
			return false;
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt) {
			ShiftWorkerBattery.noteWound(level, this, source);
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		if (!isRemoved() && !dead && level() instanceof ServerLevel serverLevel) {
			ShiftWorkers.recordDeath(serverLevel, this, source);
		}
		super.die(source);
	}

	/**
	 * A right click with a charged battery charges the worker ({@link ShiftWorkerBattery#chargeFrom}, a line on the
	 * action bar); any other right click opens its card for anyone (MOD-854, R74 — others see it read-only).
	 */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			OptionalLong moved = ShiftWorkerBattery.chargeFrom(serverPlayer.level(), this, player.getItemInHand(hand));
			if (moved.isPresent()) {
				serverPlayer.sendOverlayMessage(ShiftWorkerBattery.chargeMessage(number(), moved.getAsLong()));
			} else {
				ShiftWorkerProfiles.openFromEntity(serverPlayer, this);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public boolean canUsePortal(boolean allowPassengers) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}
}
