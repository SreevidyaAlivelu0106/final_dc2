import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class main implements Runnable {

	Node thisNode;

	public main(Node node) {
		this.thisNode = node;
	}

	@Override
	public void run() {
		// while client running
		while (!thisNode.stopClient) {
			// if node hasn't sent search message
			if (thisNode.startSearch) {
				// for each of the node's edges, send search message
				for (Edge edge : thisNode.graphEdges) {
					int targetID = (thisNode.UID != edge.firstID ? edge.firstID : edge.secondID);
					if (targetID != thisNode.UID) {
						Message searchMessage = new Message(MessageType.SEARCH, edge, targetID, thisNode.UID,
								thisNode.leaderID, thisNode.phase);

						sendMessage(searchMessage, targetID);
					}
				}
				// set the searched boolean to false
				thisNode.setStartSearch(false);
			}

			// wait for messages
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			int count = 0;
			int needMessages = 0;
			if(thisNode.UID == thisNode.leaderID){
				needMessages = thisNode.graphEdges.size();
			}
			else{
				needMessages = thisNode.graphEdges.size() - 1;
			}

			// Read the messages
			CopyOnWriteArrayList<Message> ReadMessages = thisNode.Messages;
		synchronized (ReadMessages) {
			for (Iterator<Message> iterator = ReadMessages.iterator(); iterator.hasNext();) {
				Message message = iterator.next();

				if (message.phase == thisNode.phase) {
					if(message.type == MessageType.SEARCH){
							processMWOESearch(message);
							ReadMessages.remove(message);
					}
					else if (message.type == MessageType.MERGE){
						processMergeMessage(message);
						ReadMessages.remove(message);
					}
				}
			}
		}
			
		// Read the responses to search messages
			CopyOnWriteArrayList<Message> ReplyMessages = thisNode.searchReplies;
			synchronized (ReplyMessages) {
				for (Iterator<Message> iterator = ReplyMessages.iterator(); iterator.hasNext();) {
					Message message = iterator.next();
					if (message.phase == thisNode.phase)
						count++;
					}
			}

			if (needMessages == count) {
				ArrayList<Edge> tempCandiateList = new ArrayList<>();
				synchronized (ReplyMessages) {
					for (Iterator<Message> iterator = ReplyMessages.iterator(); iterator.hasNext();) {
						Message message = iterator.next();
						if (message.type != MessageType.REJECT)
							tempCandiateList.add(message.edge);
						ReplyMessages.remove(message);
					}
				}
	
				Edge minEdge = getMinEdge(tempCandiateList);
				if(minEdge != null){
					System.out.println("Min Edge: " + minEdge);
				}

				
				if (thisNode.UID != thisNode.leaderID) {
					if (minEdge != null) {
						Message mwoeCandiate = new Message(MessageType.PROSPECT, minEdge, thisNode.parentID,
								thisNode.UID, thisNode.leaderID, thisNode.phase);
	
						sendMessage(mwoeCandiate, mwoeCandiate.targetID);
					} else {
						if (thisNode.sendReject) {
							thisNode.setSendReject(false);
							Message mwoeCandiate = new Message(MessageType.REJECT, minEdge, thisNode.parentID,
									thisNode.UID, thisNode.leaderID, thisNode.phase);
	
							sendMessage(mwoeCandiate, mwoeCandiate.targetID);
						}
	
					}
				} else {

					if (minEdge == null) {
						System.out.println("Terminate");
						Message terminateMessage = new Message(MessageType.TERMINATE, null, -1, thisNode.UID,
								thisNode.leaderID, thisNode.phase);
	
						sendMessageOnTreeEdges(thisNode, terminateMessage, MessageType.TERMINATE);
						thisNode.setStopClient(true);
					} else {
						if (thisNode.treeEdges.size() != 0 && !isNodePartOfEdge(thisNode, minEdge))
							sendMergeMessageOnTreeEdges(minEdge);
						else {
							if (!edgeExistInTreeEdgeList(minEdge, thisNode)) {
								int targetID = thisNode.UID != minEdge.firstID ? minEdge.firstID : minEdge.secondID;
	
								// add MST edge
								thisNode.treeEdges.add(minEdge);
	
								Message Message = new Message(MessageType.MERGE, minEdge, targetID, thisNode.UID,
										thisNode.leaderID, thisNode.phase);
								sendMessage(Message, targetID);
							} 
							else {
								int newLeaderUID = Math.max(minEdge.firstID, minEdge.secondID);
								Message newLeaderInfoMessage = new Message(MessageType.PROPOGATENL,
										new Edge(newLeaderUID, newLeaderUID, -1), newLeaderUID,
										thisNode.UID, thisNode.leaderID, thisNode.phase);
								sendMessage(newLeaderInfoMessage, newLeaderUID);
							}
	
						}
					}
				}
			} 
			else {
				System.out.println("Waiting...");
			}

			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// Decide new leader
			CopyOnWriteArrayList<Message> PropogateMessages = thisNode.Messages;
		synchronized (PropogateMessages) {
			for (Iterator<Message> iterator = PropogateMessages.iterator(); iterator.hasNext();) {
				Message message = iterator.next();
				if (message.phase == thisNode.phase && message.type == MessageType.PROPOGATENL) {

					int dummyTarget = thisNode.UID == thisNode.graphEdges.get(0).firstID
							? thisNode.graphEdges.get(0).secondID
							: thisNode.graphEdges.get(0).firstID;

					for (int i = 0; i < (1 * thisNode.numNodes); i++) {
						Message dummyMessage = new Message(MessageType.DUMMY, null, dummyTarget, thisNode.UID, -1,
								thisNode.phase);
						sendMessage(dummyMessage, dummyTarget);
					}

					if (thisNode.numDummy >= (1 * thisNode.numNodes)) {
						int newLeaderUID = message.edge.firstID;
						{PropogateMessages.remove(message);

						Edge leaderEdge = new Edge(newLeaderUID, newLeaderUID, 0);
						Message leaderMessageDataHolder = new Message(MessageType.REPLY, leaderEdge, -1, -1, -1, -1);
						sendMessageOnTreeEdges(thisNode, leaderMessageDataHolder, MessageType.REPLY);
						moveToNextPhase(thisNode, newLeaderUID);
					}
				}
			}
		}
	}

	// Change when you get a new leader
	CopyOnWriteArrayList<Message> NLMessages = thisNode.Messages;
	synchronized (NLMessages) {
		for (Iterator<Message> iterator = NLMessages.iterator(); iterator.hasNext();) {
			Message message = iterator.next();
			if (message.phase == thisNode.phase && message.type == MessageType.REPLY) {
				sendMessageOnTreeEdges(thisNode, message, MessageType.REPLY);
				moveToNextPhase(thisNode, message.edge.firstID);
				printAllTreeEdges();
			}
		}
	}
	
	// When you get termination, terminate and propogate message
	CopyOnWriteArrayList<Message> terminateMessages = thisNode.terminateMessages;

		synchronized (terminateMessages) {

			for (Iterator<Message> itr = terminateMessages.iterator(); itr.hasNext();) {
				Message Message = itr.next();

				if (Message.type == MessageType.TERMINATE && Message.phase == thisNode.phase) {
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					Message terminateMessage = new Message(MessageType.TERMINATE, null, 1, thisNode.UID,
							thisNode.leaderID, thisNode.phase);

					sendMessageOnTreeEdges(thisNode, terminateMessage, MessageType.TERMINATE);
					thisNode.setStopClient(true);
				}
			}
		}
	
	}
		System.out.println("End Algo");
		return;
	}

	public void processMergeMessage(Message message) {

		if (!isNodePartOfEdge(thisNode, message.edge)) {
			/*
			 * if this node is not on the min edge in the merge message,
			 * just relay that message to all my tree edges except sender
			 */
			System.out.println("Well, I am not a part of edge from merge Message, relaying it");
			sendMessageOnTreeEdges(thisNode, message, MessageType.MERGE);
		} else {
			/*
			 * if this node is on the edge from the merge message, need to start
			 * merge process, this will also have logic to detect the core edge
			 * and detect new leader
			 */
			if (!edgeExistInTreeEdgeList(message.edge, thisNode)) {
				/*
				 * Edge does not exist in my tree list
				 * add it and pass on merge message to other end of
				 * the edge, that other end should detect new leader
				 */
				System.out.println("Great, Edge does not exist, adding it to my tree edge list ->" + message.edge);
				thisNode.treeEdges.add(message.edge);
				printAllTreeEdges();
				int targeUID = thisNode.UID != message.edge.firstID ? message.edge.firstID : message.edge.secondID;

				if (message.senderLeader == thisNode.leaderID) {
					Message mergeMessage = new Message(MessageType.MERGE, message.edge, targeUID,
							thisNode.UID, thisNode.leaderID, thisNode.phase);

					sendMessage(mergeMessage, targeUID);
				}
			} else {
				/*
				 * Edge from merge message already exist in my tree list
				 * damn, I found core edge, OK then, find max UID on this edge
				 * and send it you are the new leade message
				 */

				int newLeaderUID = Math.max(message.edge.firstID, message.edge.secondID);
				System.out.println("Whola, New Leader found-->" + newLeaderUID);

				Message newLeaderInfoMessage = new Message(MessageType.PROPOGATENL,
						new Edge(newLeaderUID, newLeaderUID, -1), newLeaderUID,
						thisNode.UID, thisNode.leaderID, thisNode.phase);
				sendMessage(newLeaderInfoMessage, newLeaderUID);

			}

		}
	}


	public void moveToNextPhase(Node thisNode2, int newLeaderUID) {
		/*
		 * set unmarked, phase number+1, new Leader, startMWOESearchFlag=true,
		 * bfsparentid=-1
		 */
		System.out.println("Woh woh, changing my phase, leader and other crap");

		thisNode2.setleaderID(newLeaderUID);
		thisNode2.setPhaseNumber((thisNode2.phase + 1));
		thisNode2.setMarked(false);
		if (thisNode2.UID == newLeaderUID)
			thisNode2.setStartSearch(true);
		thisNode2.setParentID(-1);

		thisNode.setSendReject(true);
		printAllTreeEdges();

		// should we clear the MwoeCadidateReplyBuffer as well ???
	}

	public void sendMessageOnTreeEdges(Node thisNode2, Message message, MessageType merge) {

		/*
		 * send merge message to all tree edges of this node except one which sent the
		 * message.
		 */
		System.out.println("Sending message on all tree edges except sender-" + merge);
		printAllTreeEdges();

		CopyOnWriteArrayList<Edge> treeEdgesList = thisNode2.treeEdges;
		synchronized (treeEdgesList) {
			for (Iterator<Edge> itr = treeEdgesList.iterator(); itr.hasNext();) {
				Edge e = itr.next();
				int targetId = gettargetID(thisNode2, e);

				if (targetId != message.senderID) {
					Message Message = new Message(merge, message.edge, targetId,
							thisNode2.UID, thisNode2.leaderID, thisNode2.phase);

					sendMessage(Message, targetId);
				}
			}
		}
	}

	public int gettargetID(Node thisNode2, Edge e) {

		return (thisNode2.UID != e.firstID ? e.firstID : e.secondID);
	}


	public void sendMergeMessageOnTreeEdges(Edge minEdge) {
		CopyOnWriteArrayList<Edge> nodeTreeEdgeList = thisNode.treeEdges;
		synchronized (nodeTreeEdgeList) {
			for (Iterator<Edge> itr = nodeTreeEdgeList.iterator(); itr.hasNext();) {
				Edge e = (Edge) itr.next();
				int targetID = thisNode.UID != e.firstID ? e.firstID : e.secondID;

				Message mergeMessage = new Message(MessageType.MERGE, minEdge, targetID, thisNode.UID,
						thisNode.leaderID, thisNode.phase);

				sendMessage(mergeMessage, targetID);
			}
		}

	}

	public boolean existEdgeInTreeEdgeList(Edge minEdge) {

		CopyOnWriteArrayList<Edge> nodeTreeEdgeList = thisNode.treeEdges;
		synchronized (nodeTreeEdgeList) {
			for (Iterator<Edge> itr = nodeTreeEdgeList.iterator(); itr.hasNext();) {
				Edge e = (Edge) itr.next();

				if (e.equals(minEdge))
					return true;
			}
		}
		return false;
	}

	public boolean isNodePartOfEdge(Node thisNode2, Edge minEdge) {
		if (thisNode2.UID == minEdge.firstID || thisNode2.UID == minEdge.secondID)
			return true;
		return false;
	}

	private boolean edgeExistInTreeEdgeList(Edge minEdge, Node thisNode2) {
		CopyOnWriteArrayList<Edge> nodeTreeEdgeList = thisNode2.treeEdges;
		synchronized (nodeTreeEdgeList) {
			for (Iterator<Edge> itr = nodeTreeEdgeList.iterator(); itr.hasNext();) {
				Edge e = (Edge) itr.next();
				if (e.equals(minEdge))
					return true;
			}
		}

		return false;
	}

	public Edge getMinEdge(ArrayList<Edge> tempCandiateList) {

		if (tempCandiateList == null || tempCandiateList.size() == 0)
			return null;

		Collections.sort(tempCandiateList);
		return tempCandiateList.get(0);
	}

	public void processMWOESearch(Message message) {

		/*
		 * Message coming from the different component
		 * Send a MWOECandidate message
		 */
		if (message.senderLeader != thisNode.leaderID) {
			Message mwoeCandiateMessage = new Message(MessageType.PROSPECT, message.edge,
					message.senderID, thisNode.UID,
					thisNode.leaderID, thisNode.phase);

			sendMessage(mwoeCandiateMessage, message.senderID);
		} else {
			/*
			 * if MWOE search message came from the same component
			 * if you are not marked, mark , backup sender as temp parent
			 * to send result back, propagate message to all neighbors
			 */
			if (!thisNode.marked) {
				thisNode.setMarked(true);
				thisNode.setParentID(message.senderID);
				for (Edge edge : thisNode.graphEdges) {
					int targetID = (thisNode.UID != edge.firstID ? edge.firstID : edge.secondID);
					if (targetID != message.senderID) {
						Message message2 = new Message(MessageType.SEARCH, edge, targetID, thisNode.UID,
								thisNode.leaderID, thisNode.phase);

						sendMessage(message2, targetID);
					}
				}
			} else {
				/*
				 * if already marked, send MWOE rejection
				 */
				Message mwoeReject = new Message(MessageType.REJECT, message.edge, message.senderID,
						thisNode.UID, thisNode.leaderID, thisNode.phase);

				sendMessage(mwoeReject, mwoeReject.targetID);
			}

		}

	}

	public void sendMessage(Message message, int targetID) {
		Node targetNode = Node.nodeMap.get(targetID);
		try {
			Socket socket = new Socket(targetNode.host, targetNode.port);
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			System.out.println("Sending message: " + message);
			out.writeObject(message);
			socket.close();
		} catch (IOException e) {
			
		}

	}

	public void printAllTreeEdges() {
		System.out.println("MST edges for " + thisNode.UID + ": ");
		CopyOnWriteArrayList<Edge> nodeTreeEdgeList = thisNode.treeEdges;
		synchronized (nodeTreeEdgeList) {
			for (Iterator<Edge> itr = nodeTreeEdgeList.iterator(); itr.hasNext();) {
				Edge e = (Edge) itr.next();
				System.out.println(e);
			}
		}
		System.out.println("Leader: " + thisNode.leaderID);
	}
}