import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class main {

	public static Node thisNode;

	public main(Node node) {
		main.thisNode = node;
	}

	public void GHS_Run() {
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
				TimeUnit.SECONDS.sleep(2);
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
						// Do something based on your connection to the sender of the search
						// If you have different leaders, out of component
						if (message.senderLeader != main.thisNode.leaderID) {
							Message response = new Message(MessageType.PROSPECT, message.edge,
									message.senderID, main.thisNode.UID,
									main.thisNode.leaderID, main.thisNode.phase);
							helpers.sendMessage(response, message.senderID);
						} 
						// Otherwise, it's in same component so either discard or propogate
						else {
							// If you haven't sent a broadcast, do so 
							if (!main.thisNode.marked) {
								// mark yourself
								main.thisNode.setMarked(true);
								// change parent to sender of this message
								main.thisNode.setParentID(message.senderID);
								// propogate to each of your neighbors - parent
								for (Edge edge : main.thisNode.graphEdges) {
									int target = main.thisNode.UID == edge.firstID ? edge.secondID : edge.firstID;
									if (target != thisNode.parentID) {
										Message propMessage = new Message(MessageType.SEARCH, edge, target, main.thisNode.UID,
												main.thisNode.leaderID, main.thisNode.phase);
										helpers.sendMessage(propMessage, target);
									}
								}
							} 
							// if you are already marked, just send the "discard" message
							else {
								Message mwoeReject = new Message(MessageType.REJECT, message.edge, message.senderID,
										main.thisNode.UID, main.thisNode.leaderID, main.thisNode.phase);
								helpers.sendMessage(mwoeReject, mwoeReject.targetID);
							}
				
						}	
							ReadMessages.remove(message);
					}
					else if (message.type == MessageType.MERGE){
						// For merge messages, either approve or propogate the request to target
						// if this node is neither of the nodes incident on min edge, pass it on
						if (main.thisNode.UID != message.edge.firstID && main.thisNode.UID != message.edge.secondID){
							helpers.sendMessageOnTreeEdges(main.thisNode, message, MessageType.MERGE);
						} 
						// If you are an incident edge,
						else {
							// If this edge isn't already in the tree, add it to your MST edges list
							if (!helpers.edgeAlreadyInTree(message.edge, main.thisNode)) {
								main.thisNode.MSTEdges.add(message.edge);
								helpers.printMSTEdges();

								int target = main.thisNode.UID == message.edge.firstID ? message.edge.secondID : message.edge.firstID;
								// If this merge message is from same component, send a merge request to other component's node
								if (message.senderLeader == main.thisNode.leaderID) {
									Message mergeMessage = new Message(MessageType.MERGE, message.edge, target,
											main.thisNode.UID, main.thisNode.leaderID, main.thisNode.phase);
											helpers.sendMessage(mergeMessage, target);
								}
							} else {
								int newLeader = message.edge.firstID > message.edge.secondID ? message.edge.firstID : message.edge.secondID;
								// Send new leader info				
								Message newLeaderInfoMessage = new Message(MessageType.PROPOGATENL,
										new Edge(newLeader, newLeader, -1), newLeader,
										main.thisNode.UID, main.thisNode.leaderID, main.thisNode.phase);
										helpers.sendMessage(newLeaderInfoMessage, newLeader);
							}
						}
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
				ArrayList<Edge> tempResponse = new ArrayList<>();
				synchronized (ReplyMessages) {
					for (Iterator<Message> iterator = ReplyMessages.iterator(); iterator.hasNext();) {
						Message message = iterator.next();
						if (message.type != MessageType.REJECT)
							tempResponse.add(message.edge);
						ReplyMessages.remove(message);
					}
				}
	
				Edge minEdge = null;
				if (tempResponse != null && tempResponse.size() > 0){
					Collections.sort(tempResponse);
					// get smallest edge
					minEdge = tempResponse.get(0);
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
						// if there are any MST edges and this not isn't an incident one, 
						if (thisNode.MSTEdges.size() > 0 && (main.thisNode.UID != minEdge.firstID && main.thisNode.UID != minEdge.secondID)){
							CopyOnWriteArrayList<Edge> nodeTreeEdgeList = main.thisNode.MSTEdges;
							synchronized (nodeTreeEdgeList) {
								for (Iterator<Edge> itr = nodeTreeEdgeList.iterator(); itr.hasNext();) {
									Edge e = (Edge) itr.next();
									int targetID = main.thisNode.UID != e.firstID ? e.firstID : e.secondID;

									Message mergeMessage = new Message(MessageType.MERGE, minEdge, targetID, main.thisNode.UID,
											main.thisNode.leaderID, main.thisNode.phase);

									helpers.sendMessage(mergeMessage, targetID);
								}
							}
						}
						else {
							if (!helpers.edgeAlreadyInTree(minEdge, thisNode)) {
								int targetID = thisNode.UID != minEdge.firstID ? minEdge.firstID : minEdge.secondID;
	
								// add MST edge
								thisNode.MSTEdges.add(minEdge);
	
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
				TimeUnit.SECONDS.sleep(3);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// Decide new leader
			CopyOnWriteArrayList<Message> PropogateMessages = thisNode.Messages;
		synchronized (PropogateMessages) {
			for (Iterator<Message> iterator = PropogateMessages.iterator(); iterator.hasNext();) {
				Message message = iterator.next();
				// If you get a propogate new leader message,
				if (message.phase == thisNode.phase && message.type == MessageType.PROPOGATENL) {
					// get the target to send message to 
					int target = thisNode.UID == thisNode.graphEdges.get(0).firstID
							? thisNode.graphEdges.get(0).secondID
							: thisNode.graphEdges.get(0).firstID;
					// Send n messages
					for (int i = 0; i < thisNode.numNodes; i++) {
						Message dummyMessage = new Message(MessageType.DUMMY, null, target, thisNode.UID, -1,
								thisNode.phase);
						helpers.sendMessage(dummyMessage, target);
					}
					// If you have at least as many or more dummy messages as you have nodes,
					if (thisNode.numDummy >= thisNode.numNodes) {
						int newLeaderUID = message.edge.firstID;
						PropogateMessages.remove(message);

						Edge leaderEdge = new Edge(newLeaderUID, newLeaderUID, 0);
						Message leaderMessage = new Message(MessageType.REPLY, leaderEdge, -1, -1, -1, -1);
						helpers.sendMessageOnTreeEdges(thisNode, leaderMessage, MessageType.REPLY);
						helpers.phaseIncrement(thisNode, newLeaderUID);
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
				helpers.phaseIncrement(thisNode, message.edge.firstID);
				helpers.printMSTEdges();
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
						TimeUnit.SECONDS.sleep(10);
					} catch (InterruptedException e) {
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