package org.dstadler.ctw.geojson;

import java.io.IOException;

import org.dstadler.commons.testing.PrivateConstructorCoverage;
import org.junit.jupiter.api.Test;

class CreateLargestClusterGeoJSONSquaresTest {
	@Test
	void test() throws IOException {
		// for now simply run the application
		CreateLargestClusterGeoJSONSquares.main(new String[0]);
	}

	// helper method to get coverage of the unused constructor
	@Test
	void testPrivateConstructor() throws Exception {
		PrivateConstructorCoverage.executePrivateConstructor(CreateLargestClusterGeoJSONSquares.class);
	}
}
