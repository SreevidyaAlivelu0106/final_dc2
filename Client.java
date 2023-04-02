import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/*
 * Thread to handle incoming connections to node socket
 * Till termination, it listens to socket and puts all the messages
 * to thread safe collection
 */
public class Client implements Runnable{

	/*
	 *Object of type of Node on which this clientManager thread runs 
	 */
	Node thisNode;

	public Client(Node t) {
		super();
		this.thisNode = t;
	}

	@Override
	public void run() {

		while(!thisNode.stopClient)
		{
			ObjectInputStream in = null;

			try {
				Socket s = thisNode.serverSocket.accept();
				in = new ObjectInputStream(s.getInputStream());
				Message Message = (Message)in.readObject();
				System.out.println("Received " + Message.type + " message:" + Message);

				if(Message.type==MessageType.DUMMY && Message.senderID!=-1)
				{
					Message message = new Message(MessageType.DUMMY, null, Message.senderID, -1, -1, thisNode.phase);
					sendMessage(message, message.targetID);
				}
				if(Message.senderID==-1)
					thisNode.setDummyReplies((thisNode.numDummy+1));

				if(Message.type!=MessageType.DUMMY && Message.type != MessageType.TERMINATE)
				{
					if(Message.type == MessageType.MWOEREJECT || Message.type == MessageType.MWOECANDIDATE)
						thisNode.mwoeCadidateReplies.add(Message);
					else
						thisNode.Messages.add(Message);	

				}

				if(Message.type == MessageType.TERMINATE)
				{
					thisNode.terminateMessages.add(Message);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}

		}

		System.out.println("Stopping client Manager");
		//runCleanUp();
	}

	public void runCleanUp() {

		System.out.println("Closing server sockets");
		try {
			thisNode.serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sendMessage(Message message, int targetUID)
	{
		Node targetNode  = Node.nodeMap.get(targetUID);
		try {
			Socket socket = new Socket(targetNode.host, targetNode.port);
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			System.out.println("Sending message: " + message);
			out.writeObject(message);
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}