import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class main implements Runnable {

	public static Node thisNode;

	public main(Node node) {
		main.thisNode = node;
	}

	@Override
	public void run() {
		GHS_helpers helpers = new GHS_helpers();
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

								helpers.sendMessage(searchMessage, targetID);
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
							helpers.processMWOESearch(message);
							ReadMessages.remove(message);
					}
					else if (message.type == MessageType.MERGE){
						helpers.processMergeMessage(message);
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
	
				Edge minEdge = helpers.getMinEdge(tempCandiateList);
				if(minEdge != null){
					System.out.println("Min Edge: " + minEdge);
				}

				
				if (thisNode.UID != thisNode.leaderID) {
					if (minEdge != null) {
						Message mwoeCandiate = new Message(MessageType.PROSPECT, minEdge, thisNode.parentID,
								thisNode.UID, thisNode.leaderID, thisNode.phase);
	
								helpers.sendMessage(mwoeCandiate, mwoeCandiate.targetID);
					} else {
						if (thisNode.sendReject) {
							thisNode.setSendReject(false);
							Message mwoeCandiate = new Message(MessageType.REJECT, minEdge, thisNode.parentID,
									thisNode.UID, thisNode.leaderID, thisNode.phase);
	
							helpers.sendMessage(mwoeCandiate, mwoeCandiate.targetID);
						}
	
					}
				} else {

					if (minEdge == null) {
						System.out.println("Terminate");
						Message terminateMessage = new Message(MessageType.TERMINATE, null, -1, thisNode.UID,
								thisNode.leaderID, thisNode.phase);
	
						helpers.sendMessageOnTreeEdges(thisNode, terminateMessage, MessageType.TERMINATE);
						thisNode.setStopClient(true);
					} else {
						if (thisNode.treeEdges.size() != 0 && !helpers.isNodePartOfEdge(thisNode, minEdge))
						helpers.sendMergeMessageOnTreeEdges(minEdge);
						else {
							if (!helpers.edgeExistInTreeEdgeList(minEdge, thisNode)) {
								int targetID = thisNode.UID != minEdge.firstID ? minEdge.firstID : minEdge.secondID;
	
								// add MST edge
								thisNode.treeEdges.add(minEdge);
	
								Message Message = new Message(MessageType.MERGE, minEdge, targetID, thisNode.UID,
										thisNode.leaderID, thisNode.phase);
								helpers.sendMessage(Message, targetID);
							} 
							else {
								int newLeaderUID = Math.max(minEdge.firstID, minEdge.secondID);
								Message newLeaderInfoMessage = new Message(MessageType.PROPOGATENL,
										new Edge(newLeaderUID, newLeaderUID, -1), newLeaderUID,
										thisNode.UID, thisNode.leaderID, thisNode.phase);
								helpers.sendMessage(newLeaderInfoMessage, newLeaderUID);
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
						helpers.sendMessage(dummyMessage, dummyTarget);
					}

					if (thisNode.numDummy >= (1 * thisNode.numNodes)) {
						int newLeaderUID = message.edge.firstID;
						{PropogateMessages.remove(message);

						Edge leaderEdge = new Edge(newLeaderUID, newLeaderUID, 0);
						Message leaderMessageDataHolder = new Message(MessageType.REPLY, leaderEdge, -1, -1, -1, -1);
						helpers.sendMessageOnTreeEdges(thisNode, leaderMessageDataHolder, MessageType.REPLY);
						helpers.moveToNextPhase(thisNode, newLeaderUID);
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
				helpers.sendMessageOnTreeEdges(thisNode, message, MessageType.REPLY);
				helpers.moveToNextPhase(thisNode, message.edge.firstID);
				helpers.printAllTreeEdges();
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

					helpers.sendMessageOnTreeEdges(thisNode, terminateMessage, MessageType.TERMINATE);
					thisNode.setStopClient(true);
				}
			}
		}
	
	}
		System.out.println("End Algo");
		return;
	}
}