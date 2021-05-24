package com.networknt.configserver.db;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.networknt.config.Config;
import com.networknt.configserver.constants.ConfigServerConstants;
import com.networknt.server.StartupHookProvider;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Created by stevehu on 2017-03-09.
 */
public class MongoStartupHook implements StartupHookProvider {
    private static final Logger logger = LoggerFactory.getLogger(MongoStartupHook.class);
    public static MongoClient mongoClient;
    public static MongoDatabase db;
    private static final String MONGO_DB_URI = "mongoDBUri";
    private static final String MONGO_DB_NAME = "mongoDBName";


    public void onStartup() {
        if(logger.isInfoEnabled()) logger.info("MongoStartupHook.onStartup is called");
        initDataSource();
    }

    static void initDataSource() {
        //init mongodb connection
        Map<String, Object> configServerConfig = Config.getInstance().getJsonMapConfig(ConfigServerConstants.CONFIG_NAME);
        String url = (String) configServerConfig.get(MONGO_DB_URI);
        String dbName = (String) configServerConfig.get(MONGO_DB_NAME);
        CodecProvider pojoCodecProvider = PojoCodecProvider.builder().register("com.networknt.configserver.model").build();
        CodecRegistry pojoCodecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(pojoCodecProvider));
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(url))
                .codecRegistry(pojoCodecRegistry)
                .build();
        mongoClient = MongoClients.create(settings);
        db = mongoClient.getDatabase(dbName);
    }
}
