package codeprober.protocol;

import org.json.JSONArray;
import org.json.JSONObject;

public class ProtocolBuilder {

	protected static Entry<String> str(String key) {
		return new StringReader(key);
	}

	protected static Entry<Boolean> bool(String key) {
		return new BooleanReader(key);
	}

	protected static Entry<JSONObject> obj(String key) {
		return new ObjectReader(key);
	}

	protected static Entry<JSONArray> arr(String key) {
		return new ArrayReader(key);
	}

	public static abstract class Entry<T> {
		public final String key;

		public Entry(String key) {
			this.key = key;
		}

		public abstract T get(JSONObject src);

		public abstract T get(JSONObject src, T fallback);

		public abstract void put(JSONObject dst, T value);
	}

	private static class StringReader extends Entry<String> {

		public StringReader(String key) {
			super(key);
		}

		@Override
		public String get(JSONObject src) {
			return src.getString(key);
		}

		@Override
		public String get(JSONObject src, String fallback) {
			return src.optString(key, fallback);
		}

		@Override
		public void put(JSONObject dst, String value) {
			dst.put(key, value != null ? value : JSONObject.NULL);
		}
	}

	private static class BooleanReader extends Entry<Boolean> {

		public BooleanReader(String key) {
			super(key);
		}

		@Override
		public Boolean get(JSONObject src) {
			return src.getBoolean(key);
		}

		@Override
		public Boolean get(JSONObject src, Boolean fallback) {
			return src.optBoolean(key, fallback);
		}

		@Override
		public void put(JSONObject dst, Boolean value) {
			dst.put(key, value != null ? value : JSONObject.NULL);
		}
	}

	private static class ObjectReader extends Entry<JSONObject> {

		public ObjectReader(String key) {
			super(key);
		}

		@Override
		public JSONObject get(JSONObject src) {
			return src.getJSONObject(key);
		}

		@Override
		public JSONObject get(JSONObject src, JSONObject fallback) {
			return src.optJSONObject(key, fallback);
		}

		@Override
		public void put(JSONObject dst, JSONObject value) {
			dst.put(key, value != null ? value : JSONObject.NULL);
		}
	}

	private static class ArrayReader extends Entry<JSONArray> {

		public ArrayReader(String key) {
			super(key);
		}

		@Override
		public JSONArray get(JSONObject src) {
			return src.getJSONArray(key);
		}

		@Override
		public JSONArray get(JSONObject src, JSONArray fallback) {
			final JSONArray arr = src.optJSONArray(key);
			return arr != null ? arr : fallback;
		}

		@Override
		public void put(JSONObject dst, JSONArray value) {
			dst.put(key, value != null ? value : JSONObject.NULL);
		}
	}
}
