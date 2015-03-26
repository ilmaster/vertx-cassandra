package com.englishtown.vertx.cassandra.mapping.impl;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.datastax.driver.core.*;
import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.datastax.driver.core.policies.Policies;
import com.datastax.driver.core.policies.ReconnectionPolicy;
import com.datastax.driver.core.policies.RetryPolicy;
import com.englishtown.vertx.cassandra.CassandraConfigurator;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Metrics container
 */
class Metrics implements AutoCloseable {

    private final DefaultCassandraSession session;
    private final MetricRegistry registry = new MetricRegistry();
    private JmxReporter reporter;
    private GaugeStateListener listener;

    Metrics(DefaultCassandraSession session) {
        this.session = session;
    }

    protected void afterReconnect() {

        // Close any existing metrics
        close();

        final Cluster cluster = session.getCluster();
        Configuration configuration = cluster.getConfiguration();

        String name = "config";
        final String config = getConfiguration(session.getConfigurator(), configuration).encodePrettily();
        registry.remove(name);
        registry.register(name, new Gauge<String>() {
            @Override
            public String getValue() {
                return config;
            }
        });

        name = "closed";
        registry.remove(name);
        registry.register(name, new Gauge<Boolean>() {
            @Override
            public Boolean getValue() {
                return session.isClosed();
            }
        });

        listener = new GaugeStateListener();
        cluster.register(listener);

        if (configuration.getMetricsOptions().isJMXReportingEnabled()) {
            String domain = "et.cass." + cluster.getClusterName() + "-metrics";
            reporter = JmxReporter
                    .forRegistry(registry)
                    .inDomain(domain)
                    .build();

            reporter.start();
        }

    }

    private JsonObject getConfiguration(CassandraConfigurator configurator, Configuration configuration) {

        JsonObject json = new JsonObject();

        // Add seeds
        List<String> seeds = configurator.getSeeds();
        JsonArray arr = new JsonArray();
        json.put("seeds", arr);
        if (seeds != null) {
            seeds.forEach(arr::add);
        }

        Policies policies = configuration.getPolicies();
        JsonObject policiesJson = new JsonObject();
        json.put("policies", policiesJson);

        if (policies != null) {
            LoadBalancingPolicy lbPolicy = policies.getLoadBalancingPolicy();
            policiesJson.put("load_balancing", lbPolicy == null ? null : lbPolicy.getClass().getSimpleName());
            ReconnectionPolicy reconnectionPolicy = policies.getReconnectionPolicy();
            policiesJson.put("reconnection", reconnectionPolicy == null ? null : reconnectionPolicy.getClass().getSimpleName());
            RetryPolicy retryPolicy = policies.getRetryPolicy();
            policiesJson.put("retry", retryPolicy == null ? null : retryPolicy.getClass().getSimpleName());
        }

        PoolingOptions poolingOptions = configuration.getPoolingOptions();
        JsonObject pooling = new JsonObject();
        json.put("pooling", pooling);

        if (poolingOptions != null) {
            pooling.put("core_connections_per_host_local", poolingOptions.getCoreConnectionsPerHost(HostDistance.LOCAL));
            pooling.put("core_connections_per_host_remote", poolingOptions.getCoreConnectionsPerHost(HostDistance.REMOTE));
            pooling.put("max_connections_per_host_local", poolingOptions.getMaxConnectionsPerHost(HostDistance.LOCAL));
            pooling.put("max_connections_per_host_remote", poolingOptions.getMaxConnectionsPerHost(HostDistance.REMOTE));

            pooling.put("min_simultaneous_requests_local", poolingOptions.getMinSimultaneousRequestsPerConnectionThreshold(HostDistance.LOCAL));
            pooling.put("min_simultaneous_requests_remote", poolingOptions.getMinSimultaneousRequestsPerConnectionThreshold(HostDistance.REMOTE));
            pooling.put("max_simultaneous_requests_local", poolingOptions.getMaxSimultaneousRequestsPerConnectionThreshold(HostDistance.LOCAL));
            pooling.put("max_simultaneous_requests_remote", poolingOptions.getMaxSimultaneousRequestsPerConnectionThreshold(HostDistance.REMOTE));
        }

        SocketOptions socketOptions = configuration.getSocketOptions();
        JsonObject socket = new JsonObject();
        json.put("socket", socket);

        if (socketOptions != null) {
            socket.put("connect_timeout_millis", socketOptions.getConnectTimeoutMillis());
            socket.put("read_timeout_millis", socketOptions.getReadTimeoutMillis());
            socket.put("receive_buffer_size", socketOptions.getReceiveBufferSize());
            socket.put("send_buffer_size", socketOptions.getSendBufferSize());
            socket.put("so_linger", socketOptions.getSoLinger());
            socket.put("keep_alive", socketOptions.getKeepAlive());
            socket.put("reuse_address", socketOptions.getReuseAddress());
            socket.put("tcp_no_delay", socketOptions.getTcpNoDelay());
        }

        QueryOptions queryOptions = configuration.getQueryOptions();
        JsonObject query = new JsonObject();
        json.put("query", query);

        if (queryOptions != null) {
            ConsistencyLevel consistency = queryOptions.getConsistencyLevel();
            query.put("consistency", consistency == null ? null : consistency.name());
            consistency = queryOptions.getSerialConsistencyLevel();
            query.put("serial_consistency", consistency == null ? null : consistency.name());
            query.put("fetch_size", queryOptions.getFetchSize());
        }

        return json;
    }

    @Override
    public void close() {
        if (listener != null) {
            session.getCluster().unregister(listener);
            listener = null;
        }
        if (reporter != null) {
            reporter.stop();
            reporter = null;
        }
    }

    private class GaugeStateListener implements Host.StateListener {

        private final ConcurrentMap<String, Host> addedHosts = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, Host> upHosts = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, Host> removedHosts = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, Host> downHosts = new ConcurrentHashMap<>();

        public GaugeStateListener() {

            String name;

            name = "added-hosts";
            registry.remove(name);
            registry.register(name, new Gauge<String>() {
                @Override
                public String getValue() {
                    return stringify(addedHosts);
                }
            });

            name = "up-hosts";
            registry.remove(name);
            registry.register(name, new Gauge<String>() {
                @Override
                public String getValue() {
                    return stringify(upHosts);
                }
            });

            name = "down-hosts";
            registry.remove(name);
            registry.register(name, new Gauge<String>() {
                @Override
                public String getValue() {
                    return stringify(downHosts);
                }
            });

            name = "removed-hosts";
            registry.remove(name);
            registry.register(name, new Gauge<String>() {
                @Override
                public String getValue() {
                    return stringify(removedHosts);
                }
            });

        }

        private String stringify(ConcurrentMap<String, Host> hosts) {

            StringBuilder sb = new StringBuilder();
            String delimiter = "";

            for (String key : hosts.keySet()) {
                Host host = hosts.get(key);
                if (host != null) {
                    sb.append(delimiter)
                            .append(host.toString())
                            .append(" (dc=")
                            .append(host.getDatacenter())
                            .append(" up=")
                            .append(host.isUp())
                            .append(")");

                    delimiter = "\n";
                }
            }

            return sb.toString();
        }

        private String getKey(Host host) {
            return host.getAddress().toString();
        }

        /**
         * Called when a new node is added to the cluster.
         * <p>
         * The newly added node should be considered up.
         *
         * @param host the host that has been newly added.
         */
        @Override
        public void onAdd(Host host) {
            String key = getKey(host);
            addedHosts.put(key, host);
            removedHosts.remove(key);
        }

        /**
         * Called when a node is determined to be up.
         *
         * @param host the host that has been detected up.
         */
        @Override
        public void onUp(Host host) {
            String key = getKey(host);
            upHosts.put(key, host);
            downHosts.remove(key);
        }

        @Override
        public void onSuspected(Host host) {
        }

        /**
         * Called when a node is determined to be down.
         *
         * @param host the host that has been detected down.
         */
        @Override
        public void onDown(Host host) {
            String key = getKey(host);
            downHosts.put(key, host);
            upHosts.remove(key);
        }

        /**
         * Called when a node is removed from the cluster.
         *
         * @param host the removed host.
         */
        @Override
        public void onRemove(Host host) {
            String key = getKey(host);
            removedHosts.put(key, host);
            addedHosts.remove(key);
        }
    }
}
