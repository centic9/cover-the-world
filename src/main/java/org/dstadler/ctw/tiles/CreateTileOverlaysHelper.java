package org.dstadler.ctw.tiles;

import static org.dstadler.ctw.tiles.CreateStaticTiles.TILE_DIR_COMBINED_TILES;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

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

	// for printing stats when writing tiles
	private static final AtomicLong lastLogTile = new AtomicLong();

	protected static boolean init(String[] args) throws IOException {
		LoggerFactory.initLogging();

		// as we write many small files, we do not want to use disk-based caching
		ImageIO.setUseCache(false);

		return !(args.length > 0 && "all".equals(args[0]));
	}

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


	protected static Set<OSMTile> generateTiles(Set<String> tilesIn, AtomicInteger tilesOverall, File tileDir,
			File jsonFile, Predicate<OSMTile> filter, boolean borderOnly) throws InterruptedException, IOException {
		// read GeoJSON from file to use it for rendering overlay images
		final FeatureCollection<?, ?> features = GeoTools.parseFeatureCollection(jsonFile);

		Set<OSMTile> allTiles = ConcurrentHashMap.newKeySet();

		CreateTileOverlaysHelper.forEachZoom(
				zoom -> generateTilesForOneZoom(zoom, tilesIn, tilesOverall, tileDir, filter, features, allTiles, borderOnly));

		return allTiles;
	}

	private static void generateTilesForOneZoom(int zoom, Set<String> tilesIn,
			AtomicInteger tilesOverall,
			File tileDir,
			Predicate<OSMTile> filter,
			FeatureCollection<?, ?> features, Set<OSMTile> allTiles, boolean borderOnly) {
		CreateTileOverlaysHelper.ACTUAL.add(zoom, 1);

		log.info(String.format("%s: Start processing of %d tiles at zoom %d%s",
				tileDir, tilesIn.size(), zoom, CreateTileOverlaysHelper.concatProgress()));

		Set<OSMTile> tilesOut = new HashSet<>();

		int tilesCount = tilesIn.size();
		int tilesNr = 1;
		for (String tileIn : tilesIn) {
			handleTile(tileIn, zoom, tilesOut, filter);

			if (lastLogTile.get() + TimeUnit.SECONDS.toMillis(5) < System.currentTimeMillis()) {
				log.info(String.format(Locale.US, "%s: Zoom %d: %,d of %,d: %s - %,d",
						tileDir, zoom, tilesNr, tilesCount, tileIn, tilesOut.size()));

				lastLogTile.set(System.currentTimeMillis());
			}

			tilesNr++;
		}

		log.info(String.format("%s: Having %d touched tiles for zoom %d%s",
				tileDir, tilesOut.size(), zoom, CreateTileOverlaysHelper.concatProgress()));
		CreateTileOverlaysHelper.EXPECTED.add(zoom, tilesOut.size());

		allTiles.addAll(tilesOut);
		int tilesOutSize = tilesOut.size();
		tilesOverall.addAndGet(tilesOutSize);

		CreateTileOverlaysHelper.writeTilesToFiles(TILE_DIR_COMBINED_TILES, tilesOut, tileDir, features, borderOnly);

		log.info(String.format("%s: Wrote %d files for zoom %d%s",
				tileDir, tilesOutSize, zoom, CreateTileOverlaysHelper.concatProgress()));
	}

	private static void handleTile(String tileIn, int zoom, Set<OSMTile> tiles, Predicate<OSMTile> filter) {
		// select starting and ending tile
		OSMTile ref = OSMTile.fromString(tileIn);

		// iterate over all "bounding" tiles at the given zoom
		// for zoom less or equal to TILE_ZOOM, this will be a single tile
		for (OSMTile tile : ref.getTilesAtZoom(zoom)) {
			// check if this tile should be included
			if (!filter.test(tile)) {
				continue;
			}

			tiles.add(tile);
		}
	}

	public static void forEachZoom(Consumer<Integer> task) throws InterruptedException {
		// prepare counters before starting any work
		IntStream.rangeClosed(Constants.MIN_ZOOM, Constants.MAX_ZOOM).
				forEach(zoom -> {
					// indicate that this zoom is started
					EXPECTED.add(zoom, 0);
					ACTUAL.add(zoom, -1);
				});

		ForkJoinPool customThreadPool = new ForkJoinPool(Constants.MAX_ZOOM - Constants.MIN_ZOOM + 1);

		AtomicReference<Throwable> throwable = new AtomicReference<>();

		// submit a task for each zoom-level
		for (int z = Constants.MIN_ZOOM; z <= Constants.MAX_ZOOM; z++) {
			final int zoom = z;
			customThreadPool.submit(() -> {
				String threadName = Thread.currentThread().getName();
				Thread.currentThread().setName(threadName + " zoom: " + zoom);
				try {
					task.accept(zoom);
				} catch (Throwable e) {
					throwable.set(e);
				} finally {
					Thread.currentThread().setName(threadName);
				}
			});
		}

		customThreadPool.shutdown();

		while(!customThreadPool.awaitTermination(1, TimeUnit.MINUTES)) {
			if (throwable.get() != null) {
				throw new IllegalStateException("Unexpected exception while executing tasks", throwable.get());
			}
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
					String threadName = Thread.currentThread().getName();
					Thread.currentThread().setName(threadName + " tile: " + tile);
					try {
						writeTileToFile(combinedDir, tilesOut, tileDir, features, tile, tilesNr.get().intValue(), borderOnly);
					} catch (IOException e) {
						throw new RuntimeException(e);
					} finally {
						Thread.currentThread().setName(threadName);
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
		File combinedTile = new File(combinedDir, tile.toCoords() + ".png");
		if (combinedTile.exists()) {
			if (!combinedTile.delete()) {
				throw new IOException("Could not delete file " + combinedTile);
			}
		}

		if (lastLog.get() + TimeUnit.SECONDS.toMillis(5) < System.currentTimeMillis()) {
			log.info(String.format(Locale.US, "%s -> png: overall %d of %d (%.2f%%), zoom %d: %,d of %,d: %s%s",
					tileDir, actual(), expected(), ((double)actual())/expected()*100, tile.getZoom(), tilesNr, tilesOut.size(),
					tile.toCoords(), concatProgress()));

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
				double percent = ((double) actual) / expected * 100;
				progress.append(
						// use more decimal places for small percentage values
						String.format(percent < 3 ? ", %d:%.2f%%" : percent < 10 ? ", %d:%.1f%%" : ", %d:%.0f%%",
								zoom, percent));
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
