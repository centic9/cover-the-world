package org.dstadler.ctw;

import java.io.IOException;

import org.dstadler.commons.testing.PrivateConstructorCoverage;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

class CoverTheWorldTest {
	@Test
	void test() throws IOException, SAXException {
		// for now simply run the application
		CoverTheWorld.main(new String[0]);
	}

	// helper method to get coverage of the unused constructor
	@Test
	public void testPrivateConstructor() throws Exception {
		PrivateConstructorCoverage.executePrivateConstructor(CoverTheWorld.class);
	}
}