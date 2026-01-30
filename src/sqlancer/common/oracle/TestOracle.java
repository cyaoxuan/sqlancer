package sqlancer.common.oracle;

import sqlancer.GlobalState;
import sqlancer.Reproducer;
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
    default void initialiseQueryPool(G globalState) throws Exception {
        throw new UnsupportedOperationException("initialiseQueryPool not implemented for this oracle");
    }
    
    default void evaluateQueryFitness(QueryPoolEntry entry, G globalState) throws Exception {
        throw new UnsupportedOperationException("evaluateQueryFitness not implemented for this oracle");
    }
}