package sqlancer.common.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.Reproducer;
import sqlancer.SQLGlobalState;
import sqlancer.common.ast.newast.Expression;
import sqlancer.common.ast.newast.Join;
import sqlancer.common.ast.newast.Select;
import sqlancer.common.gen.TLPWhereGenerator;
import sqlancer.common.genesisql.QueryPoolEntry;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.schema.AbstractSchema;
import sqlancer.common.schema.AbstractTable;
import sqlancer.common.schema.AbstractTableColumn;
import sqlancer.common.schema.AbstractTables;

public class TLPWhereOracle<Z extends Select<J, E, T, C>, J extends Join<E, T, C>, E extends Expression<C>, S extends AbstractSchema<?, T>, T extends AbstractTable<C, ?, ?>, C extends AbstractTableColumn<?, ?>, G extends SQLGlobalState<?, S>>
        implements TestOracle<G> {

    private final G state;

    private TLPWhereGenerator<Z, J, E, T, C> gen;
    private final ExpectedErrors errors;

    private Reproducer<G> reproducer;
    private String generatedQueryString;

    private class TLPWhereReproducer implements Reproducer<G> {
        final String firstQueryString;
        final String secondQueryString;
        final String thirdQueryString;
        final String originalQueryString;
        final List<String> resultSet;
        final boolean orderBy;

        TLPWhereReproducer(String firstQueryString, String secondQueryString, String thirdQueryString,
                String originalQueryString, List<String> resultSet, boolean orderBy) {
            this.firstQueryString = firstQueryString;
            this.secondQueryString = secondQueryString;
            this.thirdQueryString = thirdQueryString;
            this.originalQueryString = originalQueryString;
            this.resultSet = resultSet;
            this.orderBy = orderBy;
        }

        @Override
        public boolean bugStillTriggers(G globalState) {
            try {
                List<String> combinedString1 = new ArrayList<>();
                List<String> secondResultSet1 = ComparatorHelper.getCombinedResultSet(firstQueryString,
                        secondQueryString, thirdQueryString, combinedString1, !orderBy, globalState, errors);
                ComparatorHelper.assumeResultSetsAreEqual(resultSet, secondResultSet1, originalQueryString,
                        combinedString1, globalState);
            } catch (AssertionError triggeredError) {
                return true;
            } catch (SQLException ignored) {
            }
            return false;
        }
    }

    public TLPWhereOracle(G state, TLPWhereGenerator<Z, J, E, T, C> gen, ExpectedErrors expectedErrors) {
        if (state == null || gen == null || expectedErrors == null) {
            throw new IllegalArgumentException("Null variables used to initialize test oracle.");
        }
        this.state = state;
        this.gen = gen;
        this.errors = expectedErrors;
    }

    @Override
    public void check() throws SQLException {
        reproducer = null;
        S s = state.getSchema();
        AbstractTables<T, C> targetTables = TestOracleUtils.getRandomTableNonEmptyTables(s);
        gen = gen.setTablesAndColumns(targetTables);

        Select<J, E, T, C> select = gen.generateSelect();

        boolean shouldCreateDummy = true;
        select.setFetchColumns(gen.generateFetchColumns(shouldCreateDummy));
        select.setJoinClauses(gen.getRandomJoinClauses());
        select.setFromList(gen.getTableRefs());
        select.setWhereClause(null);

        String originalQueryString = select.asString();
        generatedQueryString = originalQueryString;
        List<String> firstResultSet = ComparatorHelper.getResultSetFirstColumnAsString(originalQueryString, errors,
                state);

        boolean orderBy = Randomly.getBooleanWithSmallProbability();
        if (orderBy) {
            select.setOrderByClauses(gen.generateOrderBys());
        }

        TestOracleUtils.PredicateVariants<E, C> predicates = TestOracleUtils.initializeTernaryPredicateVariants(gen,
                gen.generateBooleanExpression());
        select.setWhereClause(predicates.predicate);
        String firstQueryString = select.asString();
        select.setWhereClause(predicates.negatedPredicate);
        String secondQueryString = select.asString();
        select.setWhereClause(predicates.isNullPredicate);
        String thirdQueryString = select.asString();

        List<String> combinedString = new ArrayList<>();
        List<String> secondResultSet = ComparatorHelper.getCombinedResultSet(firstQueryString, secondQueryString,
                thirdQueryString, combinedString, !orderBy, state, errors);

        ComparatorHelper.assumeResultSetsAreEqual(firstResultSet, secondResultSet, originalQueryString, combinedString,
                state);

        reproducer = new TLPWhereReproducer(firstQueryString, secondQueryString, thirdQueryString, originalQueryString,
                firstResultSet, orderBy);
    }

    @Override
    public Reproducer<G> getLastReproducer() {
        return reproducer;
    }

    @Override
    public String getLastQueryString() {
        return generatedQueryString;
    }
    
    @Override
    public void initialiseQueryPool(G globalState) throws Exception {
        // Generate initial population of queries and add them to the global state
        // This uses the same logic as the check() method to generate random SELECT statements
        int populationSize = globalState.getOptions().getGenesisqlPopulationSize();
        
        for (int i = 0; i < populationSize; i++) {
            String selectStatement = generateSelectStatement();
            
            // Regenerate if this query was already generated in this initialization
            int maxRetries = 10;
            int retryCount = 0;
            while (globalState.getQueryPool().hasQueryBeenGenerated(selectStatement) && retryCount < maxRetries) {
                selectStatement = generateSelectStatement();
                retryCount++;
            }
            
            // Add to both global state pool and global HashMap tracking all generated queries
            if (!globalState.getQueryPool().hasQueryBeenGenerated(selectStatement)) {
                QueryPoolEntry entry = new QueryPoolEntry(selectStatement, 0, 0);
                globalState.getQueryPool().addQueryPoolEntry(entry);
                globalState.getQueryPool().addToAllGeneratedQueries(selectStatement, entry);
            }
        }
    }
    
    /**
     * Generate a random SELECT statement for the query pool.
     * This follows the same logic as the current check() method.
     */
    // TODO: return Select instead of String, when QueryPoolEntry is updated to use Select
    private String generateSelectStatement() throws SQLException {
        S s = state.getSchema();
        AbstractTables<T, C> targetTables = TestOracleUtils.getRandomTableNonEmptyTables(s);
        gen = gen.setTablesAndColumns(targetTables);

        Select<J, E, T, C> select = gen.generateSelect();

        boolean shouldCreateDummy = true;
        select.setFetchColumns(gen.generateFetchColumns(shouldCreateDummy));
        select.setJoinClauses(gen.getRandomJoinClauses());
        select.setFromList(gen.getTableRefs());
        select.setWhereClause(null);

        boolean orderBy = Randomly.getBooleanWithSmallProbability();
        if (orderBy) {
            select.setOrderByClauses(gen.generateOrderBys());
        }

        TestOracleUtils.PredicateVariants<E, C> predicates = TestOracleUtils.initializeTernaryPredicateVariants(gen,
                gen.generateBooleanExpression());
        select.setWhereClause(predicates.predicate);

        return select.asString();
    }

    @Override
    public void evaluateQueryFitness(QueryPoolEntry entry, G globalState) throws Exception {
        // Step 3a: Calculate fitness score using a fitness function
        int fitnessScore = calculateFitnessScore(entry, globalState);
        entry.setFitnessScore(fitnessScore);
        
        // Step 3b: Run oracle validation on the query (TLP oracle logic)
         performOracleValidation(entry, globalState);
         
         // TODO: merge these two steps into one if possible to avoid redundant query execution
    }
    
    private int calculateFitnessScore(QueryPoolEntry entry, G globalState) throws Exception {
    	// TODO
        // Placeholder: return a random fitness score between 0-100
    	// This should check query partitioning, execution time, and uniqueness of query plan eventually
        return globalState.getRandomly().getInteger(0, 100);   
    }
    
    private void performOracleValidation(QueryPoolEntry entry, G globalState) throws SQLException {
    	// TODO
        // This should implement the TLP oracle logic to validate the query
    	// This can only be done when QueryPoolEntry uses Select instead of String for query representation
    }
}
