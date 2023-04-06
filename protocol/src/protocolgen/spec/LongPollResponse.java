package protocolgen.spec;

import org.json.JSONObject;

public class LongPollResponse extends StreamableUnion {

	public final Object etag = Integer.class;
	public final Object push = JSONObject.class;
}
