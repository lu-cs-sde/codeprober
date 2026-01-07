package protocolgen.spec;

@SuppressWarnings("unused")
public class Hover extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "ide:hover";
			public final Object src = ParsingRequestData.class;
			public final Object line = Integer.class;
			public final Object column = Integer.class;
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object lines = opt(arr(String.class));
			// The "origin context" of this completion is the part of the document that you
			// are performing a hover on
			public Object originContextStart = opt(Integer.class);
			public Object originContextEnd = opt(Integer.class);

			// The "remote context" of this completion is a remote part of the document
			// related to the thing you are hovering.
			public Object remoteContextStart = opt(Integer.class);
			public Object remoteContextEnd = opt(Integer.class);

		};
	}

}
