# light-config-server

Light Config Server is a restful service that supports pluggable backends like RDBMS, Consul, Vault, Static Site, MongoDB and Git etc.

Most users are using Git to manage configurations for their services; however, they will eventually face the challenges of the multi-dimension nature of the service configuration. Each service has its configuration, and each tag/release of the service has its configuration. The flattened Git repo is not very suitable to manage the complicated requirement. What if we need to add a config property to the values.yml for thousands of services? Can we do that with only one update? The Light Config Server manages all services with different tags and environments, and one API call can update all services for a particular tag across the organization. 


**Design Details**: [Config Server Design](docs/DESIGN.md)

**Provider Storage**: [How to Prepare Provider Storage](docs/PROVIDERS.md)

**Usage Details**: [How to Use](docs/USAGE.md)

![Config Server Architecture](docs/ConfigServerArchitecture.png)