import java.net.ServerSocket;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/*
 * A class to store node specific data
 */
public class Node {

	int UID;
	int leaderID;
	CopyOnWriteArrayList<Edge> graphEdges;
	CopyOnWriteArrayList<Edge> treeEdges;

	String host;
	int port;
	ServerSocket serverSocket;

	boolean stopClient;
	boolean marked;
	int phase;
	boolean startSearch;
	int parentID;
	int numNodes;
	int numDummy;
	boolean sendRejectMessageEnable;
	boolean terminationDetectFlag;
	CopyOnWriteArrayList<Message> terminateMessages;
	static HashMap<Integer, Node> nodeMap;
	CopyOnWriteArrayList<Message> Messages;
	CopyOnWriteArrayList<Message> mwoeCadidateReplies;

	Node(int id, String hostName, int portNum)
	{
		this.UID = id;
		this.Messages = new CopyOnWriteArrayList<>();
		this.graphEdges = new CopyOnWriteArrayList<>();
		this.treeEdges = new CopyOnWriteArrayList<>();
		this.mwoeCadidateReplies = new CopyOnWriteArrayList<>();
		this.terminateMessages = new CopyOnWriteArrayList<>();
		this.leaderID = this.UID;
		this.phase = 0;
		this.startSearch = true;
		this.parentID = -1;
		this.sendRejectMessageEnable = true;
		this.host = hostName;
		this.port = portNum;
	}

	public synchronized void setParentID(int parentID) {
		this.parentID = parentID;
	}

	public void setleaderID(int leaderID) {
		this.leaderID = leaderID;
	}

	public void setMarked(boolean marked) {
		this.marked = marked;
	}

	public synchronized void setStartSearch(boolean search) {
		this.startSearch = search;
	}

	public synchronized void setStopClientMgr(boolean stopClient) {
		this.stopClient = stopClient;
	}


	public void setUID(int uID) {
		UID = uID;
	}


	public void setLeaderUID(int leaderUID) {
		this.leaderID = leaderUID;
	}


	public void setGraphEdges(CopyOnWriteArrayList<Edge> graphEdges) {
		this.graphEdges = graphEdges;
	}

	public void setTreeEdges(CopyOnWriteArrayList<Edge> treeEdges) {
		this.treeEdges = treeEdges;
	}

	public void setServerSocket(ServerSocket serverSocket) {
		this.serverSocket = serverSocket;
	}

	public static void setConfigMap(HashMap<Integer, Node> nodeMap) {
		Node.nodeMap = nodeMap;
	}

	public synchronized void setMessages(CopyOnWriteArrayList<Message> Messages) {
		this.Messages = Messages;
	}

	public synchronized void setPhaseNumber(int phase) {
		this.phase = phase;
	}

	public synchronized void setMwoeCadidateReplyBuffer(CopyOnWriteArrayList<Message> mwoeCadidateReplies) {
		this.mwoeCadidateReplies = mwoeCadidateReplies;
	}

	public synchronized void setnumNodes(int numNodes) {
		this.numNodes = numNodes;
	}

	public synchronized void setDummyReplies(int numDummy) {
		this.numDummy = numDummy;
	}

	public synchronized boolean isSendRejectMessageEnable() {
		return sendRejectMessageEnable;
	}

	public synchronized void setSendRejectMessageEnable(boolean sendRejectMessageEnable) {
		this.sendRejectMessageEnable = sendRejectMessageEnable;
	}
	
	public synchronized void setTerminateMessages(CopyOnWriteArrayList<Message> terminateMessages) {
		this.terminateMessages = terminateMessages;
	}

}