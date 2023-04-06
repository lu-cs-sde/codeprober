package protocolgen.spec;

import java.util.Arrays;

public class NodeLocator extends Streamable {
	public final Object result = TALStep.class;
	public final Object steps = Arrays.asList(NodeLocatorStep.class);
}