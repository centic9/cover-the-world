package org.dstadler.ctw.gpx;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.dstadler.commons.testing.PrivateConstructorCoverage;
import org.dstadler.commons.util.ExecutorUtil;
import org.junit.jupiter.api.Disabled;
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

	@Disabled("Local testing for a multi-threading issue")
	@Test
	void testFutureConcurrent() throws IOException, ExecutionException, InterruptedException {
		// using an un-synchronized collection can cause issues
		// Collection<Future<?>> futures = new ArrayList<>();
		Collection<Future<?>> futures = new ConcurrentLinkedDeque<>();

		ExecutorService executor = Executors.newWorkStealingPool();

		try (Stream<Path> walk = Files.walk(Path.of("."), FileVisitOption.FOLLOW_LINKS)) {
			walk.
					parallel().
					forEach(path -> futures.add(executor.submit(() -> {
						// noop
					})));
		}

		Iterator<Future<?>> it = futures.iterator();
		while (it.hasNext()) {
			Future<?> future = it.next();
			assertNull(future.get());
			it.remove();
		}

		ExecutorUtil.shutdownAndAwaitTermination(executor, 10_000);
	}
}
