package com.amazonaws.kafka.config.providers;

import org.mockito.stubbing.Answer;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockedSecretsManagerConfigProvider extends SecretsManagerConfigProvider {
    @Override
    protected void checkOrInitSecretManagerClient() {
        this.secretsClient = mock(SecretsManagerClient.class);
        when(secretsClient.getSecretValue(request("AmazonMSK_TestKafkaConfig"))).thenAnswer(
                (Answer<GetSecretValueResponse>) invocation -> response("{\"username\": \"John\", \"password\":\"Password123\"}")
        );
        when(secretsClient.getSecretValue(request("notFound"))).thenThrow(ResourceNotFoundException.class);
    }

    private GetSecretValueRequest request(String path) {
        return GetSecretValueRequest.builder().secretId(path).build();
    }

    private GetSecretValueResponse response(String value) {
        return GetSecretValueResponse.builder().secretString(value).build();
    }
}
