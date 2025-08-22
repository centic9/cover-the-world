package org.dstadler.ctw;

import java.io.IOException;

import org.dstadler.commons.testing.PrivateConstructorCoverage;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

class CoverTheWorldTest {
	@Test
	void test() throws Throwable {
		// for now simply run the application
		CoverTheWorld.main(new String[0]);
	}

	// helper method to get coverage of the unused constructor
	@Test
	void testPrivateConstructor() throws Exception {
		PrivateConstructorCoverage.executePrivateConstructor(CoverTheWorld.class);
	}
}
