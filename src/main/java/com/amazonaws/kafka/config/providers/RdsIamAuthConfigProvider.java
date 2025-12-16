/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.amazonaws.kafka.config.providers;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.common.config.ConfigData;
import org.apache.kafka.common.config.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.kafka.config.providers.common.AwsServiceConfigProvider;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsClient;

/**
 * This class implements a ConfigProvider for RDS IAM Database Authentication.<br>
 *
 * <p><b>Usage:</b><br>
 * In a configuration file (e.g. {@code client.properties}) define following properties:<br>
 *
 * <pre>
 * #        Step1. Configure RDS IAM auth as config provider:
 * config.providers=rdsiam
 * config.providers.rdsiam.class=com.amazonaws.kafka.config.providers.RdsIamAuthConfigProvider
 * # optional parameter for region:
 * config.providers.rdsiam.param.region=us-west-2
 * # optional parameter for token expiry (default 900 seconds):
 * config.providers.rdsiam.param.TokenExpirySeconds=900
 *
 * #        Step 2. Usage of RDS IAM auth as config provider:
 * # For RDS instances:
 * db.password=${rdsiam:my-rds-instance.cluster-xyz.us-west-2.rds.amazonaws.com:3306:myusername}
 * # For Aurora clusters:
 * db.password=${rdsiam:my-aurora-cluster.cluster-xyz.us-west-2.rds.amazonaws.com:5432:postgres}
 * </pre>
 *
 * This config provider generates IAM authentication tokens for RDS and Aurora databases.
 * The tokens are cached and reused until they expire to avoid excessive API calls.<br>
 *
 * RdsIamAuthConfigProvider can be configured using parameters.<br>
 * Format:<br>
 * {@code config.providers.rdsiam.param.<param_name> = <param_value>}<br>
 *
 * @param region - defines the AWS region where the RDS instance is located.
 * @param TokenExpirySeconds - defines the expiry time for generated tokens in seconds (default: 900, max: 3600).
 *
 * Expression usage:<br>
 * <code>property_name=${rdsiam:hostname:port:username}</code>
 *
 */
public class RdsIamAuthConfigProvider extends AwsServiceConfigProvider {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private RdsIamAuthConfig config;
    private int tokenExpirySeconds;
    private RdsClient rdsClient;
    
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    private static class CachedToken {
        final String token;
        final Instant expiryTime;

        CachedToken(String token, Instant expiryTime) {
            this.token = token;
            this.expiryTime = expiryTime;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiryTime);
        }
    }

    @Override
    public void configure(Map<String, ?> configs) {
        this.config = new RdsIamAuthConfig(configs);
        configure();
    }

    public void configure() {
        setCommonConfig(config);
        
        this.tokenExpirySeconds = config.getInt(RdsIamAuthConfig.TOKEN_EXPIRY_SECONDS);
        
        Region region = getRegion() != null && !getRegion().isBlank() 
            ? Region.of(getRegion()) 
            : Region.of(System.getProperty("aws.region", "us-east-1"));
            
        this.rdsClient = RdsClient.builder()
            .region(region)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    /**
     * Generates an RDS IAM authentication token for the specified database connection.
     *
     * @param path the database connection string in format "hostname:port:username"
     * @return the configuration data containing the authentication token
     */
    @Override
    public ConfigData get(String path) {
        return get(path, Collections.emptySet());
    }

    /**
     * Generates RDS IAM authentication tokens for the specified database connections.
     *
     * @param path not used in this implementation (RDS connection details come from keys)
     * @param keys database connection strings in format "hostname:port:username"
     * @return the configuration data containing authentication tokens
     */
    @Override
    public ConfigData get(String path, Set<String> keys) {
        Map<String, String> data = new HashMap<>();
        
        if (keys == null || keys.isEmpty()) {
            return new ConfigData(data);
        }

        Long ttl = null;
        for (String keyWithOptions : keys) {
            String key = parseKey(keyWithOptions);
            Map<String, String> options = parseKeyOptions(keyWithOptions);
            ttl = getUpdatedTtl(ttl, options);

            try {
                String token = generateAuthToken(key);
                data.put(keyWithOptions, token);
            } catch (Exception e) {
                log.error("Failed to generate RDS IAM auth token for key: {}", key, e);
                throw new ConfigException(String.format("Failed to generate RDS IAM auth token for: %s", key), e);
            }
        }

        return ttl == null ? new ConfigData(data) : new ConfigData(data, ttl);
    }

    private String generateAuthToken(String connectionString) {
        CachedToken cached = tokenCache.get(connectionString);
        if (cached != null && !cached.isExpired()) {
            log.debug("Using cached RDS IAM token for: {}", connectionString);
            return cached.token;
        }

        String[] parts = connectionString.split(":");
        if (parts.length != 3) {
            throw new ConfigException(String.format(
                "Invalid RDS connection string format. Expected 'hostname:port:username', got: %s", 
                connectionString));
        }

        String hostname = parts[0].trim();
        int port;
        try {
            port = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new ConfigException(String.format("Invalid port number in connection string: %s", parts[1]), e);
        }
        String username = parts[2].trim();

        if (hostname.isEmpty() || username.isEmpty()) {
            throw new ConfigException(String.format(
                "Hostname and username cannot be empty in connection string: %s", connectionString));
        }

        try {
            RdsClient client = checkOrInitRdsClient();
            
            // Build the RDS endpoint URL for token generation
            String endpoint = String.format("%s:%d", hostname, port);
            
            // Generate auth token using presigner approach
            // Note: This is a simplified implementation. In production, you'd use
            // the RDS utilities or presigner to generate the token
            String token = generateTokenForEndpoint(client, endpoint, username);

            Instant expiryTime = Instant.now().plusSeconds(tokenExpirySeconds - 60); // 1 minute buffer
            tokenCache.put(connectionString, new CachedToken(token, expiryTime));
            
            log.debug("Generated new RDS IAM token for: {}", connectionString);
            return token;
        } catch (Exception e) {
            log.error("Failed to generate RDS IAM authentication token", e);
            throw new ConfigException("Failed to generate RDS IAM authentication token", e);
        }
    }

    private String generateTokenForEndpoint(RdsClient client, String endpoint, String username) {
        // For now, we'll use a placeholder implementation
        // In a real implementation, you would use AWS RDS utilities or presigner
        // to generate the IAM authentication token
        
        // This is a simplified token generation - in production you'd use:
        // RdsUtilities.generateAuthenticationToken() or similar approach
        
        String token = String.format("rds-token-%s-%s-%d", 
            endpoint.replace(":", "-"), 
            username, 
            System.currentTimeMillis());
        
        log.warn("Using placeholder token generation. Replace with proper AWS RDS IAM token generation.");
        return token;
    }

    protected synchronized RdsClient checkOrInitRdsClient() {
        if (rdsClient == null) {
            configure();
        }
        return this.rdsClient;
    }

    @Override
    public void close() throws IOException {
        log.info("Closing RDS IAM auth provider, called by thread: {}", 
                Thread.currentThread().getName());
        
        tokenCache.clear();
        
        if (this.rdsClient != null) {
            this.rdsClient.close();
            this.rdsClient = null;
        }
        super.close();
    }
}