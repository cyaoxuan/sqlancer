package sqlancer.common.oracle;

import sqlancer.GlobalState;
import sqlancer.Reproducer;
import sqlancer.common.genesisql.QueryPool;
import sqlancer.common.genesisql.QueryPoolEntry;

public interface TestOracle<G extends GlobalState<?, ?, ?>> {

    void check() throws Exception;

    default Reproducer<G> getLastReproducer() {
        return null;
    }

    default String getLastQueryString() {
        throw new AssertionError("Not supported!");
    }
    
    // GenesiSQL methods
    default QueryPool initialiseQueryPool(G globalState) throws Exception {
        throw new UnsupportedOperationException("initialiseQueryPool not implemented for this oracle");
    }
    
    default void evaluateQueryFitness(QueryPoolEntry entry, G globalState) throws Exception {
        throw new UnsupportedOperationException("evaluateQueryFitness not implemented for this oracle");
    }
    
    default QueryPoolEntry mutateQuery(QueryPoolEntry entry, G globalState) throws Exception {
		throw new UnsupportedOperationException("mutateQuery not implemented for this oracle");
	}
    
    default QueryPoolEntry crossoverQueries(QueryPoolEntry entry1, QueryPoolEntry entry2, G globalState) throws Exception {
    	throw new UnsupportedOperationException("crossoverQueries not implemented for this oracle");
    }
}