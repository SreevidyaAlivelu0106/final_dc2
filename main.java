import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/*
 * Thread to handle incoming connections to node socket
 * Till termination, it listens to socket and puts all the messages
 * to thread safe collection
 */
public class main implements Runnable{

	/*
	 *Object of type of Node on which this clientManager thread runs 
	 */
	Node thisNode;

	public main(Node t) {
		super();
		this.thisNode = t;
	}

	@Override
	public void run() {

		while(!thisNode.stopClient){
			if(thisNode.startMWOESearchFlag)
			{
				thisNode.setStartMWOESearchFlag(false);
				sendMWOESearch(thisNode.UID);
			}

			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			processMessages();
			processMWOECandidateMessage();

			//should i sleep for some time before processing new leader message ?
			// i guess yes, to wait for all merge messages to reach me if any
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			processInformNewLeaderMessage();
			processNewLeaderMessage();
			detectAndProcessTermination();

		}

		System.out.println("Stopping GHS Processor");
		//runCleanUp();
	}

	public void detectAndProcessTermination() {

		// do we need to check message buffers for pending messages

		CopyOnWriteArrayList<Message> terminateMessages = thisNode.terminateMessages;

		synchronized (terminateMessages) {

			for(Iterator<Message> itr = terminateMessages.iterator(); itr.hasNext();)
			{
				Message Message = itr.next();

				if(Message.type==MessageType.TERMINATE && Message.phase==thisNode.phase)
				{
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
						Message terminateMessage = new Message(MessageType.TERMINATE, null, 1, thisNode.UID,
								thisNode.leaderID, thisNode.phase);

						sendMessageOnTreeEdges(thisNode, terminateMessage, MessageType.TERMINATE);
						thisNode.setStopClientMgr(true);
					
				}
			}
		}

	}


	public void processNewLeaderMessage() {
		CopyOnWriteArrayList<Message> Messages = thisNode.Messages;
		synchronized (Messages) {
			for(Iterator<Message> iterator = Messages.iterator(); iterator.hasNext();)
			{
				Message message = iterator.next();

				/*
				 * get current phase unprocessed message out of buffer
				 * process it, once processed, remove message from the buffer
				 */
				if(message.phase == thisNode.phase && message.type == MessageType.NEWLEADER)
				{
					System.out.println("Okay, well lets process new leader message now ->" + message);

					/*
					 * update my leader id, phase number, reset few flags
					 * get ready for next phase
					 */
					//Message newLeaderMessage = new Message(MessageType.NEWLEADER, message.edge, targetID, senderUID, senderComponentId, phaseNumber)
					sendMessageOnTreeEdges(thisNode, message, MessageType.NEWLEADER);
					moveToNextPhase(thisNode, message.edge.firstID);
					printAllTreeEdges();

				}
			}
		}

	}

	public void processMessages() {

		CopyOnWriteArrayList<Message> Messages = thisNode.Messages;
		synchronized (Messages) {
			for(Iterator<Message> iterator = Messages.iterator(); iterator.hasNext();)
			{
				Message message = iterator.next();

				/*
				 * get current phase unprocessed message out of buffer
				 * process it, once processed, remove message from the buffer
				 */
				if(message.phase == thisNode.phase)
				{
					switch (message.type) {
					case MWOESEARCH:
						processMWOESearch(message);
						Messages.remove(message);
						break;

					case MERGE:
						processMergeMessage(message);
						Messages.remove(message);
						break;

					default:
						break;
					}
				}
			}

		}
	}

	public void processMergeMessage(Message message) {


		if(!isNodePartOfEdge(thisNode, message.edge)) {
			/*
			 * if this node is not on the min edge in the merge message,
			 * just relay that message to all my tree edges except sender
			 */
			System.out.println("Well, I am not a part of edge from merge Message, relaying it");
			sendMessageOnTreeEdges(thisNode,message,MessageType.MERGE);
		}
		else
		{
			/*
			 * if this node is on the edge from the merge message, need to start
			 * merge process, this will also have logic to detect the core edge
			 * and detect new leader
			 */
			if(!edgeExistInTreeEdgeList(message.edge, thisNode))
			{
				/*
				 * Edge does not exist in my tree list
				 * add it and pass on merge message to other end of 
				 * the edge, that other end should detect new leader
				 */
				System.out.println("Great, Edge does not exist, adding it to my tree edge list ->" + message.edge);
				thisNode.treeEdges.add(message.edge);
				printAllTreeEdges();
				int targeUID = thisNode.UID!=message.edge.firstID? message.edge.firstID:message.edge.secondID;

				if(message.senderLeader== thisNode.leaderID)
				{
					Message mergeMessage = new Message(MessageType.MERGE, message.edge, targeUID, 
							thisNode.UID, thisNode.leaderID, thisNode.phase);

					sendMessage(mergeMessage, targeUID);
				}
			}
			else
			{
				/*
				 * Edge from merge message already exist in my tree list
				 * damn, I found core edge, OK then, find max UID on this edge
				 * and send it you are the new leade message
				 */

				int newLeaderUID = Math.max(message.edge.firstID, message.edge.secondID);
				System.out.println("Whola, New Leader found-->" + newLeaderUID);

				Message newLeaderInfoMessage = new Message(MessageType.NEWLEADERINFO, new Edge(newLeaderUID, newLeaderUID, -1), newLeaderUID,
						thisNode.UID, thisNode.leaderID, thisNode.phase);
				sendMessage(newLeaderInfoMessage, newLeaderUID);

			}

		}
	}

	public void processInformNewLeaderMessage()
	{

		CopyOnWriteArrayList<Message> Messages = thisNode.Messages;
		synchronized (Messages) {
			for(Iterator<Message> iterator = Messages.iterator(); iterator.hasNext();)
			{
				Message message = iterator.next();

				/*
				 * get current phase unprocessed message out of buffer
				 * process it, once processed, remove message from the buffer
				 */
				if(message.phase == thisNode.phase && message.type==MessageType.NEWLEADERINFO)
				{
					/*
					 * 3n rounds dummy message transfer
					 */

					int dummyTarget = thisNode.UID!=thisNode.graphEdges.get(0).firstID?thisNode.graphEdges.get(0).firstID:thisNode.graphEdges.get(0).secondID;

					for(int i=0;i< (1*thisNode.numNodes);i++)
					{
						Message dummyMessage = new Message(MessageType.DUMMY, null, dummyTarget, thisNode.UID, -1, thisNode.phase);
						sendMessage(dummyMessage, dummyTarget);
					}

					if(thisNode.numDummy >= (1*thisNode.numNodes))
					{
						int newLeaderUID = message.edge.firstID;
						Messages.remove(message);

						Edge leaderEdge = new Edge(newLeaderUID, newLeaderUID, 0);
						Message leaderMessageDataHolder = new Message(MessageType.NEWLEADER, leaderEdge, -1, -1, -1, -1);
						sendMessageOnTreeEdges(thisNode, leaderMessageDataHolder, MessageType.NEWLEADER);
						moveToNextPhase(thisNode,newLeaderUID);
					}
					else {
						System.out.println("Waiting for 3N synch to complete-- received till now " + thisNode.numDummy);
					}
				}
			}
		}

	}

	public void moveToNextPhase(Node thisNode2, int newLeaderUID) {
		/*
		 * set unmarked, phase number+1, new Leader, startMWOESearchFlag=true, bfsparentid=-1
		 */
		System.out.println("Woh woh, changing my phase, leader and other crap");

		thisNode2.setleaderID(newLeaderUID);
		thisNode2.setPhaseNumber((thisNode2.phase+1));
		thisNode2.setMarked(false);
		if(thisNode2.UID==newLeaderUID)
			thisNode2.setStartMWOESearchFlag(true);
		thisNode2.setParentID(-1);

		thisNode.setSendRejectMessageEnable(true);
		printAllTreeEdges();

		//should we clear the MwoeCadidateReplyBuffer as well ???
	}

	public void sendMessageOnTreeEdges(Node thisNode2, Message message, MessageType merge) {

		/*
		 * send merge message to all tree edges of this node except one which sent the message.
		 */
		System.out.println("Sending message on all tree edges except sender-" + merge);
		printAllTreeEdges();

		CopyOnWriteArrayList<Edge> treeEdgesList = thisNode2.treeEdges;
		synchronized (treeEdgesList) {
			for(Iterator<Edge> itr = treeEdgesList.iterator();itr.hasNext();)
			{
				Edge e = itr.next();
				int targetId = gettargetID(thisNode2,e);

				if(targetId!=message.senderID)
				{
					Message Message = new Message(merge, message.edge, targetId, 
							thisNode2.UID, thisNode2.leaderID, thisNode2.phase);

					sendMessage(Message, targetId);
				}
			}
		}
	}

	public int gettargetID(Node thisNode2, Edge e) {

		return (thisNode2.UID!=e.firstID?e.firstID:e.secondID);
	}

	public void processMWOECandidateMessage() {
		int count = 0;
		int requiredCount = thisNode.UID==thisNode.leaderID ? thisNode.graphEdges.size(): thisNode.graphEdges.size()-1;
		CopyOnWriteArrayList<Message> Messages = thisNode.mwoeCadidateReplies;
		synchronized (Messages) {
			for(Iterator<Message> iterator = Messages.iterator(); iterator.hasNext();)
			{
				Message message = iterator.next();

				if(message.phase==thisNode.phase)
					count++;
			}
		}

		if(requiredCount == count)
		{
			ArrayList<Edge> tempCandiateList = new ArrayList<>();
			System.out.println("Required number of response for mwoe search message are received: " + requiredCount);
			synchronized (Messages) {
				for(Iterator<Message> iterator = Messages.iterator(); iterator.hasNext();)
				{
					Message message = iterator.next();
					if(message.type != MessageType.MWOEREJECT)
						tempCandiateList.add(message.edge);
					Messages.remove(message);
				}
			}

			Edge minEdge = getMinEdge(tempCandiateList);
			System.out.println("Min Edge found--> " + (minEdge!=null? minEdge:"null"));

			/*
			 * if this node is not leader then either send 
			 * local min edge up to parent or if not found local min
			 * then send mwoe reject to parent
			 */
			if(thisNode.UID != thisNode.leaderID) {
				System.out.println("Sending min edge to updward to the parent.");
				if(minEdge!=null)
				{
					/*
					 * if min edge found, send to temp bfs parent
					 */

					Message mwoeCandiate = new Message(MessageType.MWOECANDIDATE, minEdge, thisNode.parentID, 
							thisNode.UID, thisNode.leaderID, thisNode.phase);

					sendMessage(mwoeCandiate, mwoeCandiate.targetID);
				}
				else
				{
					/*
					 * send mwoe reject message
					 */

					if(thisNode.isSendRejectMessageEnable())
					{
						thisNode.setSendRejectMessageEnable(false);
						Message mwoeCandiate = new Message(MessageType.MWOEREJECT, minEdge, thisNode.parentID, 
								thisNode.UID, thisNode.leaderID, thisNode.phase);

						sendMessage(mwoeCandiate, mwoeCandiate.targetID);
					}

				}
			}
			else
			{
				/*
				 * if ur parent and done with finding global min
				 * if global min not null then initiate merge process
				 * else send termination message
				 */
				if(minEdge==null)
				{
					/*
					 * send termination signal along tree edges
					 *
					 */
					System.out.println("MWOE search failed, termination detected");
					Message terminateMessage = new Message(MessageType.TERMINATE, null, -1, thisNode.UID,
							thisNode.leaderID, thisNode.phase);

					sendMessageOnTreeEdges(thisNode, terminateMessage, MessageType.TERMINATE);
					thisNode.setStopClientMgr(true);
				}
				else
				{
					//System.out.println("Sending merge message on tree edges");
					/*
					 * this node is leader and found global min edge which is not
					 * connected to you,
					 * send merge message to all its tree edges
					 */
					if(thisNode.treeEdges.size()!=0 && !isNodePartOfEdge(thisNode, minEdge))
						sendMergeMessageOnTreeEdges(minEdge);
					else
					{
						if(!edgeExistInTreeEdgeList(minEdge, thisNode))
						{
							int targetID = thisNode.UID!=minEdge.firstID?minEdge.firstID:minEdge.secondID;

							System.out.println("Addint min edge in my edge list--> " + minEdge);
							thisNode.treeEdges.add(minEdge);

							Message Message = new Message(MessageType.MERGE, minEdge, targetID, thisNode.UID, 
									thisNode.leaderID, thisNode.phase);
							sendMessage(Message, targetID);
						}
						else
						{
							System.out.println("Whola, Leader found in MWOE candiate processing");
							int newLeaderUID = Math.max(minEdge.firstID, minEdge.secondID);
							Message newLeaderInfoMessage = new Message(MessageType.NEWLEADERINFO, new Edge(newLeaderUID, newLeaderUID, -1), newLeaderUID,
									thisNode.UID, thisNode.leaderID, thisNode.phase);
							sendMessage(newLeaderInfoMessage, newLeaderUID);
						}

					}
				}
			}

		}
		else
		{
			System.out.println("waiting for all responses to mwoe search messages, requiredCount= " + requiredCount 
					+ ", actual count: " + count + ", Messages size: " + Messages.size());
		}
	}

	public void sendMergeMessageOnTreeEdges(Edge minEdge) {
		CopyOnWriteArrayList<Edge> nodeTreeEdgeList = thisNode.treeEdges;
		synchronized (nodeTreeEdgeList) {
			for(Iterator<Edge> itr= nodeTreeEdgeList.iterator(); itr.hasNext();)
			{
				Edge e = (Edge) itr.next();
				int targetID = thisNode.UID!=e.firstID?e.firstID:e.secondID;

				Message mergeMessage = new Message(MessageType.MERGE, minEdge, targetID, thisNode.UID, 
						thisNode.leaderID, thisNode.phase);

				sendMessage(mergeMessage, targetID);
			}
		}

	}

	public boolean existEdgeInTreeEdgeList(Edge minEdge) {

		CopyOnWriteArrayList<Edge> nodeTreeEdgeList = thisNode.treeEdges;
		synchronized (nodeTreeEdgeList) {
			for(Iterator<Edge> itr= nodeTreeEdgeList.iterator(); itr.hasNext();)
			{
				Edge e = (Edge) itr.next();

				if(e.equals(minEdge))
					return true;
			}
		}
		return false;
	}

	public boolean isNodePartOfEdge(Node thisNode2, Edge minEdge) {
		if(thisNode2.UID==minEdge.firstID || thisNode2.UID==minEdge.secondID)
			return true;
		return false;
	}

	private boolean edgeExistInTreeEdgeList(Edge minEdge, Node thisNode2) {
		CopyOnWriteArrayList<Edge> nodeTreeEdgeList = thisNode2.treeEdges;
		synchronized (nodeTreeEdgeList) {
			for(Iterator<Edge> itr= nodeTreeEdgeList.iterator(); itr.hasNext();)
			{
				Edge e = (Edge) itr.next();
				if(e.equals(minEdge))
					return true;
			}
		}

		return false;
	}

	public Edge getMinEdge(ArrayList<Edge> tempCandiateList) {

		if(tempCandiateList==null || tempCandiateList.size()==0)
			return null;

		Collections.sort(tempCandiateList);
		return tempCandiateList.get(0);
	}

	public void processMWOESearch(Message message) {

		/*
		 * Message coming from the different component
		 * Send a MWOECandidate message
		 */
		if(message.senderLeader!= thisNode.leaderID)
		{
			Message mwoeCandiateMessage = new Message(MessageType.MWOECANDIDATE, message.edge, 
					message.senderID, thisNode.UID, 
					thisNode.leaderID, thisNode.phase);

			sendMessage(mwoeCandiateMessage, message.senderID);
		}
		else
		{
			/*
			 * if MWOE search message came from the same component
			 * if you are not marked, mark , backup sender as temp parent 
			 * to send result back, propagate message to all neighbors
			 */
			if(!thisNode.marked)
			{
				thisNode.setMarked(true);
				thisNode.setParentID(message.senderID);
				sendMWOESearch(message.senderID);
			}
			else
			{
				/*
				 * if already marked, send MWOE rejection
				 */
				Message mwoeReject = new Message(MessageType.MWOEREJECT, message.edge, message.senderID,
						thisNode.UID, thisNode.leaderID,thisNode.phase);

				sendMessage(mwoeReject, mwoeReject.targetID);
			}

		}

	}

	public void sendMWOESearch(int doNotSendUID)
	{
		for(Edge e: thisNode.graphEdges)
		{
			int targetID = (thisNode.UID!=e.firstID? e.firstID: e.secondID);
			if(targetID!=doNotSendUID)
			{
				Message message = new Message(MessageType.MWOESEARCH, e, targetID, thisNode.UID,
						thisNode.leaderID, thisNode.phase);

				sendMessage(message, targetID);
			}	
		}
	}

	public void sendMessage(Message message, int targetID)
	{
		Node targetNode  = Node.nodeMap.get(targetID);
		try {
			Socket socket = new Socket(targetNode.host, targetNode.port
			);
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			System.out.println("Sending message: " + message);
			out.writeObject(message);
			socket.close();
		} catch (IOException e) {
			//e.printStackTrace();
			System.out.println("Error in sending message on socket");
		}

	}

	public void printAllTreeEdges()
	{
		System.out.println("My leader is: " + thisNode.leaderID);
		System.out.println("All my tree edges are");
		CopyOnWriteArrayList<Edge> nodeTreeEdgeList = thisNode.treeEdges;
		synchronized (nodeTreeEdgeList) {
			for(Iterator<Edge> itr= nodeTreeEdgeList.iterator(); itr.hasNext();)
			{
				Edge e = (Edge) itr.next();
				System.out.println(e);
			}
		}
	}
}