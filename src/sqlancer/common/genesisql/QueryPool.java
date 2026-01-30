package sqlancer.common.genesisql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryPool {
	private List<QueryPoolEntry> queryPool;
	private Map<String, QueryPoolEntry> allGeneratedQueries;
	
	public QueryPool() {
		this.queryPool = new ArrayList<>();
		this.allGeneratedQueries = new HashMap<>();
	}
	
	public List<QueryPoolEntry> getQueryPool() {
		return queryPool;
	}
	
	public void addQueryPoolEntry(QueryPoolEntry entry) {
		this.queryPool.add(entry);
	}
	
	public void removeQueryPoolEntry(int index) {
		if (index >= 0 && index < queryPool.size()) {
			this.queryPool.remove(index);
		}
	}
	
	public QueryPoolEntry getQueryPoolEntry(int index) {
		if (index >= 0 && index < queryPool.size()) {
			return this.queryPool.get(index);
		}
		return null;
	}
	
	public int size() {
		return queryPool.size();
	}
	
	public void clear() {
		this.queryPool.clear();
	}
	
	public void printQueryPool() {
		for (QueryPoolEntry entry : queryPool) {
			System.out.println(entry);
		}
	}
	
	public void selectTopNQueries(int n) {
		// Sort by descending fitness scores
		this.queryPool.sort((a, b) -> Integer.compare(b.getFitnessScore(), a.getFitnessScore()));
		
		// Remove everything after top N
		if (this.queryPool.size() > n) {
	        this.queryPool.subList(n, this.queryPool.size()).clear();
	    }
	}
	
	public Map<String, QueryPoolEntry> getAllGeneratedQueries() {
		return allGeneratedQueries;
	}
	
	public boolean addToAllGeneratedQueries(String queryString, QueryPoolEntry entry) {
		if (allGeneratedQueries.containsKey(queryString)) {
			return false;
		}
		allGeneratedQueries.put(queryString, entry);
		return true;
	}
	
	public boolean hasQueryBeenGenerated(String queryString) {
		return allGeneratedQueries.containsKey(queryString);
	}
}