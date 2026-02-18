package protocolgen.spec;

@SuppressWarnings("unused")
public class GetDecorations extends Rpc {

	@Override
	public Streamable getRequestType() {
		return new Streamable() {
			public final Object type = "ide:decorations";
			public final Object src = ParsingRequestData.class;
			public final Object forceAllOK = opt(Boolean.class);
		};
	}

	@Override
	public Streamable getResponseType() {
		return new Streamable() {
			public final Object lines = opt(arr(Decoration.class));
		};
	}
}
