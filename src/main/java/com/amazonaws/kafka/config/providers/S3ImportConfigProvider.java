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
import org.apache.kafka.common.config.provider.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class S3ImportConfigProvider implements ConfigProvider {
    
    private final Logger log = LoggerFactory.getLogger(getClass());

    
    private S3ImportConfigDef config;

    private String localDir;
    private String defaultRegion;


    public void configure(Map<String, ?> configs) {
        this.config = new S3ImportConfigDef(configs);
        this.localDir = this.config.getString(S3ImportConfigDef.LOCAL_DIR);
        if (this.localDir == null || this.localDir.isBlank()) {
            this.localDir = System.getProperty("java.io.tmpdir");
        }
        // default region from configuration. It can be null, empty or blank.
        this.defaultRegion = this.config.getString(S3ImportConfigDef.REGION);
    }

    /**
     * Retrieves all parameters at the given path in SSM Parameters Store.
     *
     * @param path the path in Parameters Store
     * @return the configuration data
     */
    public ConfigData get(String path) {
        return get(path, Collections.emptySet());
    }

    
    /**
     * Retrieves all parameters at the given path in SSM Parameters Store with given key.
     *
     * @param path the path in Parameters Store
     * @return the configuration data
     */
    public ConfigData get(String path, Set<String> keys) {
        Map<String, String> data = new HashMap<>();
        if (   (path == null || path.isEmpty()) 
            && (keys== null || keys.isEmpty())   ) {
            return new ConfigData(data);
        }
        // regionDef still can be null!
        String regionDef = path != null && !path.isBlank() ? path : this.defaultRegion;

        S3Client s3 = getS3Client(regionDef);

        for (String key: keys) {
            try {
                Path pKey = Path.of(key);
                Path destination = getDestination(this.localDir, pKey);
                log.debug("Local destination for file: " + destination.toString());
                
                if (Files.exists(destination)) {
                    // Imported file may already exist on a file system. If tasks are restarting, 
                    // or more than one task is running on a worker, they may use the same file
                    data.put(key, destination.toString());
                    continue;
                }else if (!isValidDestination(destination)) {
                    
                }
                
                GetObjectRequest s3GetObjectRequest = GetObjectRequest.builder()
                        .bucket(getBucket(pKey))
                        .key(getS3ObjectKey(pKey))
                        .build();
                s3.getObject(s3GetObjectRequest, destination);
                data.put(key, destination.toString());
            } catch(NoSuchKeyException nske) {
                // Simply throw an exception to indicate there are issues with the objects on S3
                throw new RuntimeException("No object found at " + key, nske);
            } catch (IOException e) {
                // looks like a file cannot be created at destination...
                log.error("Failed to import a file from S3.", e);
                // wrapping into runtime to allow retries
                throw new RuntimeException(e);
            }
        }

        return new ConfigData(data);
    }
    
    
    /*
     * Validates the destination: if file exists, the destination is not valid and `false` will be returned.
     */
    private boolean isValidDestination(Path destination) throws IOException {
        // Alternatively, use this file!!
        if (Files.exists(destination)) {
            log.warn("Destination file %s already exists. Exiting with error...", destination.toString());
            return false;
        }
        Path parentDir = destination.getParent();
        if (Files.notExists(parentDir)) {
            try {
                Files.createDirectories(parentDir);
            } catch (IOException e) {
                log.error("Couldn't create parent directory: " + parentDir.toString());
                throw e;
            }
        }
        return true;
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


    public void close() throws IOException {
    }
    
    protected static S3Client getS3Client(String regionStr) {
        S3ClientBuilder s3cb = S3Client.builder();
        if (regionStr != null && !regionStr.isBlank()) s3cb.region(Region.of(regionStr));
        S3Client s3Client = s3cb.build(); 
            
        return s3Client;
    }
    
    

}
