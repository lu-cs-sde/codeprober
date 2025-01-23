package codeprober.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import codeprober.protocol.BinaryInputStream;
import codeprober.protocol.BinaryOutputStream;

public class JsonUtil {

	public static interface ToJsonable {
		public JSONObject toJSON();
	}

	public static String requireString(String actual, String... expected) {
		for (String ex : expected) {
			if (actual.equals(ex)) {
				return actual;
			}
		}

		throw new JSONException("Expected one of " + Arrays.toString(expected) + ", got " + actual);
	}

	public static <T> List<T> mapArr(JSONArray arr, BiFunction<JSONArray, Integer, T> mapper) {
		final List<T> ret = new ArrayList<>();
		final int len = arr.length();
		for (int i = 0; i < len; ++i) {
			ret.add(mapper.apply(arr, i));
		}
		return ret;
	}

	public static Object requireNull(Object actual) {
		if (actual == JSONObject.NULL) {
			return actual;
		}
		throw new JSONException("Not null: " + actual);
	}

	@FunctionalInterface
	public static interface DataArrReader<T> {
		T read() throws IOException;
	}

	public static <T> List<T> readDataArr(BinaryInputStream src, DataArrReader<T> reader) throws IOException {
		final int len = src.readInt();
		final List<T> ret = new ArrayList<>();
		for (int i = 0; i < len; ++i) {
			ret.add(reader.read());
		}
		return ret;
	}

	@FunctionalInterface
	public static interface DataArrWriter<T> {
		void write(T val) throws IOException;
	}

	public static <T> void writeDataArr(BinaryOutputStream dst, List<T> arr, DataArrWriter<T> mapper) throws IOException {
		final int len = arr.size();
		dst.writeInt(len);
		for (int i = 0; i < len; ++i) {
			mapper.write(arr.get(i));
		}
	}

	public static <T> int compareList(List<T> arrA, List<T> arrB, Comparator<T> comp) {
		final int lenA = arrA.size();
		final int lenB = arrB.size();
		if (lenA != lenB) {
			return Integer.compare(lenA,  lenB);
		}
		for (int i = 0; i < lenA; ++i) {
			final int cmp = comp.compare(arrA.get(i), arrB.get(i));
			if (cmp != 0) {
				return cmp;
			}
		}
		return 0;
	}
}
