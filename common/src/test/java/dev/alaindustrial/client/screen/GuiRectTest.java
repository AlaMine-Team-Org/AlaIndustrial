package dev.alaindustrial.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** The click-area record the screens publish (MOD-716): it carries exactly the four numbers it was given. */
class GuiRectTest {

	@Test
	void keepsItsFourNumbers() {
		GuiRect rect = new GuiRect(82, 38, 25, 9);
		assertEquals(82, rect.x());
		assertEquals(38, rect.y());
		assertEquals(25, rect.width());
		assertEquals(9, rect.height());
	}

	@Test
	void equalNumbersMakeEqualRects() {
		assertEquals(new GuiRect(1, 2, 3, 4), new GuiRect(1, 2, 3, 4));
		assertNotEquals(new GuiRect(1, 2, 3, 4), new GuiRect(1, 2, 3, 5));
	}
}
