import java.io.Serializable;

public class Edge implements Comparable<Edge>, Serializable{

	private static final long serialVersionUID = -1462118527829731810L;
	int firstID;
	int secondID;
	int weight;

	public Edge(int firstID, int secondID, int weight) {
		this.firstID = Math.min(firstID, secondID);
		this.secondID = Math.max(firstID, secondID);
		this.weight = weight;
	}

	@Override
	public String toString() {
		return ("Edge--> " + this.firstID + "," + this.secondID + "," + this.weight);
	}

	
	@Override
	public boolean equals(Object obj) {
		Edge e = (Edge) obj;
		if(this.weight==e.weight && this.firstID==e.firstID && this.secondID==e.secondID)
			return true;

		return false;
	}

	@Override
	public int compareTo(Edge e) {
		if(this.weight < e.weight)
			return -1;
		if(this.weight > e.weight)
			return 1;
		if(this.weight == e.weight)
		{
			if(this.firstID < e.firstID)
				return -1;
			if(this.firstID > e.firstID)
				return 1;
			if(this.firstID == e.firstID)
			{
				if(this.secondID < e.secondID)
					return -1;
				if(this.secondID > e.secondID)
					return 1;
			}
		}

		return 0;
	}
}