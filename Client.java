import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class Client implements Runnable {
	Node thisNode;

	public Client(Node node) {
		this.thisNode = node;
	}

	@Override
	public void run() {
		while (!thisNode.stopClient) {
			ObjectInputStream inputStream;

			try {
				Socket s = thisNode.serverSocket.accept();
				inputStream = new ObjectInputStream(s.getInputStream());
				Message Message = (Message) inputStream.readObject();
				System.out.println(" message:" + Message);

				if (Message.type == MessageType.DUMMY && Message.senderID != -1) {
					// propogate the message
					Message message = new Message(MessageType.DUMMY, null, Message.senderID, -1, -1, thisNode.phase);
					// get host and port of target node
					Node targetNode = Node.nodeMap.get(message.targetID);
					try {
						// make new connection
						Socket socket = new Socket(targetNode.host, targetNode.port);
						ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
						out.writeObject(message);
						socket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				// If a dummy message
				else if (Message.senderID == -1)
					thisNode.setDummyReplies((thisNode.numDummy + 1));

				// if a terminate message
				else if (Message.type == MessageType.TERMINATE) {
					thisNode.terminateMessages.add(Message);
				}

				// if a response from MWOE involved edges
				else if (Message.type == MessageType.REJECT || Message.type == MessageType.PROSPECT){
						thisNode.searchReplies.add(Message);
				}
				// otherwise just add to messages
				else{
					thisNode.Messages.add(Message);
				}

			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Stopping client for " + thisNode.UID);
	}
}