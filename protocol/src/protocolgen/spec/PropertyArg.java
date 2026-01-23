package protocolgen.spec;

public class PropertyArg extends StreamableUnion{
	public final Object string = String.class;
	public final Object integer = Integer.class;
	public final Object bool = Boolean.class;
	public final Object collection = PropertyArgCollection.class;
	public final Object outputstream = String.class;
	public final Object nodeLocator = NullableNodeLocator.class;
	public final Object any = PropertyArg.class;
}
