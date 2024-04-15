package org.dstadler.ctw.geotools;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;

import org.dstadler.ctw.utils.LatLonRectangle;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.style.Fill;
import org.geotools.api.style.Stroke;
import org.geotools.api.style.Style;
import org.geotools.api.style.StyleFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.renderer.GTRenderer;
import org.geotools.renderer.label.LabelCacheImpl;
import org.geotools.renderer.lite.LabelCache;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.SLD;

/**
 * Helper methods for wrapping functionality of the
 * geotools third party library.
 *
 * This mostly offers rendering of images from GeoJSON
 * feature-collections.
 *
 * Note: This class is currently not verified in multithreading
 * environments and thus may not be thread-safe.
 */
public class GeoTools {
	// opacity and alpha of the color are combined
	public static final Color RED = new Color(255, 0, 0, 80);
	private static final float OPACITY = 0.6f;

	// static factories to not re-create them for every call
	private static final StyleFactory sf = CommonFactoryFinder.getStyleFactory(null);
	private static final FilterFactory ff = CommonFactoryFinder.getFilterFactory(null);

	private static final Style styleFill = createPolygonStyle(false);
	private static final Style styleBorder = createPolygonStyle(true);

	private static final int TILE_PIXEL = 256;

	private static Style createPolygonStyle(boolean borderOnly) {
		// create an invisible stroke
		Stroke stroke = sf.createStroke(ff.literal(RED),
				// width
				ff.literal(borderOnly ? 1.0f : 0.0f),
				// opacity
				ff.literal(borderOnly ? 1.0f : 0.0f));

		// create a red semi-transparent fill
		Fill fill = sf.createFill(ff.literal(GeoTools.RED),
				// opacity
				ff.literal(borderOnly ? 0 : OPACITY));

		return SLD.wrapSymbolizers(sf.createPolygonSymbolizer(stroke, fill, null));
	}

	/**
	 * Read the contents of the given GeoJSON file
	 * and return the resulting FeatureCollection
	 *
	 * @param jsonFile The GeoJSON file
	 * @return A FeatureCollection which holds the parsed GeoJSON elements
	 * @throws IOException if the file cannot be read
	 */
	public static FeatureCollection<?, ?> parseFeatureCollection(File jsonFile) throws IOException {
		FeatureJSON featureJSON = new FeatureJSON();

		final FeatureCollection<?, ?> features;
		try (InputStream input = new BufferedInputStream(new FileInputStream(jsonFile))) {
			features = featureJSON.readFeatureCollection(input);
		} catch (IOException e) {
			throw new IOException("While reading " + jsonFile, e);
		}
		return features;
	}

	/**
	 * Render the given area of the GeoJSON feature-collection which is specified via the lat/lon-rectangle
	 * to a PNG image stored in the given file.
	 *
	 * This allows to render single "tiles" of a larger set of GeoJSON objects
	 *
	 * Note: The image size is fixed to 256x256 so the given area should be a square
	 *
	 * @param features The GeoJSON feature-collection
	 * @param rect The area-rectangle of the GeoJSON which should be rendered
	 * @param outputFile Where to store the resulting PNG file
	 * @throws IOException If writing the image-file fails
	 */
	public static void writeImage(FeatureCollection<?, ?> features, LatLonRectangle rect, File outputFile) throws IOException {
		writeImageInternal(features, rect, outputFile, styleFill);
	}

	public static void writeBorder(FeatureCollection<?, ?> features, LatLonRectangle rect, File outputFile) throws IOException {
		writeImageInternal(features, rect, outputFile, styleBorder);
	}

	private static void writeImageInternal(FeatureCollection<?, ?> features, LatLonRectangle rect, File outputFile, Style style) throws IOException {
		// Then add them to a map with a style:
		MapContent mapContent = new MapContent();
		try {
			mapContent.setTitle("GeoJSON rendering");
			//style.setBackground(DEFAULT);
			Layer layer = new FeatureLayer(features, style);
			mapContent.addLayer(layer);

			/*
minx = 14.27159
maxx = 14.38015
miny = 48.24724
maxy = 48.31913
14.0, 14.5, 48.17, 48.5
			 */
			ReferencedEnvelope bounds = new ReferencedEnvelope(
					rect.lon1, rect.lon2, rect.lat2, rect.lat1, DefaultGeographicCRS.WGS84);
			BufferedImage bufferedImage = new BufferedImage(TILE_PIXEL, TILE_PIXEL, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = bufferedImage.createGraphics();

			mapContent.getViewport().setMatchingAspectRatio(true);

			mapContent.getViewport().setScreenArea(new Rectangle(TILE_PIXEL, TILE_PIXEL));
			mapContent.getViewport().setBounds(bounds);

			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			Rectangle outputArea = new Rectangle(TILE_PIXEL, TILE_PIXEL);

			GTRenderer renderer = new StreamingRenderer();
			LabelCache labelCache = new LabelCacheImpl();
			Map<Object, Object> hints = renderer.getRendererHints();
			if (hints == null) {
				hints = new HashMap<>();
			}
			hints.put(StreamingRenderer.LABEL_CACHE_KEY, labelCache);
			renderer.setRendererHints(hints);
			renderer.setMapContent(mapContent);
			renderer.paint(g2d, outputArea, bounds);

			// Finally create an image and render the map:
			try (OutputStream fileOutputStream = new FileOutputStream(outputFile);
					ImageOutputStream outputImageFile = ImageIO.createImageOutputStream(fileOutputStream)) {
				ImageIO.write(bufferedImage, "png", outputImageFile);
			}
		} finally {
			mapContent.dispose();
		}
	}
}
