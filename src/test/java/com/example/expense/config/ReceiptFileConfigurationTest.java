package com.example.expense.config;

import com.example.expense.storage.ReceiptStorage;
import com.example.expense.storage.S3ReceiptStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptFileConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ReceiptFileConfiguration.class);

    @Test
    void s3Storage_正常系_明示選択時だけS3Beanを構築する() {
        contextRunner
                .withPropertyValues(
                        "app.receipt.storage.type=s3",
                        "app.receipt.storage.s3-bucket=private-receipts",
                        "app.receipt.storage.s3-region=ap-northeast-1"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(S3Client.class);
                    assertThat(context).hasSingleBean(ReceiptStorage.class);
                    assertThat(context.getBean(ReceiptStorage.class))
                            .isInstanceOf(S3ReceiptStorage.class);
                });
    }

    @Test
    void defaultStorage_正常系_AwsClientを構築しない() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(S3Client.class);
            assertThat(context).hasSingleBean(ReceiptStorage.class);
        });
    }

    @Test
    void s3Storage_異常系_bucket未設定ならstartupを失敗する() {
        contextRunner
                .withPropertyValues(
                        "app.receipt.storage.type=s3",
                        "app.receipt.storage.s3-region=ap-northeast-1"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("Receipt S3 bucket is required.");
                });
    }
}
