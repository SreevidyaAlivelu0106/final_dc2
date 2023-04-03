import java.io.Serializable;

public class Message implements Serializable {

	private static final long serialVersionUID = 1L;
	MessageType type;
	Edge edge;
	int targetID;
	int senderID;
	int senderLeader;
	int phase;

	public Message(MessageType type, Edge edge, int targetID, int senderID, 
			int senderLeaderID, int phaseNumber) {
		this.type = type;
		this.edge = edge;
		this.targetID = targetID;
		this.senderID = senderID;
		this.senderLeader = senderLeaderID;
		this.phase = phaseNumber;
	}

	@Override
	public String toString() {
		return "Type:" + type + ", Sender:" + senderID + " --> Target: " + targetID + ", Source Leader:" +  senderLeader + " ,Phase: " + phase + " -- Edge info: " + edge;
	}



}