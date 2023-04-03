import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class GHS_helpers {

	public void phaseIncrement(Node thisNode2, int newLeaderUID) {
		thisNode2.setleaderID(newLeaderUID);
		thisNode2.setPhaseNumber((thisNode2.phase + 1));
		thisNode2.setMarked(false);
		if (thisNode2.UID == newLeaderUID)
			thisNode2.setStartSearch(true);
		thisNode2.setParentID(-1);
		main.thisNode.setSendReject(true);
		printMSTEdges();
	}

	public void sendMessageOnTreeEdges(Node thisNode2, Message message, MessageType merge) {
		printMSTEdges();
		CopyOnWriteArrayList<Edge> treeEdgesList = thisNode2.MSTEdges;
		synchronized (treeEdgesList) {
			for (Iterator<Edge> itr = treeEdgesList.iterator(); itr.hasNext();) {
				Edge e = itr.next();
				int targetId = thisNode2.UID != e.firstID ? e.firstID : e.secondID;

				if (targetId != message.senderID) {
					Message Message = new Message(merge, message.edge, targetId,
							thisNode2.UID, thisNode2.leaderID, thisNode2.phase);
					sendMessage(Message, targetId);
				}
			}
		}
	}

	public boolean edgeAlreadyInTree(Edge minEdge, Node thisNode2) {
		CopyOnWriteArrayList<Edge> nodeTreeEdgeList = thisNode2.MSTEdges;
		synchronized (nodeTreeEdgeList) {
			for (Iterator<Edge> itr = nodeTreeEdgeList.iterator(); itr.hasNext();) {
				Edge e = (Edge) itr.next();
				if (e.equals(minEdge))
					return true;
			}
		}
		return false;
	}

	public void sendMessage(Message message, int targetID) {
		Node targetNode = Node.nodeMap.get(targetID);
		try {
			Socket socket = new Socket(targetNode.host, targetNode.port);
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			out.writeObject(message);
			socket.close();
		} catch (IOException e) {	
		}
	}

	public void printMSTEdges() {
		System.out.println("MST edges for " + main.thisNode.UID + ": ");
		CopyOnWriteArrayList<Edge> nodeTreeEdgeList = main.thisNode.MSTEdges;
		synchronized (nodeTreeEdgeList) {
			for (Iterator<Edge> itr = nodeTreeEdgeList.iterator(); itr.hasNext();) {
				Edge e = (Edge) itr.next();
				System.out.println(e);
			}
		}
		System.out.println("Leader: " + main.thisNode.leaderID);
	}    
}