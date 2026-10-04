package dev.alaindustrial.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerData;

/**
 * What a machine menu's CLIENT constructor binds instead of the block entity it does not have: an empty
 * container of the machine's slot count and a data array of its channel count, both filled by the vanilla
 * sync. Built by {@code MachineMenu.clientStub(slotCount, dataCount)} (MOD-712, BE-10), so a client
 * constructor is one statement and its two widths are named in one place.
 *
 * <p>Both widths must match the block entity: a stub narrower than its {@code getDataAccess()} throws when
 * the screen reads the missing channel (MOD-234), one with fewer slots shifts the upgrade slots on the
 * client. {@code MenuDataWidthScenarios} and the {@code menu-client-stub-slot-count-from-block-entity}
 * text rule stand guard over both.
 */
public record MenuClientStub(Container container, ContainerData data) {
}
