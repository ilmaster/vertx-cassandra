package com.englishtown.vertx.cassandra.mapping.hk2;

import com.datastax.driver.core.Cluster;
import com.englishtown.vertx.cassandra.CassandraConfigurator;
import com.englishtown.vertx.cassandra.CassandraSession;
import com.englishtown.vertx.cassandra.mapping.impl.DefaultCassandraSession;
import com.englishtown.vertx.cassandra.mapping.impl.EnvironmentCassandraConfigurator;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Singleton;

/**
 * HK2 injection binder
 */
public class HK2CassandraBinder extends AbstractBinder {
    /**
     * Implement to provide binding definitions using the exposed binding
     * methods.
     */
    @Override
    protected void configure() {

        bind(Cluster.Builder.class).to(Cluster.Builder.class);
        bind(DefaultCassandraSession.class).to(CassandraSession.class).in(Singleton.class);
        bind(EnvironmentCassandraConfigurator.class).to(CassandraConfigurator.class).in(Singleton.class);
        bind(EnvironmentCassandraConfigurator.DefaultEnvVarDelegate.class).to(EnvironmentCassandraConfigurator.EnvVarDelegate.class);

    }
}
