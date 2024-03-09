package org.dstadler.ctw.geojson;

import java.io.IOException;

import org.dstadler.commons.testing.PrivateConstructorCoverage;
import org.junit.jupiter.api.Test;

class CreateLargestRectangleGeoJSONTilesTest {
	@Test
	void test() throws IOException {
		// for now simply run the application
		CreateLargestRectangleGeoJSONTiles.main(new String[0]);
	}

	// helper method to get coverage of the unused constructor
	@Test
	public void testPrivateConstructor() throws Exception {
		PrivateConstructorCoverage.executePrivateConstructor(CreateLargestRectangleGeoJSONTiles.class);
	}
}