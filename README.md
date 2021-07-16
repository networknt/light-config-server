# light-config-server

Light Config Server is a restful service that supports pluggable backends like RDBMS, Consul, Vault, Static Site, MongoDB and Git etc.

Most users are using Git to manage configurations for their services; however, they will eventually face the challenges of the multi-dimension nature of the service configuration. Each service has its configuration, and each tag/release of the service has its configuration. The flattened Git repo is not very suitable to manage the complicated requirement. What if we need to add a config property to the values.yml for thousands of services? Can we do that with only one update? The Light Config Server manages all services with different tags and environments, and one API call can update all services for a particular tag across the organization. 


**Design Details**: [Config Server Design](docs/DESIGN.md)

**Provider Storage**: [How to Prepare Provider Storage](docs/PROVIDERS.md)

**Usage Details**: [How to Use](docs/USAGE.md)

![Config Server Architecture](docs/ConfigServerArchitecture.png)




## Start and verify locally.

### Backend repository (MongoDB)

We can connect to mongoDB dev server directly for local testing, or start local mongoDB by docker-compose

1. Use mongoDB cloud server for testing:

Add environment variable:

CONFIGSERVER_MONGODBURI: mongodb://@your_cloud_mongodb:port


Set the environment variable on IDE

![Config Server setting](docs/config.png)

2.  Use local mongoDB by docker-compose (it needs docker installed locally)

``` 
cd ~/workspace/eadp-config-server

docker-compose up

```

- change the configserver.yml config on config folder to point to local mongoDB

```

#mongoDBUri: ${CONFIGSERVER_MONGODBURI}
#mongoDBName: ${configserver.mongoDBName:APIConfigDB}
#mongoDBCollection: ${configserver.mongoDBCollection:configs}

mongoDBUri: ${configserver.mongoDBUri:mongodb://root:example@localhost:27017}
mongoDBName: ${configserver.mongoDBName:configServer}
mongoDBCollection: ${configserver.mongoDBCollection:configs}
```

### Start the local config server

1 From IDE (please refer the diagram above)

2 From command line (change the timestamp for the jar file):

java -jar target/config-server.jar


### Connect local API or proxy to config server:

Following the steps in the document below for config-server connection

https://doc.networknt.com/service/config/service-config/

