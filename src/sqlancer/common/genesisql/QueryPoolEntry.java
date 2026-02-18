package sqlancer.common.genesisql;

import sqlancer.duckdb.ast.DuckDBSelect;

public class QueryPoolEntry {
	private final DuckDBSelect query; // should be generic Select in future
	private int fitnessScore;
	private final int generation;

	public QueryPoolEntry(DuckDBSelect query, int fitnessScore, int generation) {
		this.query = query;
		this.fitnessScore = fitnessScore;
		this.generation = generation;
	}

	public DuckDBSelect getQuery() {
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
		return "Query: " + query.asString() + ", Fitness Score: " + fitnessScore + ", Generation: " + generation;
	}
}