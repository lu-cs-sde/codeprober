package protocolgen.spec;

import org.json.JSONObject;

public class WorkerTaskDone extends StreamableUnion {

	public final Object normal = JSONObject.class;
	public final Object unexpectedError = arr(String.class);
}
