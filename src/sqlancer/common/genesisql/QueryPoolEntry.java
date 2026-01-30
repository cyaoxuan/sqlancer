package sqlancer.common.genesisql;

//import sqlancer.common.query.Query;

public class QueryPoolEntry {
	private final String query; // temporary representation, should change it to Select so that we can use the AST to manipulate queries
	private int fitnessScore;
	private final int generation;

	public QueryPoolEntry(String query, int fitnessScore, int generation) {
		this.query = query;
		this.fitnessScore = fitnessScore;
		this.generation = generation;
	}

	public String getQuery() {
		return query;
	}

	public int getFitnessScore() {
		return fitnessScore;
	}

	public void setFitnessScore(int fitnessScore) {
		this.fitnessScore = fitnessScore;
	}

	public int getGeneration() {
		return generation;
	}
	
	@Override
	public String toString() {
		return "Query: " + query + ", Fitness Score: " + fitnessScore + ", Generation: " + generation;
	}
}