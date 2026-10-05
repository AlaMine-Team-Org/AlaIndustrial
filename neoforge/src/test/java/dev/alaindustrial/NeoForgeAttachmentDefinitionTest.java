package dev.alaindustrial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.stats.PlayerModStats;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.function.BiPredicate;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * L1.5 characterization of the two player attachments as NeoForge defines them (MOD-708, batch 0): the id,
 * the save form, survival of death and owner-only sync. NeoForge only: the definition is NeoForge's
 * {@link AttachmentType}; Fabric's is pinned by {@code PlayerAttachmentDefinitionGameTest}.
 *
 * <p>Checked on the definition, not in a world: NeoForge's mock player does not accept the mod's payloads,
 * so a sync could not be observed in a gametest. The attachment is looked up by id in
 * {@code NeoForgeRegistries.ATTACHMENT_TYPES}, so moving the declaration into a shared list leaves this test
 * untouched.
 *
 * <p>{@code AttachmentType} keeps its definition in package-private fields ({@code serializer},
 * {@code copyOnDeath}, {@code syncHandler}); the builder wraps a {@code MapCodec} in
 * {@code AttachmentType$Builder$1} (field {@code val$codec}) and a sync predicate with its stream codec in
 * {@code AttachmentType$Builder$2} ({@code val$sendToPlayer}, {@code val$streamCodec}) — javap against
 * neoforge 26.3.0.7-beta. They are read by reflection. The save form must be the record's
 * {@code MAP_CODEC}: the form every NeoForge player file holds (the round trip itself is
 * {@code PlayerAttachmentRoundTripScenarios}).
 *
 * <p>Owner-only sync is asked of the declared predicate directly: a holder that is the player itself is
 * synced, any other holder is not.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class NeoForgeAttachmentDefinitionTest {

	private static Object field(Object owner, String name) throws ReflectiveOperationException {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

	private static Object attachmentField(AttachmentType<?> type, String name) throws ReflectiveOperationException {
		Field field = AttachmentType.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(type);
	}

	@SuppressWarnings("unchecked")
	private static void assertDefinition(String path, Object empty, Object mapCodec, Object streamCodec)
			throws ReflectiveOperationException {
		Identifier id = Industrialization.id(path);
		assertTrue(NeoForgeRegistries.ATTACHMENT_TYPES.containsKey(id), id + " is not registered");
		AttachmentType<?> type = NeoForgeRegistries.ATTACHMENT_TYPES.getValue(id);
		assertNotNull(type, id + " is not registered");

		IAttachmentHolder stranger = (IAttachmentHolder) Proxy.newProxyInstance(
				IAttachmentHolder.class.getClassLoader(), new Class<?>[] {IAttachmentHolder.class},
				(proxy, method, args) -> {
					throw new UnsupportedOperationException(method.getName());
				});
		java.util.function.Function<IAttachmentHolder, ?> defaults =
				(java.util.function.Function<IAttachmentHolder, ?>) attachmentField(type, "defaultValueSupplier");
		assertSame(empty, defaults.apply(stranger), id + " must start from EMPTY");

		Object serializer = attachmentField(type, "serializer");
		assertNotNull(serializer, id + " must be persisted");
		assertSame(mapCodec, field(serializer, "val$codec"), id + " must be persisted through the record's MAP_CODEC");

		assertEquals(Boolean.TRUE, attachmentField(type, "copyOnDeath"), id + " must be copied on death");

		Object sync = attachmentField(type, "syncHandler");
		assertNotNull(sync, id + " must be synced");
		assertSame(streamCodec, field(sync, "val$streamCodec"), id + " must sync through the record's STREAM_CODEC");
		BiPredicate<Object, Object> toOwner = (BiPredicate<Object, Object>) field(sync, "val$sendToPlayer");
		assertTrue(toOwner.test(null, null), id + ": a holder that is the player itself must be synced");
		assertFalse(toOwner.test(stranger, null), id + ": a holder must not be synced to another player");
	}

	/**
	 * @implements MOD-708-ATT02 — both player attachments keep their id, MapCodec save form, copy on death
	 *     and owner-only sync on NeoForge
	 */
	@Test
	void playerAttachmentsKeepTheirDefinition() throws ReflectiveOperationException {
		assertDefinition("player_stats", PlayerModStats.EMPTY, PlayerModStats.MAP_CODEC, PlayerModStats.STREAM_CODEC);
		assertDefinition("player_skills", PlayerSkills.EMPTY, PlayerSkills.MAP_CODEC, PlayerSkills.STREAM_CODEC);
	}
}
