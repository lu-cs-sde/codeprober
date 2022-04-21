package pasta.locator;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONObject;

import pasta.metaprogramming.Reflect;
import pasta.protocol.PositionRecoveryStrategy;

public class NodesAtPosition {

	public static List<JSONObject> get(Object astNode, int pos, PositionRecoveryStrategy recoveryStrategy) {
		List<JSONObject> ret = new ArrayList<>();
		getTo(ret, astNode, pos, recoveryStrategy);
		Collections.reverse(ret); // Narrowest/smallest node first inthe list
		return ret;
	}

	private static void getTo(List<JSONObject> out, Object astNode, int pos,
			PositionRecoveryStrategy recoveryStrategy) {
		final Span nodePos;
		try {
			nodePos = Span.extractPosition(astNode, recoveryStrategy);
		} catch (NoSuchMethodException | InvocationTargetException e1) {
			e1.printStackTrace();
			return;
		}
		if ((nodePos.start <= pos && nodePos.end >= pos)) {
			boolean show = true;
			switch (astNode.getClass().getSimpleName()) {
			case "List":
			case "Opt": {
				show = false; // Default false for these two, they are very rarely useful
			}
			}

			try {
				show = (Boolean) Reflect.throwingInvoke0(astNode, "pastaVisible");
			} catch (NoSuchMethodException | InvocationTargetException e) {
				// Ignore
			}
			if (show) {
				JSONObject obj = new JSONObject();
				obj.put("start", nodePos.start);
				obj.put("end", nodePos.end);
				obj.put("type", astNode.getClass().getSimpleName());
				out.add(obj);
			}
		}
		for (Object child : (Iterable<?>) Reflect.invoke0(astNode, "astChildren")) {
			getTo(out, child, pos, recoveryStrategy);
		}
	}
}
