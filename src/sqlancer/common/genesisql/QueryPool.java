package sqlancer.common.genesisql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryPool {
	private List<QueryPoolEntry> queryPoolList;
	private Map<String, QueryPoolEntry> allGeneratedQueries;
	
	public QueryPool() {
		this.queryPoolList = new ArrayList<>();
		this.allGeneratedQueries = new HashMap<>();
	}
	
	public List<QueryPoolEntry> getQueryPoolList() {
		return queryPoolList;
	}
	
	public void addQueryPoolEntry(QueryPoolEntry entry) {
		if (hasQueryBeenGenerated(entry.getQuery().asString())) {
			return;
		}
		
		this.queryPoolList.add(entry);
		this.allGeneratedQueries.put(entry.getQuery().asString(), entry);
	}
	
	public void removeQueryPoolEntry(int index) {
		if (index >= 0 && index < queryPoolList.size()) {
			this.queryPoolList.remove(index);
		}
	}
	
	public QueryPoolEntry getQueryPoolEntry(int index) {
		if (index >= 0 && index < queryPoolList.size()) {
			return this.queryPoolList.get(index);
		}
		return null;
	}
	
	public int size() {
		return queryPoolList.size();
	}
	
	public void printQueryPool() {
		for (QueryPoolEntry entry : queryPoolList) {
			System.out.println(entry);
		}
	}
	
	public void selectTopNQueries(int n) {
		// Sort by descending fitness scores
		this.queryPoolList.sort((a, b) -> Integer.compare(b.getFitnessScore(), a.getFitnessScore()));
		
		// Remove everything after top N
		if (this.queryPoolList.size() > n) {
	        this.queryPoolList.subList(n, this.queryPoolList.size()).clear();
	    }
	}
	
	public boolean hasQueryBeenGenerated(String queryString) {
		return allGeneratedQueries.containsKey(queryString);
	}
	
	public QueryPoolEntry getRandomQueryPoolEntry() {
		if (queryPoolList.isEmpty()) {
			return null;
		}
		int index = (int) (Math.random() * queryPoolList.size());
		return queryPoolList.get(index);
	}
}