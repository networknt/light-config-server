package com.networknt.configserver.db;

import com.networknt.server.ShutdownHookProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mongo Shutdown Hook to close the mongoClient from the MongoStartupHook
 *
 * @author Steve Hu
 */
public class MongoShutdownHook implements ShutdownHookProvider {
    private static final Logger logger = LoggerFactory.getLogger(MongoShutdownHook.class);
    @Override
    public void onShutdown() {
        if(logger.isInfoEnabled()) logger.info("MongoShutdownHook.onShutdown is called");
        if(MongoStartupHook.mongoClient != null) {
            MongoStartupHook.mongoClient.close();
        }
    }
}
