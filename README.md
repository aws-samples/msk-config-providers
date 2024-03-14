# Apache Kafka Config Providers

This repository provides samples of Apache Kafka Config Providers that can be used to integrate Kafka client properties with other systems. Following Kafka clients can use config providers:

- Kafka admin client
- Kafka consumer
- Kafka producer
- Kafka Connect (both workers and connectors)
- Kafka Streams


## How to use

Config providers, their configuration and usage are defined as properties of the Kafka client:

```
# define names of config providers:
config.providers=secretsmanager,ssm,s3import

# provide implementation classes for each provider:
config.providers.secretsmanager.class    = com.amazonaws.kafka.config.providers.SecretsManagerConfigProvider
config.providers.ssm.class               = com.amazonaws.kafka.config.providers.SsmParamStoreConfigProvider
config.providers.s3import.class          = com.amazonaws.kafka.config.providers.S3ImportConfigProvider

# configure a config provider (if it needs additional initialization), for example you can provide a region where the secrets or S3 buckets are located:
config.providers.secretsmanager.param.region   = us-west-2
config.providers.s3import.param.region         = us-west-2

# below is an example of config provider usage to supply a truststore location and its password. 
# Actual parameter names depend on how those config providers are used in the client's configuration.
database.ssl.truststore.password         = ${secretsmanager:mySslCertCredentials:ssl_trust_pass}
database.ssl.truststore.location         = ${s3import:us-west-2:my_cert_bucket/pass/to/trustore_unique_filename.jks}

# Example of accessing a Secret via an ARN which has been URL Encoded
database.password = ${secretsmanager:arn%3Aaws%3Asecretsmanager%3Aus-west-2%3A123477892456%3Asecret%3A%2Fdb%2Fadmin_user_credentials-ieAE11:password}
```

> NOTE: For the `secretsmanager` provider, the base parser from Kafka does not support using ARNs due to the `:` character. 
> In this case, URL encode the ARN first, and it will be decoded correctly by this Provider. 

More information about configuration of the config providers and usage, see below per config provider.

For a detailed documentation on config providers in general, please follow the Apache Kafka documentation.

### Kafka Connect Users:

** Configuration in distributed mode **

Please note, that Kafka Connect uses two levels of configurations: workers and connectors.

To avoid issues with validation of the connector configuration, users need to define config providers in workers properties, and then use the tokens in the connector properties.

** TTL Support **

Currently Secrets and System Manager Config Providers support time-to-live (TTL) configuration in milliseconds.

```
# Reload configuration and restart connector every 5 minutes:
database.ssl.truststore.password         = ${secretsmanager:mySslCertCredentials:ssl_trust_pass?ttl=300000}
```

Note, 
- TTL value is in milliseconds
- upon an expiration, the entire connector will be restarted, regardless whether a value has been changed or not.


## Build


```
git clone <URL>
cd msk-config-providers
mvn clean package
```

Expected target artifacts:

```
> tree target/
target
├── lib
│   ├── <DEPENDENCT_JARS>
│   └── .................
└─── msk-config-providers-<VERSION>.jar
```

Additionally, there is a flat UBER jar at the following location:

```
target
├── shade-uber
│   └── msk-config-providers-<VERSION>-uber.jar
└── .......
```


## Access Management

Currently config providers do not support credentials to be explicitly provided. Config provider will inherit any type of credentials of hosting application, OS or service.

Access Policy/Role associated with the application that is running a config provider should have sufficient but least privileged permissions to access the services that are configured/referenced in the configuration. E.g., Supplying secrets through AWS Secrets Manager provider will need read permissions for the client to read that particular secret from AWS Secrets Manager service.

## Road Map

- add support for more authentication options
- support TTL for S3Import Config Provider
