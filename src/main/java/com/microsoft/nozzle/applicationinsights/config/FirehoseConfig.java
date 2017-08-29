package com.microsoft.nozzle.applicationinsights.config;

import com.microsoft.nozzle.applicationinsights.cache.AppDataCache;
import com.microsoft.nozzle.applicationinsights.nozzle.FirehoseConsumer;
import com.microsoft.nozzle.applicationinsights.nozzle.FirehoseEventRouter;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.doppler.DopplerClient;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.doppler.ReactorDopplerClient;
import org.cloudfoundry.reactor.tokenprovider.ClientCredentialsGrantTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.Retryable;

import java.net.MalformedURLException;
import java.net.URL;

@Configuration
@EnableConfigurationProperties(NozzleProperties.class)
public class FirehoseConfig {
    private DefaultConnectionContext connectionContext(String apiHost, Boolean skipSslValidation) {
        return DefaultConnectionContext.builder()
                .apiHost(apiHost)
                .skipSslValidation(skipSslValidation)
                .build();
    }

    private TokenProvider tokenProvider(String clientId, String clientSecret) {
        return ClientCredentialsGrantTokenProvider.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
    }

    @Bean
    @Autowired
    @Retryable // Retry in case of unstable network connection to the API endpoint
    public DopplerClient dopplerClient(NozzleProperties properties) {
        return ReactorDopplerClient.builder()
                .connectionContext(connectionContext(getApiHost(properties), properties.isSkipSslValidation()))
                .tokenProvider(tokenProvider(properties.getClientId(), properties.getClientSecret()))
                .build();
    }

    @Bean
    @Autowired
    @Retryable // Retry in case of unstable network connection to the API endpoint
    public CloudFoundryClient cfClient(NozzleProperties properties) {
        return ReactorCloudFoundryClient.builder()
                .connectionContext(connectionContext(getApiHost(properties), properties.isSkipSslValidation()))
                .tokenProvider(tokenProvider(properties.getClientId(), properties.getClientSecret()))
                .build();
    }

    @Bean
    @Autowired
    FirehoseConsumer firehoseConsumer(DopplerClient dopplerClient, NozzleProperties properties, FirehoseEventRouter router) {
        return new FirehoseConsumer(dopplerClient, properties, router);
    }

    @Bean
    @Autowired
    AppDataCache appDataCache(CloudFoundryClient cfClient) {
        return new AppDataCache(cfClient);
    }

    /**
     * Get the API address without "https://"
     *
     * @param properties
     * @return
     */
    private String getApiHost(NozzleProperties properties) {
        String apiHost = properties.getApiAddr();

        // The accepted format of API address shouldn't contain "https://", handle the case if the user input address contains "https://"
        try {
            URL url = new URL(apiHost);
            apiHost = url.getHost();
        } catch (MalformedURLException e) {
        }

        return apiHost;
    }
}
