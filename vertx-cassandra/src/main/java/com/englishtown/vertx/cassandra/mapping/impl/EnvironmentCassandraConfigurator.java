package com.englishtown.vertx.cassandra.mapping.impl;

import com.datastax.driver.core.PlainTextAuthProvider;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

import javax.inject.Inject;

/**
 *
 */
public class EnvironmentCassandraConfigurator extends JsonCassandraConfigurator {

    // The environment variable that contains the pipe delimited list of seeds
    public static final String ENV_VAR_SEEDS = "CASSANDRA_SEEDS";
    public static final String ENV_VAR_LOCAL_DC = "CASSANDRA_LOCAL_DC";
    public static final String ENV_VAR_USERNAME = "CASSANDRA_USERNAME";
    public static final String ENV_VAR_PASSWORD = "CASSANDRA_PASSWORD";

    public static final Logger logger = LoggerFactory.getLogger(EnvironmentCassandraConfigurator.class);
    private final EnvVarDelegate envVarDelegate;

    @Inject
    public EnvironmentCassandraConfigurator(Vertx vertx, EnvVarDelegate envVarDelegate) {
        super(vertx);
        this.envVarDelegate = envVarDelegate;
        init();
    }

    public EnvironmentCassandraConfigurator(JsonObject config, EnvVarDelegate envVarDelegate) {
        super(config);
        this.envVarDelegate = envVarDelegate;
        init();
    }

    private void init() {
        initSeeds();
        initLoadBalancingPolicy();
        initAuthProvider();
    }

    private void initSeeds() {

        // If default, try env vars
        if (DEFAULT_SEEDS.equals(this.seeds)) {
            String envVarSeeds = envVarDelegate.get(ENV_VAR_SEEDS);

            if (!Strings.isNullOrEmpty(envVarSeeds)) {
                logger.debug("Using environment configuration of " + envVarSeeds);
                String[] seedsArray = envVarSeeds.split("\\|");
                this.seeds = ImmutableList.copyOf(seedsArray);
            }
        }
    }

    private void initLoadBalancingPolicy() {

        // If LB policy not set, try env vars
        if (loadBalancingPolicy == null) {
            String localDC = envVarDelegate.get(ENV_VAR_LOCAL_DC);

            if (!Strings.isNullOrEmpty(localDC)) {
                logger.debug("Using environment config for Local DC of " + localDC);
                loadBalancingPolicy = new DCAwareRoundRobinPolicy(localDC);
            } else {
                logger.debug("No environment configuration found for local DC");
            }
        }
    }

    private void initAuthProvider() {

        // If auth provider not set, try env vars
        if (authProvider == null) {
            String username = envVarDelegate.get(ENV_VAR_USERNAME);
            String password = envVarDelegate.get(ENV_VAR_PASSWORD);

            if (!Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(password)) {
                authProvider = new PlainTextAuthProvider(username, password);
            }
        }

    }

    public interface EnvVarDelegate {
        String get(String name);
    }

    public static class DefaultEnvVarDelegate implements EnvVarDelegate {

        @Override
        public String get(String name) {
            return System.getenv(name);
        }
    }

}
