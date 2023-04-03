import java.io.*;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
	
	public class readConfig {
		public static void main(String[] args) throws IOException {

		int UID = Integer.parseInt(args[0]);

		System.out.println("UID:" + UID);
		String filePath = "/home/012/s/sx/sxa200150/final_dc2/config.txt";
		
		HashMap<Integer, Node> nodeMap = new HashMap<>();
		CopyOnWriteArrayList<Edge> edgeList = new CopyOnWriteArrayList<>();
		int numNodes = 0;
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			String line;
			boolean parsingNodes = false;
			int cnt = 0;
			boolean passedFirstLine = false;

			while ((line = reader.readLine()) != null) {
				line = line.trim();

				if (line.startsWith("#") || line.isEmpty()) {
					continue;
				}

				String[] tokens = line.split("\\s+");

				if (!passedFirstLine && tokens.length == 1) {
					numNodes = Integer.parseInt(tokens[0]);
					cnt = numNodes;
					parsingNodes = true;
					passedFirstLine = true;
				} else if (parsingNodes && tokens.length == 3) {
					int nodeId = Integer.parseInt(tokens[0]);
					String hostName = tokens[1];
					int portNumber = Integer.parseInt(tokens[2]);

					Node currNode = new Node(nodeId, hostName, portNumber);

					nodeMap.put(nodeId, currNode);
					cnt -= 1;
					if (cnt == 0) {
						parsingNodes = false;
					}

				} else if (!parsingNodes && tokens.length >= 1) {
					Pattern pattern = Pattern.compile("\\((\\d+),\\s*(\\d+)\\)\\s*(\\d+)");
					Matcher matcher = pattern.matcher(line);

					if (matcher.matches()) {
						int firstNumber = Integer.parseInt(matcher.group(1));
						int secondNumber = Integer.parseInt(matcher.group(2));
						int thirdNumber = Integer.parseInt(matcher.group(3));

						Edge edge = new Edge(firstNumber, secondNumber, thirdNumber);

						edgeList.add(edge);
					}
				}
			}
		}

			
		CopyOnWriteArrayList<Edge> thisNodeEdges = new CopyOnWriteArrayList<>();
		for(Edge e: edgeList)
		{
			if(e.firstID == UID || e.secondID == UID){
				thisNodeEdges.add(e);
			}
		}

		Node.setNodeMap(nodeMap);
		Node thisNode = Node.nodeMap.get(UID);
		thisNode.setnumNodes(numNodes);
		thisNode.setGraphEdges(thisNodeEdges);

		ServerSocket socket = new ServerSocket(thisNode.port, numNodes);

		thisNode.setServerSocket(socket);

		Client client = new Client(thisNode);
		Thread t = new Thread(client);
		t.start();

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}


		main GHS = new main(thisNode);
		Thread thread = new Thread(GHS);
		thread.start();
	}

}