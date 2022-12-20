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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.common.config.ConfigChangeCallback;
import org.apache.kafka.common.config.ConfigData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.kafka.config.providers.common.AwsServiceConfigProvider;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * This class implements a ConfigProvider for AWS S3 File Importer.<br>
 * 
 * <p><b>Usage:</b><br>
 * In a configuration file (e.g. {@code client.properties}) define following properties:<br>
 * 
 * <pre>
 * #        Step1. Configure the secrets manager as config provider:
 * config.providers=s3import
 * config.providers.s3import.class=com.amazonaws.kafka.config.providers.S3ImportConfigProvider
 * # optional parameter for default region:
 * config.providers.s3import.param.region=us-west-2
 * 
 * #        Step 2. Usage of AWS S3 Importer as config provider with explicitly defined region:
 * database.sslcert=${s3import:us-west-2:my-bucket/full/path/file.jks}
 * #        Alternatively, use default or current region:
 * database.sslcert=${s3import::my-bucket/full/path/file.jks}
 * </pre>
 * 
 * Note, you must have permissions to access an object on S3.
 * 
 * @param region - defines a region to get a secret from.
 * 
 * Expression usage:<br>
 * <code>property_name=${s3import:<REGION>:<OBJECT_KEY>}</code>
 *
 */
public class S3ImportConfigProvider extends AwsServiceConfigProvider {
    
    private final Logger log = LoggerFactory.getLogger(getClass());

    private S3ImportConfig config;

    private String localDir;


    @Override
    public void configure(Map<String, ?> configs) {
        this.config = new S3ImportConfig(configs);
        setCommonConfig(config);

        this.localDir = this.config.getString(S3ImportConfig.LOCAL_DIR);
        if (this.localDir == null || this.localDir.isBlank()) {
            // if not defined, use temp dir defined in OS
            this.localDir = System.getProperty("java.io.tmpdir");
        }
    }

    /**
     * Retrieves all parameters at the given path in SSM Parameters Store.
     *
     * @param path the path in Parameters Store
     * @return the configuration data
     */
   @Override
   public ConfigData get(String path) {
        return get(path, Collections.emptySet());
    }
    
    /**
     * Copies a file from S3 to a local (to a process) file system.
     *
     * @param path (optional) a region where an S3 object is located. If null, 
     *        a default region from config provider's configuration will be used.
     * @return the configuration data with resolved variables.
     */
    @Override
    public ConfigData get(String path, Set<String> keys) {
        Map<String, String> data = new HashMap<>();
        if (   (path == null || path.isEmpty()) 
            && (keys== null || keys.isEmpty())   ) {
            return new ConfigData(data);
        }

        S3Client s3 = checkOrInitS3Client(path);

        for (String key: keys) {
            try {
                Path pKey = Path.of(key);
                Path destination = getDestination(this.localDir, pKey);
                log.debug("Local destination for file: {}", destination);
                
                if (Files.exists(destination)) {
                    // Imported file may already exist on a file system. If tasks are restarting, 
                    // or more than one task is running on a worker, they may use the same file
                    log.info("File already imported at destination: {}", destination);
                    data.put(key, destination.toString());
                    continue;
                }
                GetObjectRequest s3GetObjectRequest = GetObjectRequest.builder()
                        .bucket(getBucket(pKey))
                        .key(getS3ObjectKey(pKey))
                        .build();
                s3.getObject(s3GetObjectRequest, destination);
                log.debug("Successfully imported a file from S3 bucket: s3://{}", key);
                data.put(key, destination.toString());
            } catch(NoSuchKeyException nske) {
                // Simply throw an exception to indicate there are issues with the objects on S3
                throw new RuntimeException("No object found at " + key, nske);
            }
        }

        return new ConfigData(data);
    }
    
    private static Path getDestination(String localDir, Path pKey) {
        Path pDest = Path.of(localDir, pKey.getName(pKey.getNameCount()-1).toString());
        return pDest;
    }

    private static String getS3ObjectKey(Path pKey) {
        return pKey.subpath(1, pKey.getNameCount()).toString();
    }

    private static String getBucket(Path pKey) {
        
        return pKey.getName(0).toString();
    }

    @Override
    public void subscribe(String path, Set<String> keys, ConfigChangeCallback callback) {
        log.info("Subscription is not implemented and will be ignored");
    }

    @Override
    public void close() throws IOException {
    }
    
    protected S3Client checkOrInitS3Client(String regionStr) {
        S3ClientBuilder s3cb = S3Client.builder();

        setClientCommonConfig(s3cb);
        
        // If region is not provided as path, then Common Config sets default region. 
        // No need to override.
        if (regionStr != null && !regionStr.isBlank()) {
            s3cb.region(Region.of(regionStr));
        }
            
        return s3cb.build();
    }
    
    

}
