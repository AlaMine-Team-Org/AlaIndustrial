package dev.alaindustrial.entity;

import dev.alaindustrial.Industrialization;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The solid body of the quarry's drill (MOD-952, owner 2026-11-06): the drill is the controller's drawing and moves
 * through air where no block could stand, so a player walked straight through it. Each formed quarry keeps two of
 * these invisible bodies on its drill - the head and the pipe above it - and moves them with the drill every tick;
 * a player collides with them as with a boat.
 *
 * <p>A body is the controller's: it is never saved (the controller makes it again after a load), it takes no damage,
 * nothing can push it, and one its controller stopped moving for a while removes itself. Its size travels to the
 * client in synced data, because the pipe grows as the drill goes down.
 */
public class QuarryDrillBody extends Entity {

	public static final Identifier ID = Industrialization.id("quarry_drill_body");
	/** Ticks a body waits for its controller before it removes itself. */
	private static final int ORPHAN_TICKS = 40;

	private static final EntityDataAccessor<Float> WIDTH =
			SynchedEntityData.defineId(QuarryDrillBody.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> HEIGHT =
			SynchedEntityData.defineId(QuarryDrillBody.class, EntityDataSerializers.FLOAT);

	private int sinceMoved;

	public QuarryDrillBody(EntityType<? extends QuarryDrillBody> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	/** The registered type, read by id like every type the shared code makes. */
	@SuppressWarnings("unchecked")
	public static EntityType<QuarryDrillBody> type() {
		return (EntityType<QuarryDrillBody>) BuiltInRegistries.ENTITY_TYPE.getValue(ID);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(WIDTH, 1.0F);
		builder.define(HEIGHT, 1.0F);
	}

	/** Puts the body's bottom centre at {@code (x, y, z)} with this size; its controller calls it every tick. */
	public void place(double x, double y, double z, float width, float height) {
		if (Math.abs(entityData.get(WIDTH) - width) > 1.0E-3F) {
			entityData.set(WIDTH, width);
		}
		if (Math.abs(entityData.get(HEIGHT) - height) > 1.0E-3F) {
			entityData.set(HEIGHT, height);
		}
		setPos(x, y, z);
		sinceMoved = 0;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (WIDTH.equals(accessor) || HEIGHT.equals(accessor)) {
			refreshDimensions();
		}
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return EntityDimensions.fixed(entityData.get(WIDTH), entityData.get(HEIGHT));
	}

	@Override
	public void tick() {
		if (!level().isClientSide() && ++sinceMoved > ORPHAN_TICKS) {
			discard();
		}
	}

	/** Solid to whatever walks into it, the way a boat is. */
	@Override
	public boolean canBeCollidedWith(Entity other) {
		return true;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
