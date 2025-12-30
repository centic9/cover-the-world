package org.dstadler.ctw.tiles;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class CreateTileOverlaysHelperTest {

	@Test
	void concatProgress() {
		// this can be replaced by @DefaultLocale in JUnit 6.1.0
		Locale prev = Locale.getDefault();
		try {
			Locale.setDefault(Locale.GERMAN);

			CreateTileOverlaysHelper.ACTUAL.clear();
			CreateTileOverlaysHelper.EXPECTED.clear();

			assertEquals(
					", 0:0%, 1:0%, 2:0%, 3:0%, 4:0%, 5:0%, 6:0%, 7:0%, 8:0%, 9:0%, 10:0%, 11:0%, 12:0%, 13:0%, 14:0%, 15:0%, 16:0%, 17:0%, 18:0%",
					CreateTileOverlaysHelper.concatProgress());

			CreateTileOverlaysHelper.EXPECTED.add(10, 2349);
			assertEquals(
					", 0:0%, 1:0%, 2:0%, 3:0%, 4:0%, 5:0%, 6:0%, 7:0%, 8:0%, 9:0%, 10:0,00%, 11:0%, 12:0%, 13:0%, 14:0%, 15:0%, 16:0%, 17:0%, 18:0%",
					CreateTileOverlaysHelper.concatProgress());

			CreateTileOverlaysHelper.ACTUAL.add(10, 1623);
			assertEquals(
					", 0:0%, 1:0%, 2:0%, 3:0%, 4:0%, 5:0%, 6:0%, 7:0%, 8:0%, 9:0%, 10:69%, 11:0%, 12:0%, 13:0%, 14:0%, 15:0%, 16:0%, 17:0%, 18:0%",
					CreateTileOverlaysHelper.concatProgress());

			CreateTileOverlaysHelper.ACTUAL.add(11, -1);
			assertEquals(
					", 0:0%, 1:0%, 2:0%, 3:0%, 4:0%, 5:0%, 6:0%, 7:0%, 8:0%, 9:0%, 10:69%, 11:_, 12:0%, 13:0%, 14:0%, 15:0%, 16:0%, 17:0%, 18:0%",
					CreateTileOverlaysHelper.concatProgress());

			CreateTileOverlaysHelper.EXPECTED.add(12, 123);
			CreateTileOverlaysHelper.ACTUAL.add(12, 123);
			assertEquals(
					", 0:0%, 1:0%, 2:0%, 3:0%, 4:0%, 5:0%, 6:0%, 7:0%, 8:0%, 9:0%, 10:69%, 11:_, 13:0%, 14:0%, 15:0%, 16:0%, 17:0%, 18:0%",
					CreateTileOverlaysHelper.concatProgress());
		} finally {
			Locale.setDefault(prev);
		}
	}
}