package protocolgen.spec;

import codeprober.requesthandler.BlessFileMode;

@SuppressWarnings("unused")
public class BlessFile extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "BlessFile";
			public final Object src = ParsingRequestData.class;
			public final Object mode = BlessFileMode.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			// null on any error
			// non-null on success
			public final Object numUpdatedProbes = opt(Integer.class);

			// - if any error then result=error message
			// - else if numUpdatedProbes=0 then result=null
			// - else if mode=UPDATE_IN_PLACE then result=null
			// - else if mode=DRY_RUN then result=Description of updates that would be taken
			// - else if mode=ECHO_RESULT then result=The new src
			public final Object result = opt(String.class);
		};
	}
}
