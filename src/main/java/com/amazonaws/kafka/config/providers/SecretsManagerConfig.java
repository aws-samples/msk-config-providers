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

import java.util.Map;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.ValidString;

import com.amazonaws.kafka.config.providers.common.CommonConfigUtils;

public class SecretsManagerConfig extends AbstractConfig{

    public static final String NOT_FOUND_STRATEGY = "NotFoundStrategy";
    public static final String NOT_FOUND_FAIL = "fail";
    public static final String NOT_FOUND_IGNORE = "ignore";

    public static final String RAW_SECRET_ENCODING = "RawSecretEncoding";
    public static final String RAW_SECRET_ENCODING_NONE = "none";
    public static final String RAW_SECRET_ENCODING_BASE64 = "base64";

    private static final String NOT_FOUND_STRATEGY_DOC =
            "An action to take in case a secret cannot be found. "
            + "Possible actions are: `ignore` and `fail`. <br>"
            + "If `ignore` is selected and a secret cannot be found, the empty string will be assigned to a parameter.<br>"
            + "If `fail` is selected, the config provider will throw an exception to signal the issue.<br>"
            + "If there is a connectivity or access issue with AWS Secrets Manager service, an exception will be thrown.";

    private static final String RAW_SECRET_ENCODING_DOC =
            "Encoding to apply when returning the entire secret value via the two-segment form (e.g., ${secretsmanager:mySecret}). "
            + "Possible values are: `none` and `base64`. <br>"
            + "If `none` is selected, the raw secret string is returned as-is. "
            + "Note: if the secret value contains double quotes (e.g., JSON), these must be escaped for use in JAAS configs.<br>"
            + "If `base64` is selected, the secret string is Base64-encoded before being returned, "
            + "which avoids special character escaping requirements.";

    public SecretsManagerConfig(Map<?, ?> originals) {
        super(config(), originals);
    }

    private static ConfigDef config() {
        return new ConfigDef(CommonConfigUtils.COMMON_CONFIG)
                .define(
                        NOT_FOUND_STRATEGY,
                        ConfigDef.Type.STRING,
                        NOT_FOUND_IGNORE,
                        ValidString.in(NOT_FOUND_FAIL, NOT_FOUND_IGNORE),
                        ConfigDef.Importance.LOW,
                        NOT_FOUND_STRATEGY_DOC
                )
                .define(
                        RAW_SECRET_ENCODING,
                        ConfigDef.Type.STRING,
                        RAW_SECRET_ENCODING_NONE,
                        ValidString.in(RAW_SECRET_ENCODING_NONE, RAW_SECRET_ENCODING_BASE64),
                        ConfigDef.Importance.LOW,
                        RAW_SECRET_ENCODING_DOC
                );
    }
}