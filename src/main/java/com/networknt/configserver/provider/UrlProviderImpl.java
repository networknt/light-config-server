package com.networknt.configserver.provider;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.networknt.configserver.model.ServiceConfig;
import org.jose4j.json.internal.json_simple.JSONValue;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.client.Http2Client;
import com.networknt.client.builder.ConnectionCacheManager;
import com.networknt.client.model.TimeoutDef;
import com.networknt.config.Config;
import com.networknt.configserver.constants.ConfigServerConstants;
import com.networknt.configserver.model.Service;
import com.networknt.configserver.model.ServiceConfigs;
import com.networknt.exception.ApiException;
import com.networknt.status.Status;
import com.networknt.utility.StringUtils;

import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

/**
 * Config Server Provider with configurations structure lile {@link FileSystemProviderImpl}.
 * set your configserver.serviceConfigsHost config property.
 * 
 * client.yml tls.loadTrustStore=false
 * 
 * @author xlongwei
 */
public class UrlProviderImpl implements IProvider {
	static final String SERVICE_CONFIGS_HOST = "serviceConfigsHost";
    static final String SERVICE_CONFIGS_DIR = "serviceConfigsDir";
    static final String CONFIGS_FILE_NAME = "values";
    static final String CONFIG_EXT_JSON = ".json";
    static final String CONFIG_EXT_YAML = ".yaml";
    static final String CONFIG_EXT_YML = ".yml";
    static final String[] configExtensionsOrdered = {CONFIG_EXT_YML, CONFIG_EXT_YAML, CONFIG_EXT_JSON};
    static final ConnectionCacheManager connectionCacheManager = new ConnectionCacheManager();
    static final ExecutorService executorService = Executors.newCachedThreadPool();
    static final Http2Client client = Http2Client.getInstance();
    static final Pattern files = Pattern.compile(">([^>/]+)</a>");
    private TimeoutDef connectionRequestTimeout = new TimeoutDef(5, TimeUnit.SECONDS);
    private TimeoutDef requestTimeout = new TimeoutDef(5, TimeUnit.SECONDS);
    private long connectionCacheTTLms = 10000;
    
	@Override
	public String login(String authorization) throws ApiException {
		return authorization;
	}

	@Override
	public ServiceConfigs getServiceConfigs(String authToken, Service service) throws ApiException {
		Map<String, Object> configsMap = new HashMap<String, Object>();
        ServiceConfigs serviceConfigs = new ServiceConfigs();
        serviceConfigs.setConfigProperties(configsMap);
        serviceConfigs.setService(service);
        String configPath = null;

        //Get Global configs
        configPath = buildConfigPath(service, ConfigServerConstants.CONFIGS, ConfigServerConstants.GLOBALS, service.getProjectVersion());
        configsMap.putAll(getConfigs(authToken, configPath, CONFIGS_FILE_NAME));
        //Get Service configs
        configPath = buildConfigPath(service, ConfigServerConstants.CONFIGS, service.getServiceName(), service.getServiceVersion());
        configsMap.putAll(getConfigs(authToken, configPath, CONFIGS_FILE_NAME));
        
        return serviceConfigs;
	}

	@Override
	public ServiceConfigs getServiceCertificates(String authToken, Service service) throws ApiException {
		return getFileConfigs(authToken, service, ConfigServerConstants.CERTS);
	}

	@Override
	public ServiceConfigs getServiceFiles(String authToken, Service service) throws ApiException {
		return getFileConfigs(authToken, service, ConfigServerConstants.FILES);
	}

	@Override
	public List<Service> searchServices(String authToken, String projectName) throws ApiException {
		return null;
	}

	@Override
    public List<String> saveServiceConfigs(String authToken, Service service, List<ServiceConfig> serviceConfigs) {
	    return List.of();
    }

	private ServiceConfigs getFileConfigs(String authToken, Service service, String type) throws ApiException {
		Map<String, Object> configsMap = new HashMap<String, Object>();
        ServiceConfigs serviceConfigs = new ServiceConfigs();
        serviceConfigs.setConfigProperties(configsMap);
        serviceConfigs.setService(service);
        String configPath = null;

        //Get Global files
        configPath = buildConfigPath(service, type, ConfigServerConstants.GLOBALS, service.getProjectVersion());
        configsMap.putAll(getFiles(authToken, configPath + ConfigServerConstants.SLASH));
        //Get Service files
        configPath = buildConfigPath(service, type, service.getServiceName(), service.getServiceVersion());
        configsMap.putAll(getFiles(authToken, configPath + ConfigServerConstants.SLASH));
        
        return serviceConfigs;
	}

    private String buildConfigPath(Service service, String configType, String name, String version) {
        Map<String, Object> config = Config.getInstance().getJsonMapConfig(ConfigServerConstants.CONFIG_NAME);
        String configsDir = (String) config.get(SERVICE_CONFIGS_DIR);
        StringBuffer configPath = new StringBuffer(configsDir);
        configPath.append(ConfigServerConstants.SLASH).append(configType)
                .append(ConfigServerConstants.SLASH).append(service.getProjectName())
                .append(ConfigServerConstants.SLASH).append(name)
                .append(ConfigServerConstants.SLASH).append(version)
                .append(ConfigServerConstants.SLASH).append(service.getEnvironment());
        return configPath.toString();
    }
    
    private Map<String, Object> getConfigs(String clientToken, String configPath, String configName) throws ApiException {
    	Map<String, Object> config;
        for (String extension : configExtensionsOrdered) {
            config = loadSpecificConfigFileAsMap(configPath, configName, extension, clientToken);
            if (config != null) return config;
        }
        return Collections.emptyMap();
    }
    
    private Map<String, Object> getFiles(String clientToken, String configPath) throws ApiException {
    	Map<String, Object> configsMap = new HashMap<>();
    	String respBody = getString(clientToken, configPath);
    	if(StringUtils.isNotBlank(respBody)) {
    		Matcher matcher = files.matcher(respBody);
    		String endpoint = null;
    		while(matcher.find()) {
    			String file = matcher.group(1);
    			endpoint = configPath + ConfigServerConstants.SLASH + file;
    			byte[] bs = getBytes(clientToken, endpoint);
    			if(bs!=null && bs.length>0) {
    				String encodedContent = Base64.getMimeEncoder().encodeToString(bs);
                    configsMap.put(file, encodedContent);
    			}
    		}
    		return configsMap;
    	}
    	return Collections.emptyMap();
    }
    
    private Map<String, Object> loadSpecificConfigFileAsMap(String configPath, String configName, String fileExtension, String clientToken) throws ApiException {
        String endpoint = new StringBuilder(configPath).append(ConfigServerConstants.SLASH).append(configName).append(fileExtension).toString();
        String respBody = getString(clientToken, endpoint);
        if(StringUtils.isNotBlank(respBody)) {
        	try {
        		if(!CONFIG_EXT_JSON.equals(fileExtension)) {
        			respBody = JSONValue.toJSONString(new Yaml().load(respBody));
        		}
        		return mapper.readValue(respBody, new TypeReference<Map<String, Object>>() {});
        	}catch (Exception e) {
              logger.error("Exception while parsing Url response: ", e);
              throw new ApiException(new Status("ACS00002"));
			}
        }
        return null;
    }
    
    private String getString(String clientToken, String endpoint) throws ApiException {
    	Object object = sendRequest(clientToken, endpoint, false);
    	return object==null ? null : (String)object;
    }
    
    private byte[] getBytes(String clientToken, String endpoint) throws ApiException {
    	Object object = sendRequest(clientToken, endpoint, true);
    	if(object != null) {
    		ByteBuffer buffer = (ByteBuffer)object;
    		return buffer.array();
    	}
    	return null;
    }
    
    private Object sendRequest(String clientToken, String endpoint, boolean raw) throws ApiException {
    	Map<String, Object> config = Config.getInstance().getJsonMapConfig(ConfigServerConstants.CONFIG_NAME);
    	String configsHost = Objects.toString(config.get(SERVICE_CONFIGS_HOST), System.getProperty(ConfigServerConstants.CONFIG_NAME + "." + SERVICE_CONFIGS_HOST));
        ClientResponse clientResponse = null;
        try {
        	logger.info("GET url configs endpoint:{}", endpoint);
        	URI requestHost = new URI(configsHost);
            ClientRequest clientRequest = new ClientRequest().setMethod(Methods.GET).setPath(endpoint);
            if(StringUtils.isNotBlank(clientToken)) {
            	clientRequest.getRequestHeaders().put(Headers.AUTHORIZATION, clientToken);
            }
            clientRequest.getRequestHeaders().put(Headers.HOST, requestHost.getHost());
            
        	ClientConnection clientConnection = connectionCacheManager.getConnection(requestHost,
        			connectionCacheTTLms, connectionRequestTimeout, true, -1, 0);
        	
            // Send the request
        	AtomicReference<ClientResponse> responseReference = new AtomicReference<>();
        	CountDownLatch latch = new CountDownLatch(1);
            clientConnection.sendRequest(clientRequest, raw
            		? client.byteBufferClientCallback(responseReference, latch)
            				: client.createClientCallback(responseReference, latch));

            // Start a thread to wait for the timeout if provided.
            Future<ClientResponse> clientResponseFuture = executorService.submit(() -> {
                try {
                    if (requestTimeout != null) {
                        latch.await(requestTimeout.getTimeout(), requestTimeout.getUnit());
                    } else {
                        latch.await();
                    }
                    return responseReference.get();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            clientResponse = clientResponseFuture.get();
            if (clientResponse != null) {
                int statusCode = clientResponse.getResponseCode();

                if(statusCode == 200) {
                	return raw ? clientResponse.getAttachment(Http2Client.BUFFER_BODY) : clientResponse.getAttachment(Http2Client.RESPONSE_BODY);
                }
            }
            logger.debug("Received client response: " + clientResponse);
        } catch (URISyntaxException | ExecutionException | TimeoutException | InterruptedException e) {
            logger.error("Exception while calling Url Configs API: ", e);
            throw new ApiException(new Status("ACS00001"));
        }
        return null;
    }
    
}
