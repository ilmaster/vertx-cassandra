package com.englishtown.vertx.cassandra.mapping.guice;

import com.datastax.driver.core.Cluster;
import com.englishtown.vertx.cassandra.CassandraConfigurator;
import com.englishtown.vertx.cassandra.CassandraSession;
import com.englishtown.vertx.cassandra.mapping.impl.DefaultCassandraSession;
import com.englishtown.vertx.cassandra.mapping.impl.EnvironmentCassandraConfigurator;
import com.google.inject.AbstractModule;

import javax.inject.Singleton;

/**
 * Guice injection bindings
 */
public class GuiceCassandraBinder extends AbstractModule {
    /**
     * Configures a {@link com.google.inject.Binder} via the exposed methods.
     */
    @Override
    protected void configure() {

        bind(Cluster.Builder.class).to(Cluster.Builder.class);
        bind(CassandraSession.class).to(DefaultCassandraSession.class).in(Singleton.class);
        bind(CassandraConfigurator.class).to(EnvironmentCassandraConfigurator.class).in(Singleton.class);
        bind(EnvironmentCassandraConfigurator.EnvVarDelegate.class).to(EnvironmentCassandraConfigurator.DefaultEnvVarDelegate.class);

    }
}
