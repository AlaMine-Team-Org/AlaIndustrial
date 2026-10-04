package dev.alaindustrial.gametest;

import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.impl.attachment.AttachmentRegistryImpl;
import net.fabricmc.fabric.impl.attachment.AttachmentTypeImpl;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fabric entry point for the player-attachment characterization (MOD-708, batch 0). The checks are
 * loader-neutral ({@link PlayerAttachmentDefinitionScenarios}); the probe reads Fabric's
 * {@code AttachmentType}, whose save form is the record's {@code CODEC} ({@code persistent(codec)}).
 *
 * <p>The registry lookup and the sync side live on the implementation classes
 * ({@code AttachmentRegistryImpl.get}, {@code AttachmentTypeImpl.syncPredicate/streamCodec}, verified by
 * javap against fabric-data-attachment-api-v1 2.2.30 for 26.3); the public interface has no read side for
 * either. Owner-only sync is fabric-api's {@code targetOnly()}, a non-capturing lambda, so the declared
 * predicate is that very instance — and the type must also be among fabric-api's syncable attachments.
 */
@SuppressWarnings("UnstableApiUsage")
public class PlayerAttachmentDefinitionGameTest {

	private static final PlayerAttachmentDefinitionScenarios.Probe PROBE = id -> {
		AttachmentType<?> type = AttachmentRegistryImpl.get(id);
		if (type == null || !id.equals(type.identifier())) {
			return null;
		}
		AttachmentTypeImpl<?> impl = type instanceof AttachmentTypeImpl<?> cast ? cast : null;
		boolean synced = type.isSynced() && impl != null
				&& AttachmentRegistryImpl.getSyncableAttachments().contains(id);
		return new PlayerAttachmentDefinitionScenarios.Definition(
				type.initializer() == null ? null : type.initializer().get(),
				type.isPersistent() ? type.persistenceCodec() : null,
				type.copyOnDeath(),
				synced ? impl.streamCodec() : null,
				synced && impl.syncPredicate() == AttachmentSyncPredicate.targetOnly());
	};

	/**
	 * @implements MOD-708-ATT01 — both player attachments keep their id, codec save form, copy on death
	 *     and owner-only sync on Fabric
	 */
	@GameTest
	public void playerAttachmentsKeepTheirDefinition(GameTestHelper helper) {
		PlayerAttachmentDefinitionScenarios.attachmentsKeepTheirDefinition(helper, PROBE,
				PlayerAttachmentDefinitionScenarios.SaveForm.CODEC);
	}
}
