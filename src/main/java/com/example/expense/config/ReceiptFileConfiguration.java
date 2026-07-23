package com.example.expense.config;

import com.example.expense.storage.EicarPatternMalwareScanner;
import com.example.expense.storage.LocalReceiptStorage;
import com.example.expense.storage.MalwareScanner;
import com.example.expense.storage.ReceiptStorage;
import com.example.expense.storage.S3ReceiptStorage;
import com.example.expense.storage.UnavailableMalwareScanner;
import com.example.expense.storage.UnavailableReceiptStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(ReceiptFileProperties.class)
public class ReceiptFileConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "app.receipt.storage",
            name = "type",
            havingValue = "local"
    )
    public ReceiptStorage localReceiptStorage(ReceiptFileProperties properties) {
        return new LocalReceiptStorage(properties.getStorage().getLocalRoot());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.receipt.storage",
            name = "type",
            havingValue = "unavailable",
            matchIfMissing = true
    )
    public ReceiptStorage unavailableReceiptStorage() {
        return new UnavailableReceiptStorage();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.receipt.storage",
            name = "type",
            havingValue = "s3"
    )
    public S3Client receiptS3Client(ReceiptFileProperties properties) {
        String configuredBucket = properties.getStorage().getS3Bucket();
        if (configuredBucket == null || configuredBucket.isBlank()) {
            throw new IllegalArgumentException("Receipt S3 bucket is required.");
        }
        String configuredRegion = properties.getStorage().getS3Region();
        if (configuredRegion == null || configuredRegion.isBlank()) {
            throw new IllegalArgumentException("Receipt S3 region is required.");
        }
        return S3Client.builder()
                .region(Region.of(configuredRegion.trim()))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.receipt.storage",
            name = "type",
            havingValue = "s3"
    )
    public ReceiptStorage s3ReceiptStorage(
            S3Client receiptS3Client,
            ReceiptFileProperties properties
    ) {
        return new S3ReceiptStorage(
                receiptS3Client,
                properties.getStorage().getS3Bucket()
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.receipt.scanner",
            name = "type",
            havingValue = "eicar-test"
    )
    public MalwareScanner eicarPatternMalwareScanner() {
        return new EicarPatternMalwareScanner();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.receipt.scanner",
            name = "type",
            havingValue = "unavailable",
            matchIfMissing = true
    )
    public MalwareScanner unavailableMalwareScanner() {
        return new UnavailableMalwareScanner();
    }
}
