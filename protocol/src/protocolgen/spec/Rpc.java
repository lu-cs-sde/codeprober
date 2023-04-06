package protocolgen.spec;

public abstract class Rpc {
	public abstract Streamable getRequestType();
	public abstract Streamable getResponseType();
}
