package sqlancer.common.oracle;

import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.Reproducer;
import sqlancer.SQLGlobalState;
import sqlancer.common.ast.newast.Expression;
import sqlancer.common.ast.newast.Join;
import sqlancer.common.ast.newast.Select;
import sqlancer.common.gen.NoRECGenerator;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.common.schema.AbstractSchema;
import sqlancer.common.schema.AbstractTable;
import sqlancer.common.schema.AbstractTableColumn;
import sqlancer.common.schema.AbstractTables;

public class NoRECOracle<Z extends Select<J, E, T, C>, J extends Join<E, T, C>, E extends Expression<C>, S extends AbstractSchema<?, T>, T extends AbstractTable<C, ?, ?>, C extends AbstractTableColumn<?, ?>, G extends SQLGlobalState<?, S>>
        implements TestOracle<G> {

    private final G state;

    private NoRECGenerator<Z, J, E, T, C> gen;
    private final ExpectedErrors errors;

    private Reproducer<G> reproducer;
    private String lastQueryString;

    // Static counters for query statistics
    public static int zeroCountQueries = 0;
    public static int allRecordsCountQueries = 0;
    public static int globalUniqueQueries = 0;
    public static final java.util.concurrent.ConcurrentHashMap<String, Boolean> queryHistory = new java.util.concurrent.ConcurrentHashMap<>();
    
    // New static tracking for per-database statistics using seed value as identifier
    private static final java.util.concurrent.ConcurrentHashMap<Long, java.util.Set<String>> perDatabaseQueries = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.atomic.AtomicInteger> perDatabaseValidQueries = new java.util.concurrent.ConcurrentHashMap<>();

    private static class NoRECReproducer<G extends SQLGlobalState<?, ?>> implements Reproducer<G> {
        private final Function<G, Integer> optimizedQuery;
        private final Function<G, Integer> unoptimizedQuery;

        NoRECReproducer(Function<G, Integer> optimizedQuery, Function<G, Integer> unoptimizedQuery) {
            this.optimizedQuery = optimizedQuery;
            this.unoptimizedQuery = unoptimizedQuery;
        }

        @Override
        public boolean bugStillTriggers(G globalState) {
            return !Objects.equals(optimizedQuery.apply(globalState), unoptimizedQuery.apply(globalState));
        }
    }

    public NoRECOracle(G state, NoRECGenerator<Z, J, E, T, C> gen, ExpectedErrors expectedErrors) {
        if (state == null || gen == null || expectedErrors == null) {
            throw new IllegalArgumentException("Null variables used to initialize test oracle.");
        }
        this.state = state;
        this.gen = gen;
        this.errors = expectedErrors;
        this.reproducer = null;
    }

    @Override
    public void check() throws SQLException {
        reproducer = null;
        S schema = state.getSchema();
        AbstractTables<T, C> targetTables = TestOracleUtils.getRandomTableNonEmptyTables(schema);
        gen = gen.setTablesAndColumns(targetTables);

        Z select = gen.generateSelect();
        select.setJoinClauses(gen.getRandomJoinClauses());
        select.setFromList(gen.getTableRefs());

        E randomWhereCondition = gen.generateBooleanExpression();

        boolean shouldUseAggregate = Randomly.getBoolean();
        String optimizedQueryString = gen.generateOptimizedQueryString(select, randomWhereCondition,
                shouldUseAggregate);
        lastQueryString = optimizedQueryString;
        if (state.getOptions().logEachSelect()) {
            state.getLogger().writeCurrent(optimizedQueryString);
        }

        String unoptimizedQueryString = gen.generateUnoptimizedQueryString(select, randomWhereCondition);
        if (state.getOptions().logEachSelect()) {
            state.getLogger().writeCurrent(unoptimizedQueryString);
        }

        int optimizedCount = shouldUseAggregate ? extractCounts(optimizedQueryString, errors, state)
                : countRows(optimizedQueryString, errors, state);
        int unoptimizedCount = extractCounts(unoptimizedQueryString, errors, state);

        if (optimizedCount == -1 || unoptimizedCount == -1) {
            throw new IgnoreMeException();
        }

        // Query statistics tracking
        getQueryStatistics(targetTables, state, unoptimizedQueryString, optimizedQueryString);

        if (unoptimizedCount != optimizedCount) {
            Function<G, Integer> optimizedQuery = state -> shouldUseAggregate
                    ? extractCounts(optimizedQueryString, errors, state)
                    : countRows(optimizedQueryString, errors, state);

            Function<G, Integer> unoptimizedQuery = state -> extractCounts(unoptimizedQueryString, errors, state);
            reproducer = new NoRECReproducer<>(optimizedQuery, unoptimizedQuery);

            String queryFormatString = "-- %s;\n-- count: %d";
            String firstQueryStringWithCount = String.format(queryFormatString, optimizedQueryString, optimizedCount);
            String secondQueryStringWithCount = String.format(queryFormatString, unoptimizedQueryString,
                    unoptimizedCount);
            state.getState().getLocalState()
                    .log(String.format("%s\n%s", firstQueryStringWithCount, secondQueryStringWithCount));
            String assertionMessage = String.format("the counts mismatch (%d and %d)!\n%s\n%s", optimizedCount,
                    unoptimizedCount, firstQueryStringWithCount, secondQueryStringWithCount);
            throw new AssertionError(assertionMessage);
        }
    }

    private void getQueryStatistics(AbstractTables<T, C> targetTables, SQLGlobalState<?, ?> state, String unoptimizedQueryString, String optimizedQueryString) {
        // Keep existing tracking
        trackDuplicateQuery(state, unoptimizedQueryString);
        trackFetchAllNone(targetTables, state, optimizedQueryString);
        
        // Add per-database tracking using seed value as identifier
        long seedValue = state.getState().getSeedValue();
        
        // Initialize tracking for new database
        perDatabaseQueries.computeIfAbsent(seedValue, k -> 
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>()));
        perDatabaseValidQueries.computeIfAbsent(seedValue, k -> 
            new java.util.concurrent.atomic.AtomicInteger(0));
        
        // Track unique queries for this database
        perDatabaseQueries.get(seedValue).add(unoptimizedQueryString);
        
        // Increment valid query count
        perDatabaseValidQueries.get(seedValue).incrementAndGet();
    }

    // New method to log database statistics when database is completed/reset
    public static void logDatabaseStatistics(String dbName, long seedValue) {
        if (!perDatabaseQueries.containsKey(seedValue)) {
            return;
        }

        int uniqueQueries = perDatabaseQueries.get(seedValue).size();
        int validQueries = perDatabaseValidQueries.get(seedValue).get();
        double uniqueRate = validQueries > 0 ? (double) uniqueQueries / validQueries : 0.0;

        String logEntry = String.format("[%s] Database: %s (Seed: %d)\n" +
                "Unique queries: %d\n" +
                "Valid queries: %d\n" +
                "Unique rate: %.4f\n\n",
                java.time.LocalDateTime.now(), dbName, seedValue,
                uniqueQueries, validQueries, uniqueRate);

        try {
            java.nio.file.Files.write(
                java.nio.file.Paths.get("./logs/database_query_statistics.log"),
                logEntry.getBytes(),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception e) {
            // Ignore file writing errors
        }

        // Clean up the maps to free memory
        perDatabaseQueries.remove(seedValue);
        perDatabaseValidQueries.remove(seedValue);
    }

    // Method to get current unique rates for all active databases
    public static double[] getCurrentUniqueRates() {
        return perDatabaseQueries.keySet().stream()
            .mapToDouble(seedValue -> {
                int uniqueQueries = perDatabaseQueries.get(seedValue).size();
                int validQueries = perDatabaseValidQueries.get(seedValue).get();
                return validQueries > 0 ? (double) uniqueQueries / validQueries : 0.0;
            })
            .toArray();
    }

    private void trackDuplicateQuery(SQLGlobalState<?, ?> state, String unoptimizedQueryString) {
        String dbStateKey = state.getDatabaseName() + ":" + unoptimizedQueryString;
        if (queryHistory.putIfAbsent(dbStateKey, Boolean.TRUE) == null) {
        	globalUniqueQueries++;
        }
    }

    private void trackFetchAllNone(AbstractTables<T, C> targetTables, SQLGlobalState<?, ?> state, String optimizedQueryString) {
    	// Compare SELECT COUNT(*) FROM <table> WHERE <conditions> were compared to SELECT COUNT(*) FROM <table>.
    	// 1. Get record count from target tables and where clause from optimized query (SELECT COUNT(*) FROM <table> WHERE <clause>)
    	int derivedCount = 0;
    	int fromIndex = optimizedQueryString.toUpperCase().indexOf("FROM");
    	int toIndex = optimizedQueryString.toUpperCase().indexOf("ORDER BY"); // drop ORDER BY clause if exists
    	String derivedQuery = "SELECT COUNT(*) " + optimizedQueryString.substring(fromIndex, toIndex == -1 ? optimizedQueryString.length() : toIndex);
    	
        SQLQueryAdapter q = new SQLQueryAdapter(derivedQuery, errors, false, false);
        try (SQLancerResultSet rs = q.executeAndGet(state)) {
            if (rs != null && rs.next()) {
                derivedCount = rs.getInt(1);
            }
        } catch (Exception e) {
            // If error occurs, leave derivedCount as 0
        }
    	
    	
    	// 2. Get total record count from all target tables (SELECT COUNT(*) FROM <table>)
		int totalRecordCount = 0;
		fromIndex = optimizedQueryString.toUpperCase().indexOf("FROM");
		toIndex = optimizedQueryString.toUpperCase().indexOf("WHERE");
		derivedQuery = "SELECT COUNT(*) " + optimizedQueryString.substring(fromIndex, toIndex == -1 ? optimizedQueryString.length() : toIndex);
		
		q = new SQLQueryAdapter(derivedQuery, errors, false, false);
        try (SQLancerResultSet rs = q.executeAndGet(state)) {
            if (rs != null && rs.next()) {
            	totalRecordCount = rs.getInt(1);
            }
        } catch (Exception e) {
            // If error occurs, leave derivedCount as 0
        }
		
		
		// 3. Compare
		if (totalRecordCount != 0) {
			if (derivedCount == 0) {
				zeroCountQueries++;
			} else if (derivedCount == totalRecordCount) {
				allRecordsCountQueries++;
			} else {
				// Write query to another file in ./logs/aaa.txt using file writer
				
//				String logEntry = String.format("-- %s\n-- Seed: %d\nOutput count: %d, Total record count: %d\n%s\n\n", 
//						java.time.LocalDateTime.now().toString(), 
//						state.getState().getSeedValue(), 
//						derivedCount, totalRecordCount,
//						optimizedQueryString + ";");
//				try {
//					java.nio.file.Files.write(java.nio.file.Paths.get("./logs/duckdb_interesting-queries.sql"), logEntry.getBytes(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
//				} catch (Exception e) {
//					// Ignore file writing errors
//				}
			}
		}
    }

    // Helper method to get total record count from all target tables
    private int getTotalRecordCount(AbstractTables<T, C> targetTables, SQLGlobalState<?, ?> state) {
        int total = 0;
        for (T table : targetTables.getTables()) {
            String tableName = table.getName();
            String countQuery = "SELECT COUNT(*) FROM " + tableName;
            SQLQueryAdapter q = new SQLQueryAdapter(countQuery, new ExpectedErrors(), false, false);
            try (SQLancerResultSet rs = q.executeAndGet(state)) {
                if (rs != null && rs.next()) {
                    total += rs.getInt(1);
                }
            } catch (Exception e) {
                // If error, skip this table
            }
        }
        return total;
    }

    @Override
    public String getLastQueryString() {
        return lastQueryString;
    }

    @Override
    public Reproducer<G> getLastReproducer() {
        return reproducer;
    }

    private int countRows(String queryString, ExpectedErrors errors, SQLGlobalState<?, ?> state) {
        SQLQueryAdapter q = new SQLQueryAdapter(queryString, errors, false, false);

        int count = 0;
        try (SQLancerResultSet rs = q.executeAndGet(state)) {
            if (rs == null) {
                return -1;
            } else {
                try {
                    while (rs.next()) {
                        count++;
                    }
                } catch (SQLException e) {
                    count = -1;
                }
            }
        } catch (Exception e) {
            if (e instanceof IgnoreMeException) {
                throw (IgnoreMeException) e;
            }
            throw new AssertionError(q.getQueryString(), e);
        }
        return count;
    }

    private int extractCounts(String queryString, ExpectedErrors errors, SQLGlobalState<?, ?> state) {
        SQLQueryAdapter q = new SQLQueryAdapter(queryString, errors, false, false);
        int count = 0;
        try (SQLancerResultSet rs = q.executeAndGet(state)) {
            if (rs == null) {
                return -1;
            } else {
                try {
                    while (rs.next()) {
                        count += rs.getInt(1);
                    }
                } catch (SQLException e) {
                    count = -1;
                }
            }
        } catch (Exception e) {
            if (e instanceof IgnoreMeException) {
                throw (IgnoreMeException) e;
            }
            throw new AssertionError(q.getQueryString(), e);
        }
        return count;
    }

}
