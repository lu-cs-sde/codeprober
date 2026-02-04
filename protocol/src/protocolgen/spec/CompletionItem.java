package protocolgen.spec;

public class CompletionItem extends Streamable {
	public Object label = String.class;
	public Object insertText = String.class;
	// See
	// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#completionItemKind
	public Object kind = Integer.class;
	public Object sortText = opt(String.class);
	public Object detail = opt(String.class);
	// The "context" of this completion item is a span of text that the item is
	// associated with. Not all items have a context, but for example, when
	// completing a list of type names in a text probe, each item represents a
	// concrete node in the document. The context span should then be the span of
	// each node.
	public Object contextStart = opt(Integer.class);
	public Object contextEnd = opt(Integer.class);
	// The span of text that "insertText" is meant to replace
	public Object insertStart = opt(Integer.class);
	public Object insertEnd = opt(Integer.class);
}
