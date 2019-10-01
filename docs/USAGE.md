How to Use Config Server
========================
### Running Light Config Server:

Starting config server instance is similar to starting any other light-4j module (run Server class).   
Make sure you have selected correct config server provider implementation in service.yml file.  
Make sure to prepare respective provider storage as per [How to Prepare Provider Storage](docs/PROVIDERS.md) & have
correct config values in configserver.yml.

### Integrating Light Config Server with Client API:

Make sure you have Light Config server instance up and running before configuring your client API to use config server.
Client requires minimum configurations in order to be able to access config server. From there on, the rest of the
configuration will be downloaded and injected into the light-4j framework flow.

Server (light-4j startup) class is modified so that it enables different config loading implementation to be selected at
the runtime. Accordingly a Default Config Loader implementation is developed for the interaction with the light config
server.

##### Startup.yml:

New configuration file startup.yml is introduced & needs to be added in api config in order to integrate with light
config server. This file provides basic information about the service(api) and the project it belongs to. This file is
considered static and it is part of the API jar file. There are no externalized values in it.   
This file can also define which implementation of the config loader is to be used if its not DefaultConfigLoader.

```
# Implementation of the config loader interface if you are not using DefaultConfigLoader
configLoaderClass: com.abc.ABCConfigLoader

# Project name and version where this API belongs to
projectName: retail
projectVersion: v1

# Service name and version
serviceName: api-customers
serviceVersion: v1
```
        

##### Config Loader Configurations:
 
 In addition to the startup.yml, Client API need to provide below Environment Variables & JVM parameters in the
 invocation command. These parameters are used by DefaultConfigLoader called through light-4J framework during API
 startup.

**Environment Variables:**
1. config_server_authorization: Authorization token for config server provider implementation. Format of the token 
   depends on the config provider. For Git provider, its GIT API Bearer token while its Base64 encoded basic
   authorization token for Vault.
2. config_server_client_truststore_location: Location for the initial client truststore to be used while connecting to
   Config Server.
3. config_server_client_truststore_password: for the initial client truststore to be used
4. config_server_client_verify_host_name: Flag to specify host name verification (True/False)

**JVM Parameters:**
1. light-config-server-uri: URL of the light config server instance
2. light-env: Environment
3. light-4j-config-dir: Location for saving third party config files

 Example:  
 ```
 -Dlight-config-server-uri="http://localhost:8443" -Dlight-env=dev -Dlight-4j-config-dir="/config" 
 ```

