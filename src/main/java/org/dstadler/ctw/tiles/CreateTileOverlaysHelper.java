package org.dstadler.ctw.tiles;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.dstadler.commons.collections.ConcurrentMappedCounter;
import org.dstadler.commons.logging.jdk.LoggerFactory;
import org.dstadler.ctw.utils.Constants;
import org.dstadler.ctw.utils.LatLonRectangle;
import org.dstadler.ctw.utils.OSMTile;

import com.google.common.base.Preconditions;
import com.pngencoder.PngEncoder;

/**
 * Functionality which is shared by the applications which
 * create overlay pngs for covered tiles and squares.
 */
public class CreateTileOverlaysHelper {
	private static final Logger log = LoggerFactory.make();

	// Create a GradientPaint for this direction and size
	private static final int RGB = new Color(255, 0, 0, 80).getRGB();

	// most arrays will be full, so let's re-use them to save main memory
	protected static final boolean[][] FULL = new boolean[256][256];
	static {
		for (int x = 0; x < 256; x++) {
			for (int y = 0; y < 256; y++) {
				FULL[x][y] = true;
			}
		}
	}

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
			Arrays.stream(Objects.requireNonNull(tileDir.listFiles())).forEach(s -> {
				try {
					FileUtils.deleteDirectory(s);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	public static boolean isFull(boolean[][] pixel) {
		boolean full = true;
		for (int x = 0; x < 256; x++) {
			for (int y = 0; y < 256; y++) {
				if (!pixel[x][y]) {
					full = false;
					break;
				}
			}
			if (!full) {
				break;
			}
		}
		return full;
	}

	public static void writePixel(Map<OSMTile, boolean[][]> tiles, OSMTile tile, LatLonRectangle recTileIn) {
		// ensure the tile and it's pixel-array are in the map
		boolean[][] pixel = tiles.computeIfAbsent(tile, osmTile -> new boolean[256][256]);

		if (pixel == CreateTileOverlaysHelper.FULL) {
			// already full, nothing to do anymore
			return;
		}

		LatLonRectangle recTile = tile.getRectangle();

		// compute how much of the tileIn is located in this tile
		// so that we can fill the boolean-buffer accordingly
		LatLonRectangle recResult = recTileIn.intersect(recTile);
		Preconditions.checkNotNull(recResult,
				"Expected to have an intersection of rectangles %s and %s",
				recTileIn, recTile);

		//log.info("For '" + tile + "', zoom " + zoom + " and xy " + x + "/" + y + ": Had rect " + recResult + " for " + recTile + " and " + recTile);
		CreateTileOverlaysHelper.fillPixel(recResult, pixel, tile);

		// replace a "full" array with a global instance to save main memory
		if (CreateTileOverlaysHelper.isFull(pixel)) {
			tiles.put(tile, CreateTileOverlaysHelper.FULL);
		}
	}

	public static void fillPixel(LatLonRectangle recResult, boolean[][] pixel, OSMTile tile) {
		Pair<Integer, Integer> pixelStart = getAndCheckPixel(recResult.lat1, recResult.lon1, tile);
		Pair<Integer, Integer> pixelEnd = getAndCheckPixel(recResult.lat2, recResult.lon2, tile);

		// add some pixel to avoid strange artefacts caused by changing sizes depending on latitude
		int endX = Math.min(255, pixelEnd.getKey() + expandPixel(tile.getZoom()));
		int endY = pixelEnd.getValue();
		int startX = pixelStart.getKey();
		int startY = pixelStart.getValue();
		Preconditions.checkState(startX <= endX,
				"Having: pixelStart: %s and pixelEnd %s for recResult: %s",
				pixelStart, pixelEnd, recResult);
		Preconditions.checkState(startY <= endY,
				"Having: pixelStart: %s and pixelEnd %s for recResult: %s",
				pixelStart, pixelEnd, recResult);

		for (int xPixel = startX; xPixel <= endX; xPixel++) {
			for (int yPixel = startY; yPixel <= endY; yPixel++) {
				pixel[xPixel][yPixel] = true;
			}
		}
	}

	private static int expandPixel(int zoom) {
		switch (zoom) {
			case 13:
				return 1;
			case 14:
				return 2;
			case 15:
				return 4;
			case 16:
				return 9;
			case 17:
				return 18;
			case 18:
				return 36;
			default:
				return 0;
		}
	}

	protected static void writeTilesToFiles(File dir, Map<OSMTile, boolean[][]> tiles, File tileDir, int zoom) throws IOException {
		int tileCount = tiles.size();
		int tileNr = 1;
		for (Map.Entry<OSMTile, boolean[][]> entry : tiles.entrySet()) {
			boolean written = writePNG(entry.getKey().toFile(tileDir), entry.getValue(), tileNr, tileCount);

			// whenever writing a tile, remove the combined overlay to re-create it in a follow-up step
			if (written) {
				File combinedTile = new File(dir, entry.getKey().toCoords() + ".png");
				if (combinedTile.exists()) {
					if (!combinedTile.delete()) {
						throw new IOException("Could not delete file " + combinedTile);
					}
				}
			}

			tileNr++;
			ACTUAL.inc(zoom);
		}
	}

	public static boolean writePNG(File file, boolean[][] pixel, int tileNr, int tileCount) throws IOException {
		BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);

		for (int x = 0; x < 256; x++) {
			for (int y = 0; y < 256; y++) {
				if (pixel[x][y]) {
					image.setRGB(x, y, RGB);
					//g.fillRect(x, y, x, y);
				}
			}
		}

		// Save the image in PNG format using the javax.imageio API
		if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
			throw new IOException("Could not create directory at " + file.getParentFile());
		}

		if (lastLog.get() + TimeUnit.SECONDS.toMillis(5) < System.currentTimeMillis()) {
			log.info(String.format(Locale.US, "%,d of %,d: %s%s",
					tileNr, tileCount, file, concatProgress()));

			lastLog.set(System.currentTimeMillis());
		}

		// skip if existing Image is equal to not change the "last modified" date
		if (file.exists()) {
			if (imagesEqual(file, image)) {
				return false;
			}
		}

		new PngEncoder()
				.withBufferedImage(image)
				.withCompressionLevel(1)
				.toFile(file);

		return true;
	}

	protected static boolean imagesEqual(File file, BufferedImage image) throws IOException {
		BufferedImage existing = ImageIO.read(file);

		int[] a1 = existing.getData().getPixels(0, 0, 256, 256, (int[])null);
		int[] a2 = image.getData().getPixels(0, 0, 256, 256, (int[])null);

		return Arrays.compare(a1, a2) == 0;
	}

	public static Pair<Integer, Integer> getAndCheckPixel(double lat, double lon, OSMTile tile) {
		Pair<Integer, Integer> pixel = tile.getPixelInTile(lat, lon);
		Preconditions.checkState(pixel.getKey() >= 0 && pixel.getKey() < 256,
				"Had: %s", pixel);
		Preconditions.checkState(pixel.getValue() >= 0 && pixel.getValue() < 256,
				"Had: %s", pixel);
		return pixel;
	}

	protected static String concatProgress() {
		StringBuilder progress = new StringBuilder();
		for (int zoom = Constants.MIN_ZOOM; zoom <= Constants.MAX_ZOOM; zoom++) {
			long actual = ACTUAL.get(zoom);
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

}
