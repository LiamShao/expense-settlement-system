package com.example.expense.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ReceiptStorageTest {

    private static final String BUCKET = "private-receipt-bucket";
    private static final String KEY = "receipts/2026/07/23/20/generated-id";

    @Mock
    private S3Client s3Client;

    private S3ReceiptStorage storage;

    @BeforeEach
    void setUp() {
        storage = new S3ReceiptStorage(s3Client, BUCKET);
    }

    @Test
    void put_正常系_exactLengthとcontentTypeとSseS3をSDKへ渡す() throws Exception {
        byte[] content = "%PDF-1.7\nreceipt".getBytes(StandardCharsets.US_ASCII);
        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);

        storage.put(
                KEY,
                new ByteArrayInputStream(content),
                content.length,
                "application/pdf"
        );

        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key()).isEqualTo(KEY);
        assertThat(request.contentType()).isEqualTo("application/pdf");
        assertThat(request.serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        assertThat(request.ifNoneMatch()).isEqualTo("*");
        assertThat(request.acl()).isNull();
        assertThat(bodyCaptor.getValue().optionalContentLength()).contains((long) content.length);
        try (var uploaded = bodyCaptor.getValue().contentStreamProvider().newStream()) {
            assertThat(uploaded.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void open_正常系_responseStreamを返す() throws Exception {
        byte[] content = "stored".getBytes(StandardCharsets.UTF_8);
        ResponseInputStream<GetObjectResponse> response = new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength((long) content.length).build(),
                AbortableInputStream.create(new ByteArrayInputStream(content))
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(response);

        try (var opened = storage.open(KEY)) {
            assertThat(opened.readAllBytes()).isEqualTo(content);
        }

        ArgumentCaptor<GetObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(requestCaptor.getValue().key()).isEqualTo(KEY);
    }

    @Test
    void exists_正常系_head成功ならtrueで404ならfalse() {
        assertThat(storage.exists(KEY)).isTrue();

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        assertThat(storage.exists(KEY)).isFalse();
    }

    @Test
    void delete_正常系_bucketとkeyを固定したrequestを送る() {
        storage.delete(KEY);

        ArgumentCaptor<DeleteObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(requestCaptor.getValue().key()).isEqualTo(KEY);
    }

    @Test
    void open_異常系_404をstorageNotFoundへ変換する() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        assertThatThrownBy(() -> storage.open(KEY))
                .isInstanceOf(ReceiptStorageObjectNotFoundException.class);
    }

    @Test
    void sdk障害_異常系_bucketやkeyを例外messageへ漏らさない() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(SdkClientException.create("credential detail"));

        assertThatThrownBy(() -> storage.exists(KEY))
                .isInstanceOf(ReceiptStorageException.class)
                .hasMessage("Failed to inspect receipt object.")
                .hasMessageNotContaining(BUCKET)
                .hasMessageNotContaining(KEY);
    }

    @Test
    void put_異常系_invalidInputはSDK呼出前に拒否する() {
        assertThatThrownBy(() -> storage.put(
                "../outside",
                new ByteArrayInputStream(new byte[]{1}),
                1,
                "application/pdf"
        )).isInstanceOf(ReceiptStorageException.class);
        assertThatThrownBy(() -> storage.put(
                KEY,
                new ByteArrayInputStream(new byte[]{1}),
                0,
                "application/pdf"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.put(
                KEY,
                new ByteArrayInputStream(new byte[]{1}),
                1,
                "text/plain"
        )).isInstanceOf(IllegalArgumentException.class);

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void constructor_異常系_bucket未設定をfailFastする() {
        assertThatThrownBy(() -> new S3ReceiptStorage(s3Client, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket");
    }
}
