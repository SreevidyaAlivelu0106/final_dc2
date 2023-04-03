import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class GHS_helpers {

    public void processMergeMessage(Message message) {

		if (!isNodePartOfEdge(main.thisNode, message.edge)) {
			/*
			 * if this node is not on the min edge in the merge message,
			 * just relay that message to all my tree edges except sender
			 */
			System.out.println("Well, I am not a part of edge from merge Message, relaying it");
			sendMessageOnTreeEdges(main.thisNode, message, MessageType.MERGE);
		} else {
			/*
			 * if this node is on the edge from the merge message, need to start
			 * merge process, this will also have logic to detect the core edge
			 * and detect new leader
			 */
			if (!edgeExistInTreeEdgeList(message.edge, main.thisNode)) {
				/*
				 * Edge does not exist in my tree list
				 * add it and pass on merge message to other end of
				 * the edge, that other end should detect new leader
				 */
				System.out.println("Great, Edge does not exist, adding it to my tree edge list ->" + message.edge);
				main.thisNode.treeEdges.add(message.edge);
				printAllTreeEdges();
				int targeUID = main.thisNode.UID != message.edge.firstID ? message.edge.firstID : message.edge.secondID;

				if (message.senderLeader == main.thisNode.leaderID) {
					Message mergeMessage = new Message(MessageType.MERGE, message.edge, targeUID,
							main.thisNode.UID, main.thisNode.leaderID, main.thisNode.phase);

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
						main.thisNode.UID, main.thisNode.leaderID, main.thisNode.phase);
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

		main.thisNode.setSendReject(true);
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
		CopyOnWriteArrayList<Edge> nodeTreeEdgeList = main.thisNode.treeEdges;
		synchronized (nodeTreeEdgeList) {
			for (Iterator<Edge> itr = nodeTreeEdgeList.iterator(); itr.hasNext();) {
				Edge e = (Edge) itr.next();
				int targetID = main.thisNode.UID != e.firstID ? e.firstID : e.secondID;

				Message mergeMessage = new Message(MessageType.MERGE, minEdge, targetID, main.thisNode.UID,
						main.thisNode.leaderID, main.thisNode.phase);

				sendMessage(mergeMessage, targetID);
			}
		}

	}

	public boolean existEdgeInTreeEdgeList(Edge minEdge) {

		CopyOnWriteArrayList<Edge> nodeTreeEdgeList = main.thisNode.treeEdges;
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

	public boolean edgeExistInTreeEdgeList(Edge minEdge, Node thisNode2) {
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
		if (message.senderLeader != main.thisNode.leaderID) {
			Message mwoeCandiateMessage = new Message(MessageType.PROSPECT, message.edge,
					message.senderID, main.thisNode.UID,
					main.thisNode.leaderID, main.thisNode.phase);

			sendMessage(mwoeCandiateMessage, message.senderID);
		} else {
			/*
			 * if MWOE search message came from the same component
			 * if you are not marked, mark , backup sender as temp parent
			 * to send result back, propagate message to all neighbors
			 */
			if (!main.thisNode.marked) {
				main.thisNode.setMarked(true);
				main.thisNode.setParentID(message.senderID);
				for (Edge edge : main.thisNode.graphEdges) {
					int targetID = (main.thisNode.UID != edge.firstID ? edge.firstID : edge.secondID);
					if (targetID != message.senderID) {
						Message message2 = new Message(MessageType.SEARCH, edge, targetID, main.thisNode.UID,
								main.thisNode.leaderID, main.thisNode.phase);

						sendMessage(message2, targetID);
					}
				}
			} else {
				/*
				 * if already marked, send MWOE rejection
				 */
				Message mwoeReject = new Message(MessageType.REJECT, message.edge, message.senderID,
						main.thisNode.UID, main.thisNode.leaderID, main.thisNode.phase);

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
		System.out.println("MST edges for " + main.thisNode.UID + ": ");
		CopyOnWriteArrayList<Edge> nodeTreeEdgeList = main.thisNode.treeEdges;
		synchronized (nodeTreeEdgeList) {
			for (Iterator<Edge> itr = nodeTreeEdgeList.iterator(); itr.hasNext();) {
				Edge e = (Edge) itr.next();
				System.out.println(e);
			}
		}
		System.out.println("Leader: " + main.thisNode.leaderID);
	}

    
}