package org.dstadler.ctw.tiles;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.dstadler.commons.collections.ConcurrentMappedCounter;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.geotools.GeoTools;
import org.dstadler.ctw.utils.Constants;
import org.dstadler.ctw.utils.OSMTile;
import org.geotools.feature.FeatureCollection;

/**
 * Functionality which is shared by the applications which
 * create overlay pngs for covered tiles and squares.
 */
public class CreateTileOverlaysHelper {
	private static final Logger log = LoggerFactory.make();

	// for printing stats when writing tiles
	private static final AtomicLong lastLog = new AtomicLong();

	protected static final ConcurrentMappedCounter<Integer> EXPECTED = new ConcurrentMappedCounter<>();
	protected static final ConcurrentMappedCounter<Integer> ACTUAL = new ConcurrentMappedCounter<>();

	protected static Set<String> read(String file, String logName) throws IOException {
		Set<String> lines = new TreeSet<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			while (true) {
				String line = reader.readLine();
				if (line == null) {
					break;
				}

				// 33U 608000.0 5337000.0,33U 609000.0 5338000.0
				lines.add(line);
			}
		}

		log.info("Found " + lines.size() + " covered " + logName);

		return lines;
	}

	protected static void cleanTiles(File tileDir) {
		if (tileDir.exists()) {
			log.info("Removing previous tiles at " + tileDir);
			Arrays.stream(Objects.requireNonNull(tileDir.listFiles())).forEach(s -> {
				try {
					FileUtils.deleteDirectory(s);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	public static void forEachZoom(Consumer<Integer> task) throws InterruptedException {
		// prepare counters
		IntStream.rangeClosed(Constants.MIN_ZOOM, Constants.MAX_ZOOM).
				forEach(zoom -> {
					// indicate that this zoom is started
					EXPECTED.add(zoom, 0);
					ACTUAL.add(zoom, -1);
				});

		List<Integer> aList = IntStream.rangeClosed(Constants.MIN_ZOOM, Constants.MAX_ZOOM).boxed()
				.collect(Collectors.toList());

		ForkJoinPool customThreadPool = new ForkJoinPool(Constants.MAX_ZOOM - Constants.MIN_ZOOM);
		aList.forEach(
				zoom -> customThreadPool.submit(
					() -> task.accept(zoom)));

		customThreadPool.shutdown();
		if (!customThreadPool.awaitTermination(4,TimeUnit.HOURS)) {
			throw new IllegalStateException("Timed out while waiting for all tasks to finish");
		}
	}

	public static void writeTilesToFiles(File combinedDir, Set<OSMTile> tilesOut, File tileDir,
			FeatureCollection<?, ?> features, boolean borderOnly) {
		MutableInt tilesNr = new MutableInt(1);

		// process in parallel to make good use of CPU
		// if this is called from a thread inside a custom thread pool, it should be used
		// for scheduling the new tasks as well!
		tilesOut.stream().parallel().forEach(
				tile -> {
					try {
						writeTileToFile(combinedDir, tilesOut, tileDir, features, tile, tilesNr.getValue(), borderOnly);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}

					tilesNr.increment();
					ACTUAL.inc(tile.getZoom());
				}
		);
	}

	private static void writeTileToFile(File combinedDir,
			Set<OSMTile> tilesOut,
			File tileDir,
			FeatureCollection<?, ?> features,
			OSMTile tile,
			int tilesNr,
			boolean borderOnly) throws IOException {
		File file = tile.toFile(tileDir);

		// Save the image in PNG format using the javax.imageio API
		if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
			throw new IOException("Could not create directory at " + file.getParentFile());
		}

		if (borderOnly) {
			GeoTools.writeBorder(features, tile.getRectangle(), file);
		} else {
			GeoTools.writeImage(features, tile.getRectangle(), file);
		}

		// whenever writing a tile, remove the combined overlay to re-create it in a follow-up step
		/*if (written)*/
		{
			File combinedTile = new File(combinedDir, tile.toCoords() + ".png");
			if (combinedTile.exists()) {
				if (!combinedTile.delete()) {
					throw new IOException("Could not delete file " + combinedTile);
				}
			}
		}

		if (lastLog.get() + TimeUnit.SECONDS.toMillis(5) < System.currentTimeMillis()) {
			log.info(String.format(Locale.US, "%s -> png: overall %d of %d, zoom %d: %,d of %,d: %s%s",
					tileDir, actual(), expected(), tile.getZoom(), tilesNr, tilesOut.size(), tile.toCoords(), concatProgress()));

			lastLog.set(System.currentTimeMillis());
		}
	}

	protected static String concatProgress() {
		StringBuilder progress = new StringBuilder();
		for (int zoom = Constants.MIN_ZOOM; zoom <= Constants.MAX_ZOOM; zoom++) {
			long actual = ACTUAL.get(zoom);
			if (actual == -1) {
				progress.append(", ").append(zoom).append(":_");
				continue;
			}

			long expected = EXPECTED.get(zoom);

			// include if not started yet
			if (expected == 0) {
				progress.append(", ").append(zoom).append(":0%");
			} else if (actual != expected) {
				// otherwise include if not completed yet
				progress.append(
						String.format(", %d:%.0f%%",
								zoom, ((double) actual) / expected * 100));
			}
		}

		return progress.toString();
	}

	private static long actual() {
		long count = 0;
		for (int zoom = Constants.MIN_ZOOM; zoom <= Constants.MAX_ZOOM; zoom++) {
			long actual = ACTUAL.get(zoom);
			if (actual == -1) {
				continue;
			}

			count += actual;
		}

		return count;
	}

	private static long expected() {
		long count = 0;
		for (int zoom = Constants.MIN_ZOOM; zoom <= Constants.MAX_ZOOM; zoom++) {
			long actual = ACTUAL.get(zoom);
			if (actual == -1) {
				continue;
			}

			count += EXPECTED.get(zoom);
		}

		return count;
	}
}
