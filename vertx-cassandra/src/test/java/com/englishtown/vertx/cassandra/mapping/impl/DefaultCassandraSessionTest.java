package com.englishtown.vertx.cassandra.mapping.impl;

import com.datastax.driver.core.*;
import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.englishtown.vertx.cassandra.CassandraConfigurator;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import io.vertx.core.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DefaultCassandraSession}
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultCassandraSessionTest {

    DefaultCassandraSession cassandraSession;
    List<String> seeds = new ArrayList<>();
    Configuration configuration = new Configuration();

    @Mock
    Vertx vertx;
    @Mock
    Context context;
    @Mock
    CassandraConfigurator configurator;
    @Mock
    Cluster.Builder clusterBuilder;
    @Mock
    Cluster cluster;
    @Mock
    Session session;
    @Mock
    Metadata metadata;
    @Mock
    FutureCallback<ResultSet> callback;
    @Mock
    ListenableFuture<PreparedStatement> preparedStatementFuture;
    @Mock
    FutureCallback<PreparedStatement> preparedStatementCallback;
    @Mock
    CloseFuture closeFuture;
    @Captor
    ArgumentCaptor<Statement> statementCaptor;
    @Captor
    ArgumentCaptor<String> queryCaptor;
    @Captor
    ArgumentCaptor<Runnable> runnableCaptor;
    @Captor
    ArgumentCaptor<Handler<Void>> handlerCaptor;
    @Captor
    ArgumentCaptor<Handler<AsyncResult<Void>>> onReadyCaptor;
    @Captor
    ArgumentCaptor<Executor> executorCaptor;

    public static class TestLoadBalancingPolicy implements LoadBalancingPolicy {
        @Override
        public void init(Cluster cluster, Collection<Host> hosts) {
        }

        @Override
        public HostDistance distance(Host host) {
            return null;
        }

        @Override
        public Iterator<Host> newQueryPlan(String loggedKeyspace, Statement statement) {
            return null;
        }

        @Override
        public void onAdd(Host host) {
        }

        @Override
        public void onUp(Host host) {
        }

        @Override
        public void onSuspected(Host host) {
        }

        @Override
        public void onDown(Host host) {
        }

        @Override
        public void onRemove(Host host) {
        }
    }

    @Before
    public void setUp() {

        when(vertx.getOrCreateContext()).thenReturn(context).thenReturn(null);

        when(clusterBuilder.build()).thenReturn(cluster);
        when(cluster.getConfiguration()).thenReturn(configuration);
        when(cluster.connect()).thenReturn(session);
        when(cluster.getMetadata()).thenReturn(metadata);
        when(cluster.closeAsync()).thenReturn(closeFuture);
        when(closeFuture.force()).thenReturn(closeFuture);

        when(configurator.getSeeds()).thenReturn(seeds);
        seeds.add("127.0.0.1");

        when(session.getCluster()).thenReturn(cluster);
        when(session.prepareAsync(any(RegularStatement.class))).thenReturn(preparedStatementFuture);
        when(session.prepareAsync(anyString())).thenReturn(preparedStatementFuture);

        cassandraSession = new DefaultCassandraSession(clusterBuilder, configurator, vertx);

        verify(configurator).onReady(onReadyCaptor.capture());
        onReadyCaptor.getValue().handle(Future.succeededFuture(null));
    }

    @Test
    public void testInit() throws Exception {

        seeds.clear();
        seeds.add("127.0.0.1");
        seeds.add("127.0.0.2");
        seeds.add("127.0.0.3");

        LoadBalancingPolicy lbPolicy = mock(LoadBalancingPolicy.class);
        when(configurator.getLoadBalancingPolicy()).thenReturn(lbPolicy);
        PoolingOptions poolingOptions = mock(PoolingOptions.class);
        when(configurator.getPoolingOptions()).thenReturn(poolingOptions);
        SocketOptions socketOptions = mock(SocketOptions.class);
        when(configurator.getSocketOptions()).thenReturn(socketOptions);
        QueryOptions queryOptions = mock(QueryOptions.class);
        when(configurator.getQueryOptions()).thenReturn(queryOptions);
        MetricsOptions metricsOptions = mock(MetricsOptions.class);
        when(configurator.getMetricsOptions()).thenReturn(metricsOptions);

        cassandraSession.init(configurator);
        verify(clusterBuilder, times(4)).addContactPoint(anyString());
        verify(clusterBuilder).withLoadBalancingPolicy(eq(lbPolicy));
        verify(clusterBuilder).withPoolingOptions(eq(poolingOptions));
        verify(clusterBuilder, times(2)).build();
        verify(cluster, times(2)).connect();

        verify(cluster, times(0)).getMetadata();
        cassandraSession.getMetadata();
        verify(cluster, times(1)).getMetadata();

        verify(cluster, times(0)).isClosed();
        verify(session, times(0)).isClosed();
        cassandraSession.isClosed();
        verify(cluster, times(0)).isClosed();
        verify(session, times(1)).isClosed();

        assertEquals(cluster, cassandraSession.getCluster());

        seeds.clear();
        try {
            cassandraSession.init(configurator);
            fail();
        } catch (Throwable t) {
            // Expected
        }

    }

    @Test
    public void testExecuteAsync() throws Exception {

        Statement statement = mock(Statement.class);
        ResultSetFuture future = mock(ResultSetFuture.class);
        when(session.executeAsync(any(Statement.class))).thenReturn(future);

        cassandraSession.executeAsync(statement, callback);
        verify(session).executeAsync(eq(statement));
        verify(future).addListener(runnableCaptor.capture(), executorCaptor.capture());

        ResultSet resultSet = mock(ResultSet.class);
        RuntimeException e = new RuntimeException("Unit test exception");
        when(future.get()).thenReturn(resultSet).thenThrow(e);

        executorCaptor.getValue().execute(runnableCaptor.getValue());
        verify(context).runOnContext(handlerCaptor.capture());
        handlerCaptor.getValue().handle(null);
        verify(callback).onSuccess(eq(resultSet));

        executorCaptor.getValue().execute(runnableCaptor.getValue());
        verify(context, times(2)).runOnContext(handlerCaptor.capture());
        handlerCaptor.getValue().handle(null);
        verify(callback).onFailure(eq(e));

    }

    @Test
    public void testExecuteAsync_Query() throws Exception {

        String query = "SELECT * FROM table";
        ResultSetFuture future = mock(ResultSetFuture.class);
        when(session.executeAsync(anyString())).thenReturn(future);

        cassandraSession.executeAsync(query, callback);
        verify(session).executeAsync(queryCaptor.capture());
        assertEquals(query, queryCaptor.getValue());
        verify(future).addListener(any(Runnable.class), any(Executor.class));

    }

    @Test
    public void testExecute() throws Exception {

        String query = "SELECT * FROM table;";

        cassandraSession.execute(query);
        verify(session).execute(queryCaptor.capture());
        assertEquals(query, queryCaptor.getValue());

    }

    @Test
    public void testPrepareAsync_Statement() throws Exception {
        RegularStatement statement = QueryBuilder
                .select()
                .from("ks", "table")
                .where(QueryBuilder.eq("id", QueryBuilder.bindMarker()));

        cassandraSession.prepareAsync(statement, preparedStatementCallback);
        verify(session).prepareAsync(eq(statement));
        verify(preparedStatementFuture).addListener(any(Runnable.class), any(Executor.class));
    }

    @Test
    public void testPrepareAsync_Query() throws Exception {
        String query = "SELECT * FROM ks.table where id = ?";
        cassandraSession.prepareAsync(query, preparedStatementCallback);
        verify(session).prepareAsync(eq(query));
        verify(preparedStatementFuture).addListener(any(Runnable.class), any(Executor.class));
    }

    @Test
    public void testPrepare_Statement() throws Exception {
        RegularStatement statement = QueryBuilder
                .select()
                .from("ks", "table")
                .where(QueryBuilder.eq("id", QueryBuilder.bindMarker()));

        cassandraSession.prepare(statement);
        verify(session).prepare(eq(statement));
    }

    @Test
    public void testPrepare_Query() throws Exception {
        String query = "SELECT * FROM ks.table where id = ?";
        cassandraSession.prepare(query);
        verify(session).prepare(eq(query));
    }

    @Test
    public void testGetMetadata() throws Exception {

        assertEquals(metadata, cassandraSession.getMetadata());

    }

    @Test
    public void testClose() throws Exception {
        cassandraSession.close();
        verify(cluster).closeAsync();
        verify(closeFuture).force();
    }
}
