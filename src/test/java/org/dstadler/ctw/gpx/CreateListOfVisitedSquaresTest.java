package org.dstadler.ctw.gpx;

import java.io.IOException;

import org.dstadler.commons.testing.PrivateConstructorCoverage;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

class CreateListOfVisitedSquaresTest {
	@Test
	void test() throws IOException, SAXException {
		// for now simply run the application
		CreateListOfVisitedSquares.main(new String[0]);
	}

	// helper method to get coverage of the unused constructor
	@Test
	void testPrivateConstructor() throws Exception {
		PrivateConstructorCoverage.executePrivateConstructor(CreateListOfVisitedSquares.class);
	}
}
