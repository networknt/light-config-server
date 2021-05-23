How to prepare provider storage 
===============================
Each Config Server Provider needs its storage backend to store and read the configurations from. 
Here are the instructions to setup the provider storage for each type of the provider:

###	1. File System Provider:
The file system provider can be used for local environment when testing with config server. Not recommended for
production use, unless until the encryption and authentication is implemented.
 
To use this provider: 
- Create a folder for the externalized configurations
- Set configserver.serviceConfigsDir property in Config Server's config properties to point to this folder e.g. 
```
configserver.serviceConfigsDir: /light-service-configs
```

Folder should follow below structure 
```
{light-service-configs-dir} e.g. /light-service-configs
|-- configs
   |-- {projectName} e.g. retail
       |-- globals
           |-- {projectVersion} e.g. v1
               |-- {environment} e.g. dev
                   |-- values.yml      // project's global configs go here
       |-- {serviceName} e.g. api-customers
           |-- {serviceVersion} e.g. v1
               |-- {environment} e.g. dev
                   |-- values.yml     // service configs go here
       |-- ... add more services
|-- certs
   |-- {projectName} e.g. retail
       |-- globals
           |-- {projectVersion} e.g. v1
               |-- {environment} e.g. dev
                 |-- client.keystore   // project's global cert files go here
       |-- {serviceName} e.g. api-customers
           |-- {serviceVersion} e.g. v1
               |-- {environment} e.g. dev
                 |-- client.keystore   // project's service cert files go here
       |-- ... add more services
|-- files
   |-- {projectName} e.g. retail
       |-- globals
           |-- {projectVersion} e.g. v1
               |-- {environment} e.g. dev
                     |-- logback.xml   // project's global config files go here
                     |-- hibernate.properties
       |-- {serviceName} e.g. api-customers
           |-- {serviceVersion} e.g. v1
               |-- {environment} e.g. dev
                    |-- logback.xml   // project's service config files go here
                    |-- hibernate.properties
       |-- ... add more services
```


###	2. Git Provider:
The Git Provider enables storing the service configs in git repository.

To use this provider: 
- Create a separate git repo following the naming convention as `light-service-configs-{projectName}-{environment}` for
  each project/env pair e.g. light-service-configs-retail-dev for 'retail' project in 'dev' env
- Populate configserver.gitApiHost, configserver.gitApiContextRoot, configserver.gitRepoOwner, configserver.gitRepoName in Config Server's config properties with proper values. e.g.
```
configserver.gitApiHost: https://api.github.com
configserver.gitApiContextRoot: repos
configserver.gitRepoOwner: networknt
configserver.gitRepoName: light-service-configs-{projectName}-{environment}
```
Keeping repo name with placeholders {projectName} & {environment} in config properties enables using same config server instance for multiple projects and environments.

If repo name is mentioned in config properties, it will be used for all the requests after replacing projectName and
environment placeholder with values from the request. If repo name is missing, then it will be constructed using
projectName and environment from the request path as light-service-configs-{projectName}-{environment} for each request.
 
This provider assumes that caller has git authorization token already.
 
Git repo should follow below folder structure: 
 ```
{git-repo-name} // recommended naming conventions is "light-service-configs-{projectName}-{environment}" e.g. light-service-configs-retail-dev
|-- configs
   |-- globals
       |-- {projectVersion} e.g. v1
           |-- values.yml    // project's global configs go here
   |-- {serviceName} e.g. api-customers
       |-- {serviceVersion} e.g. v1
           |-- values.yml    // service configs go here
   |-- ... add more services
|-- certs
   |-- globals
       |-- {projectVersion} e.g. v1
           |-- client.keystore   // project's global cert files go here
           |-- client.truststore
   |-- {serviceName} e.g. api-customers
       |-- {serviceVersion} e.g. v1
           |-- client.keystore    // service cert files go here
           |-- server.truststore
   |-- ... add more services
|-- files
   |-- globals
       |-- {projectVersion} e.g. v1
           |-- logback.xml   // project's global config files go here
           |-- hibernate.properties
   |-- {serviceName} e.g. api-customers
       |-- {serviceVersion} e.g. v1
           |-- logback.xml    // service config files go here
           |-- hibernate.properties
   |-- ... add more services
  ```    
  
  
###	3. Vault Provider:
Vault can be used as a secure backend for the config storage. It provides encryption/decryption & Access Control Policies out of the box. 
There is also an UI that can be used for managing the config values.
Vault does not provide its own storage backend and it needs to be configured to use one of:
- Consul key/value pairs
- Filesystem (encrypted)
- SQL database  

Consul key/value pairs is recommended as the storage backend for Vault. 

To use this provider:
- Install and setup the Vault Server instance (Refer to [More Info](#more-info-on-vault) section below)
- Create 3 separate KV type Secret Engines for configs, certs and files; make sure you name path as 'configs', 'certs' & 'files' and select Version 2 for each of them.
- Create secrets under those secret engines by following `{project_name}/{service_name}/{service_version}/{environment}` naming convention for path e.g.
    ```
    retail/globals/v1/dev
    retail/api-customers/v1/dev
    retail/globals/v1/test
    retail/api-customers/v1/test
    ```
- Add configserver.vaultServerUri property in Config Server's config properties and point it to Vault server url. e.g.
    ```
    configserver.vaultServerUri: http://localhost:8200
    ```

### 4. Url Provider
This provider fetch configs from centralized Nginx, and the config structure is just like File System Provider. You can manage real configs with GIT or SVN, or File System.


To use this provider:
- Create a GIT or SVN repository, or just /light-servie-configs directory. config nginx e.g.
```
server {
    server_name git.xlongwei.com;
    location /light-service-configs/ {
        alias /soft/gitdata/light-service-configs/;
        autoindex on;
    }
}
```
- Using -Dconfigserver.serviceConfigsHost system property, or Set configserver.serviceConfigsHost and configserver.serviceConfigsDir property in Config Server's config properties to point to this configserver e.g. 
```
configserver.serviceConfigsHost: https://git.xlongwei.com
configserver.serviceConfigsDir: /light-service-configs
```

###### More info on Vault:
- [What is it](https://www.vaultproject.io)
- [How to install](https://learn.hashicorp.com/vault/getting-started/install)
- [How to setup](https://learn.hashicorp.com/vault/getting-started/deploy )
- [How to login](https://learn.hashicorp.com/vault/getting-started/authentication )
- Config Management Options:
    - [CLI](https://www.vaultproject.io/docs/commands/)
    - [HTTP APIs](https://learn.hashicorp.com/vault/getting-started/apis)
    - [Web UI](https://learn.hashicorp.com/vault/getting-started/ui) (Recommended)
- [ACL Policies](https://learn.hashicorp.com/vault/getting-started/policies)
