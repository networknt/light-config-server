package com.networknt.configserver.provider;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.networknt.config.Config;
import com.networknt.configserver.constants.ConfigServerConstants;
import com.networknt.configserver.db.MongoStartupHookProvider;
import com.networknt.configserver.model.Service;
import com.networknt.configserver.model.ServiceConfig;
import com.networknt.configserver.model.ServiceConfigs;
import com.networknt.exception.ApiException;
import org.bson.Document;

import java.util.*;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

public class MongoDBProviderImpl implements IProvider {
  private static final String CONFIGS_FILE_NAME = "values";
  private static final String MONGO_COLLECTION_NAME = "mongoDBCollection";
  ConfigKeyResolver resolver = new ConfigKeyResolver();

  @Override
  public String login(String authorization) throws ApiException {
    return null;
  }

  /**
   * Get config properties from config directory for given service details
   *
   * @param authToken will be used for validate service id
   * @param service:  object with service details like projectName, projectVersion, serviceName, serviceVersion etc. to create config directory configs path
   * @return serviceConfigs
   * @throws ApiException when fails to fetch configs from config directory
   */
  @Override
  public ServiceConfigs getServiceConfigs(String authToken, Service service) throws ApiException {
    ServiceConfigs serviceConfigs = new ServiceConfigs();
    serviceConfigs.setConfigProperties(new HashMap<String, Object>());
    serviceConfigs.setService(service);
    String configPath = null;
    Map<String, Object> configsMap = null;

    //Get Global configs
    configPath = buildConfigKey(service, ConfigServerConstants.CONFIGS, ConfigServerConstants.GLOBALS, service.getProjectVersion());
    configsMap = Config.getInstance().getJsonMapConfigNoCache(CONFIGS_FILE_NAME, configPath);
    if (configsMap != null) {
      ((Map<String, Object>) serviceConfigs.getConfigProperties()).putAll(configsMap);
    }
    //Get Service configs
    configPath = buildConfigKey(service, ConfigServerConstants.CONFIGS, service.getServiceName(), service.getServiceVersion());
    configsMap = Config.getInstance().getJsonMapConfigNoCache(CONFIGS_FILE_NAME, configPath);
    if (configsMap != null) {
      ((Map<String, Object>) serviceConfigs.getConfigProperties()).putAll(configsMap);
    }
    return serviceConfigs;
  }

  /**
   * Get certs from mongoDB for given service details
   *
   * @param authToken will be used for validate service id
   * @param service:  object with service details like projectName, projectVersion, serviceName, serviceVersion etc. to create config directory certs path
   * @return service certs
   * @throws ApiException when fails to fetch certs from config directory
   */
  @Override
  public ServiceConfigs getServiceCertificates(String authToken, Service service) throws ApiException {
    return getServiceConfigs(service, ConfigServerConstants.CERTS);
  }

  /**
   * Get files from config directory for given service details
   *
   * @param authToken will be used for validate service id
   * @param service:  object with service details like projectName, projectVersion, serviceName, serviceVersion etc. to create config directory files path
   * @return service files
   * @throws ApiException when fails to fetch files from config directory
   */
  @Override
  public ServiceConfigs getServiceFiles(String authToken, Service service) throws ApiException {
    return getServiceConfigs(service, ConfigServerConstants.FILES);
  }

  private ServiceConfigs getServiceConfigs(Service service, String files) {
    ServiceConfigs serviceConfigs = new ServiceConfigs();
    serviceConfigs.setService(service);
    serviceConfigs.setConfigProperties(new HashMap<String, Object>());
    String configKey = null;
    Map<String, Object> configMap = null;

    //Get Global files
    configKey = buildConfigKey(service, files, ConfigServerConstants.GLOBALS, service.getProjectVersion());
    configMap = getConfigs(configKey);
    if (configMap != null) {
      ((Map<String, Object>) serviceConfigs.getConfigProperties()).putAll(configMap);
    }
    //Get Service files
    configKey = buildConfigKey(service, files, service.getServiceName(), service.getServiceVersion());
    configMap = getConfigs(configKey);
    if (configMap != null) {
      ((Map<String, Object>) serviceConfigs.getConfigProperties()).putAll(configMap);
    }
    return serviceConfigs;
  }

  @Override
  public List<Service> searchServices(String authToken, String projectName) throws ApiException {
    return null;
  }

  @Override
  public List<String> saveServiceConfigs(String authToken, Service service, List<ServiceConfig> serviceConfigs) {
    List<String> failedConfigKeys = new ArrayList<>();
    Map<String, Object> configServerConfig = Config.getInstance().getJsonMapConfig(ConfigServerConstants.CONFIG_NAME);
    String collectionName = (String) configServerConfig.get(MONGO_COLLECTION_NAME);
    MongoCollection<Document> collection = MongoStartupHookProvider.db.getCollection(collectionName);

    Map<String, List<ServiceConfig>> configsByKey = new HashMap<>();
    for (ServiceConfig serviceConfig : serviceConfigs) {
      String key = resolver.resolve(service, serviceConfig);
      List<ServiceConfig> configs = configsByKey.getOrDefault(key, new ArrayList<>());
      configs.add(serviceConfig);
      configsByKey.put(key, configs);
    }
    configsByKey.forEach((key, configs) -> {
      try {
//        collection.insertOne(new Document("_id", key).append("config", configs.get(0)));
        collection.updateOne(eq("_id", key), set("configs", configs.stream().map(ServiceConfig::toBson).collect(Collectors.toList())), new UpdateOptions().upsert(true));
      } catch (Exception e) {
        logger.error("cannot set config {}", key, e);
        failedConfigKeys.add(key);
      }
    });
    return failedConfigKeys;
  }

  private String buildConfigKey(Service service, String configType, String name, String version) {
    StringBuffer configPath = new StringBuffer();
    configPath.append(configType)
            .append(ConfigServerConstants.SLASH).append(service.getProjectName())
            .append(ConfigServerConstants.SLASH).append(name)
            .append(ConfigServerConstants.SLASH).append(version)
            .append(ConfigServerConstants.SLASH).append(service.getEnvironment());
    return configPath.toString();
  }

  private Map<String, Object> getConfigs(String configKey) {
    Map<String, Object> configServerConfig = Config.getInstance().getJsonMapConfig(ConfigServerConstants.CONFIG_NAME);
    String collectionName = (String) configServerConfig.get(MONGO_COLLECTION_NAME);
    MongoCollection<Document> collection = MongoStartupHookProvider.db.getCollection(collectionName);
    Map<String, Object> configsMap = new HashMap<>();

    Document document = new Document("_id", configKey);
    Document entry;
    try {
      entry = collection.find(document).first();
      if (entry == null || entry.isEmpty()) {
        logger.error("cannot get entry from mongo db with key: {}", configKey);
        return configsMap;
      }
      logger.debug("loaded config with content {}", entry);
      List<Document> configs = ((List)entry.get("configs"));
      configs.forEach(config -> {
        byte[] content = config.getString("content").getBytes();
        String encodedContent = Base64.getMimeEncoder().encodeToString(content);
        configsMap.put(config.getString("configName"), encodedContent);
      });

    } catch (Exception e) {
      logger.error("cannot get entry from mongo db with key: {} with error {}", configKey, e);
      return configsMap;
    }
    return configsMap;
  }
}
