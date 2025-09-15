package codeprober.requesthandler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import codeprober.protocol.data.Decoration;
import codeprober.protocol.data.GetDecorationsReq;
import codeprober.protocol.data.GetDecorationsRes;
import codeprober.protocol.data.RpcBodyLine;
import codeprober.rpc.JsonRequestHandler;
import codeprober.textprobe.TextAssertionMatch;
import codeprober.textprobe.TextProbeEnvironment;
import codeprober.textprobe.TextProbeEnvironment.VariableLoadStatus;
import codeprober.textprobe.VarAssignMatch;

public class DecorationsHandler {

	public static GetDecorationsRes apply(GetDecorationsReq req, JsonRequestHandler requestHandler,
			WorkspaceHandler workspaceHandler, LazyParser parser) {
		final TextProbeEnvironment env = new TextProbeEnvironment(requestHandler, workspaceHandler, req.src.src, null,
				false);

		env.printExpectedValuesInComparisonFailures = false;
		env.loadVariables();
		if (env.getVariableStatus() == VariableLoadStatus.LOAD_ERR) {
			return new GetDecorationsRes();
		}
		final List<Decoration> ret = new ArrayList<>();
		for (VarAssignMatch vam : env.parsedFile.assignments) {
			final int line = (vam.lineIdx + 1) << 12;
			final int start = line + vam.columnIdx;
			final int end = start + vam.full.length() + 2;
			ret.add(new Decoration(start, end, "var"));
		}

		for (TextAssertionMatch tam : env.parsedFile.assertions) {
			final int pre = env.errMsgs.size();
			final List<RpcBodyLine> lhsBody = env.evaluateQuery(tam);
			final int pos = env.errMsgs.size();
			final int line = (tam.lineIdx + 1) << 12;
			final int start = line + tam.columnIdx;
			final int end = start + tam.full.length() + 2;
			if (pre != pos) {
				// Error during evaluation (bad node name, attribute name, etc)
				final List<String> filtered = env.errMsgs.subList(pre, pos).stream().map(x -> x.trim())
						.collect(Collectors.toList());
				ret.add(new Decoration(start, end, "error",
						filtered.size() == 1 ? filtered.get(0) : filtered.toString()));
			} else {
				// Maybe ok, perform comparison
				final boolean ok = env.evaluateComparison(tam, lhsBody);
				if (ok) {
					if (tam.expectVal == null) {
						ret.add(new Decoration(start, end, "info",
								"Result: " + TextProbeEnvironment.flattenBody(lhsBody)));
					} else {
						ret.add(new Decoration(start, end, "ok"));
					}
				} else {
					final String msg;
					if (env.errMsgs.size() == pos) {
						msg = "Assertion Failed";
					} else {
						final List<String> filtered = env.errMsgs.subList(pos, env.errMsgs.size()).stream()
								.map(x -> x.trim()).collect(Collectors.toList());
						msg = filtered.size() == 1 ? filtered.get(0) : filtered.toString();
					}
					ret.add(new Decoration(start, end, "error", msg));
				}
			}
		}
		return new GetDecorationsRes(ret);
	}

}
