package org.dstadler.ctw.tiles;

import static org.dstadler.ctw.tiles.CreateTileOverlaysFromTiles.TILES_TILES_DIR;
import static org.dstadler.ctw.tiles.CreateTileOverlaysFromUTMRef.TILES_SQUARES_DIR;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.commons.net.UrlUtils;

import com.google.common.base.Preconditions;

/**
 * Combine the overlay tiles for Cover the World with
 * normal OSM tiles downloaded from a tile-server.
 *
 * Resulting PNG files are stored in directories
 * "tilesSquaresCombined" and "tilesTilesCombined".
 *
 * This is trying to run in many threads in parallel
 * to speed up processing the large number of initial
 * tiles.
 *
 * Note: This needs a local tile-server installation
 * because public tile-servers disallow mass-fetching
 * and thus usually quickly throttle downloading tiles!
 */
public class CreateStaticTiles {

	private static final Logger log = LoggerFactory.make();

	public static final File TILE_DIR_COMBINED_SQUARES = new File("tilesSquaresCombined");
	public static final File TILE_DIR_COMBINED_TILES = new File("tilesTilesCombined");

	// Stats: http://localhost:8080/mod_tile/
	// Sample: http://localhost:8080/tile/14/8825/5664.png
	private static final String TILE_SERVER_URL = "http://localhost:8080/tile/";
	//private static final String TILE_SERVER_URL = "https://b.tile.openstreetmap.fr/hot/";
	//private static final String TILE_SERVER_URL = "https://tile.openstreetmap.org/";

	private static final long start = System.currentTimeMillis();

	private static long fileCount = 0;
	private static long existsCount = 0;
	private static final AtomicLong exceptionCount = new AtomicLong();
	private static final AtomicLong filesDone = new AtomicLong();
	private static long lastLog = 0;

	private static final AtomicReference<Throwable> exception = new AtomicReference<>();
	private static final ForkJoinPool commonPool = new ForkJoinPool(8);

	public static void main(String[] args) throws Throwable {
		LoggerFactory.initLogging();

		// make sure the tile-server is available
		for (int i = 0; i < 60; i++) {
			String error = UrlUtils.getAccessError(TILE_SERVER_URL + "1/1/1.png",
					true, false, 10_000);
			if (error == null) {
				break;
			}

			log.warning(i + "/60: Tile-Server seems to be unavailable, retrying after sleeping 10 seconds: " + error);
			Thread.sleep(10_000);
		}

		process(TILES_SQUARES_DIR, TILE_DIR_COMBINED_SQUARES);
		process(TILES_TILES_DIR, TILE_DIR_COMBINED_TILES);
	}

	private static void process(final File tileDir, final File tileDirCombined) throws Throwable {
		if (!tileDirCombined.exists() && !tileDirCombined.mkdirs()) {
			throw new IOException("Could not create directory at " + tileDirCombined);
		}

		fileCount = 0;
		existsCount = 0;
		filesDone.set(0);

		Files.walkFileTree(tileDir.toPath(), new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				fileCount++;

				// stop whenever an exception happened anywhere
				if(exception.get() != null) {
					return FileVisitResult.TERMINATE;
				}

				// compute the OSM tile-coords of this file
				String coords = StringUtils.removeEnd(
						StringUtils.removeStart(
								StringUtils.removeStart(file.toFile().getAbsolutePath(), tileDir.getAbsolutePath())
										.replace("\\", "/"),
								"/"),
						".png");
				Preconditions.checkState(coords.matches("\\d+/\\d+/\\d+"),
						"Had invalid coordinates for file %s: %s",
						file, coords);

				File combinedTile = new File(tileDirCombined,  coords + ".png");
				if (combinedTile.exists()) {
					existsCount++;
					return FileVisitResult.CONTINUE;
				}
				/*if (!combinedTile.getAbsolutePath().endsWith("16/35284/22668.png")) {
					return FileVisitResult.CONTINUE;
				}*/

				commonPool.execute(() -> {
					try {
						writeOSMCombined(coords, ImageIO.read(file.toFile()), tileDirCombined);

						filesDone.incrementAndGet();
					} catch (IOException e) {
						exception.set(new IOException("Failed for " + file + ": " + e, e));
						exceptionCount.incrementAndGet();
					}

					if (lastLog + TimeUnit.SECONDS.toMillis(10) < System.currentTimeMillis()) {
						lastLog = System.currentTimeMillis();

						log("Having", tileDirCombined);
					}
				});

				if (lastLog + TimeUnit.SECONDS.toMillis(10) < System.currentTimeMillis()) {
					lastLog = System.currentTimeMillis();

					log("Scheduling tiles to render, currently having", tileDirCombined);
				}

				return FileVisitResult.CONTINUE;
			}
		});

		log.info("Finished collecting files, now waiting for " +
				commonPool.getQueuedSubmissionCount() + " tasks to finish");

		while (commonPool.getQueuedSubmissionCount() > 0 && exception.get() == null) {
			log("Having", tileDirCombined);

			//noinspection BusyWait
			Thread.sleep(10_000);
		}

		log("After the loop having", tileDirCombined);

		if (exception.get() != null) {
			commonPool.shutdownNow();
			throw exception.get();
		}

		log.info("Waiting for remaining jobs to finish");

		if (!commonPool.awaitQuiescence(10, TimeUnit.MINUTES)) {
			throw new IllegalStateException("Could not wait for all tasks to finish");
		}

		if (exception.get() != null) {
			commonPool.shutdownNow();
			throw exception.get();
		}

		log("After processing having", tileDirCombined);
	}

	private static void log(String x, File dir) {
		double percent = ((double)filesDone.get()) / (fileCount - existsCount) * 100;

		log.info(x + String.format(" %,d waiting tasks after %,d files in %s, "
						+ "%,d existing, %,d done, %,.2f per second, "
						+ "%,.2f%% done, "
						+ (exceptionCount.get() != 0 ?
							exceptionCount.get() + " exceptions: " + exception.get() :
							""),
				commonPool.getQueuedSubmissionCount(), fileCount, dir, existsCount, filesDone.get(),
				((double) filesDone.get()) / ((System.currentTimeMillis() - start) / 1000),
				percent));
	}

	private static void writeOSMCombined(String coords, BufferedImage image, File tileDirCombined) throws IOException {
		BufferedImage osmImage = fetchOSMTile(coords);

		BufferedImage combined = combineImages(osmImage, image);

		final File file = getFile(coords, tileDirCombined);

		//log.info("Writing " + file);
		ImageIO.write(combined, "PNG", file);
	}

	private static BufferedImage combineImages(BufferedImage osmImage, BufferedImage image) {
		// create a combined image, first the full OSM image and then the semi-transparent overlay on top
		BufferedImage combined = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		Graphics g = combined.getGraphics();
		g.drawImage(osmImage, 0, 0, null);
		g.drawImage(image, 0, 0, null);

		g.dispose();
		return combined;
	}

	private static File getFile(String coords, File tileDirCombined) throws IOException {
		final File file = new File(tileDirCombined, coords + ".png");
		if (!file.getParentFile().exists()) {
			// synchronize creating directories globally and use double-check pattern
			// to avoid race-conditions if two threads try to create the same directory
			// at the same time
			synchronized (CreateStaticTiles.class) {
				if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
					throw new IOException("Could not create directory at " + file.getParentFile());
				}
			}
		}
		return file;
	}

	private static BufferedImage fetchOSMTile(String coords) throws IOException {
		int retries = 0;
		while (true) {
			try {
				return fetchOSMTileInternal(coords);
			} catch (IOException e) {
				retries++;
				if (retries > 10) {
					throw e;
				}

				log.info("Sleeping 30 seconds before retry " + retries + " on exception: " + e);
				try {
					//noinspection BusyWait
					Thread.sleep(30_000);
				} catch (InterruptedException ex) {
					throw new RuntimeException(ex);
				}
			}
		}
	}

	private static BufferedImage fetchOSMTileInternal(String coords) throws IOException {
		String url = TILE_SERVER_URL + coords + ".png";
		URL cUrl = new URL(url);

		HttpURLConnection conn = (HttpURLConnection) cUrl.openConnection();

		// set specified timeout if non-zero
		conn.setConnectTimeout(300_000);
		conn.setReadTimeout(300_000);

		try {
			conn.setDoOutput(false);
			conn.setDoInput(true);
			conn.connect();
			int code = conn.getResponseCode();
			if (code != HttpURLConnection.HTTP_OK &&
					code != HttpURLConnection.HTTP_CREATED &&
					code != HttpURLConnection.HTTP_ACCEPTED) {

				throw new IOException("Error " + code + " returned while retrieving response for url '" + url
						+ "' message from client: " + conn.getResponseMessage());
			}

			try (InputStream strm = conn.getInputStream()) {
				return Preconditions.checkNotNull(ImageIO.read(strm),
						"Could not read tile from %s", url);
			}
		} finally {
			conn.disconnect();
		}
	}
}
