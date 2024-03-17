package org.dstadler.ctw.geojson;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.dstadler.ctw.utils.LatLonRectangle;

import com.github.filosganga.geogson.gson.GeometryAdapterFactory;
import com.github.filosganga.geogson.gson.PositionsAdapter;
import com.github.filosganga.geogson.model.Feature;
import com.github.filosganga.geogson.model.FeatureCollection;
import com.github.filosganga.geogson.model.LinearRing;
import com.github.filosganga.geogson.model.Point;
import com.github.filosganga.geogson.model.Polygon;
import com.github.filosganga.geogson.model.positions.Positions;
import com.github.filosganga.geogson.model.positions.SinglePosition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;

/**
 * Utility class to provide some functionality
 * for creating GeoJSON files
 */
public class GeoJSON {
	private static final Gson gson = new GsonBuilder()
			.registerTypeAdapterFactory(new GeometryAdapterFactory())
			// override the adapter for Positions to write only with 5 decimal
			// places to optimize size of geo-json files
			// this should still give precision down to single meters
			.registerTypeAdapter(Positions.class, new PositionsAdapter() {
				@Override
				public void write(JsonWriter out, Positions value) throws IOException {
					if (value == null) {
						out.nullValue();
					} else {
						out.beginArray();
						if (value instanceof SinglePosition) {
							SinglePosition sp = (SinglePosition) value;
							out.value(formatDecimal(sp.lon()));
							out.value(formatDecimal(sp.lat()));
							if (!Double.isNaN(sp.alt())) {
								out.value(formatDecimal(sp.alt()));
							}
						} else {
							for (Positions child : value.children()) {
								write(out, child);
							}
						}
						out.endArray();
					}
				}
			})
			.create();

	protected static double formatDecimal(double d) {
		// use only 5 decimal digits to reduce the size of the geo-json
		return BigDecimal.valueOf(d).setScale(5, RoundingMode.HALF_UP).doubleValue();
	}

	public static Feature createSquare(LatLonRectangle rec, String property) {
		Feature.Builder builder = Feature.builder().withGeometry(Polygon.of(LinearRing.of(
				Point.from(rec.lon1, rec.lat1),
				Point.from(rec.lon2, rec.lat1),
				Point.from(rec.lon2, rec.lat2),
				Point.from(rec.lon1, rec.lat2),
				Point.from(rec.lon1, rec.lat1)
		)));

		if (property != null) {
			builder.withProperty("popupContent", new JsonPrimitive(property));
		}

		return builder.build();
	}

	public static void writeGeoJSON(String jsonOutputFile, String varPrefix, List<Feature> features) throws
			IOException {
		FeatureCollection collection = new FeatureCollection(features);
		try (Writer writer = new BufferedWriter(new FileWriter(jsonOutputFile))) {
			writer.write("var " + varPrefix + "states=[");
			gson.toJson(collection, writer);
			writer.write("];");
		}
	}

	public static InputStream getGeoJSON(List<Feature> features) throws IOException {
		FeatureCollection collection = new FeatureCollection(features);
		try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
			Writer writer = new BufferedWriter(new OutputStreamWriter(stream))) {
			gson.toJson(collection, writer);

			writer.flush();
			stream.flush();

			return new ByteArrayInputStream(stream.toByteArray());
		}
	}
}
