package protocolgen;

import java.util.ArrayList;
import java.util.List;

import protocolgen.spec.AsyncRpcUpdate;
import protocolgen.spec.Complete;
import protocolgen.spec.EvaluateProperty;
import protocolgen.spec.GetTestSuite;
import protocolgen.spec.GetWorkerStatus;
import protocolgen.spec.Hover;
import protocolgen.spec.ListNodes;
import protocolgen.spec.ListProperties;
import protocolgen.spec.ListTestSuites;
import protocolgen.spec.ListTree;
import protocolgen.spec.PollWorkerStatus;
import protocolgen.spec.PutTestSuite;
import protocolgen.spec.Refresh;
import protocolgen.spec.Rpc;
import protocolgen.spec.StopJob;
import protocolgen.spec.Streamable;
import protocolgen.spec.SubmitWorkerTask;
import protocolgen.spec.SubscribeToWorkerStatus;
import protocolgen.spec.TopRequest;
import protocolgen.spec.TunneledWsPutRequest;
import protocolgen.spec.UnsubscribeFromWorkerStatus;
import protocolgen.spec.WsPutInit;
import protocolgen.spec.WsPutLongpoll;

public class GenAll {

	public static void main(String[] args) throws Exception {
		final List<Class<? extends Rpc>> rpcs = new ArrayList<>();
		final List<Class<? extends Streamable>> serverToClient = new ArrayList<>();

		// Shared
		rpcs.add(TopRequest.class);

		// Client->Server, general
		rpcs.add(ListNodes.class);
		rpcs.add(ListProperties.class);
		rpcs.add(EvaluateProperty.class);
		rpcs.add(ListTree.class);
		rpcs.add(ListTestSuites.class);
		rpcs.add(GetTestSuite.class);
		rpcs.add(PutTestSuite.class);
		rpcs.add(SubscribeToWorkerStatus.class);
		rpcs.add(UnsubscribeFromWorkerStatus.class);
		rpcs.add(StopJob.class);
		rpcs.add(PollWorkerStatus.class);

		// Client->Server, 'LSP'
		rpcs.add(Hover.class);
		rpcs.add(Complete.class);

		// Client->Server, wsput
		rpcs.add(WsPutInit.class);
		rpcs.add(WsPutLongpoll.class);
		rpcs.add(TunneledWsPutRequest.class);

		// Coordinator -> Worker
		rpcs.add(GetWorkerStatus.class);
		rpcs.add(SubmitWorkerTask.class);

		// Server->Client
		serverToClient.add(Refresh.class);
		serverToClient.add(AsyncRpcUpdate.class);

		GenJava.gen(rpcs, serverToClient);
		GenTs.gen(rpcs, serverToClient);
	}
}
