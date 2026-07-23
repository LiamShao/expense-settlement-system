package com.example.expense.controller;

import com.example.expense.common.ApiResponse;
import com.example.expense.dto.response.ReceiptFileResponse;
import com.example.expense.security.SecurityUser;
import com.example.expense.service.AuthorizedReceiptContent;
import com.example.expense.service.ReceiptContentDisposition;
import com.example.expense.service.ReceiptFileException;
import com.example.expense.service.ReceiptFileService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/expense-applications/{applicationId}/items/{itemId}/receipt")
public class ReceiptFileController {

    private final ReceiptFileService receiptFileService;

    public ReceiptFileController(ReceiptFileService receiptFileService) {
        this.receiptFileService = receiptFileService;
    }

    @GetMapping
    public ApiResponse<ReceiptFileResponse> getMetadata(
            @PathVariable Long applicationId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        return ApiResponse.success(receiptFileService.getMetadata(applicationId, itemId, securityUser));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReceiptFileResponse> uploadOrReplace(
            @PathVariable Long applicationId,
            @PathVariable Long itemId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        if (file == null) {
            throw ReceiptFileException.invalidFile("ファイルを選択してください。");
        }
        try (InputStream input = file.getInputStream()) {
            ReceiptFileResponse response = receiptFileService.uploadOrReplace(
                    applicationId,
                    itemId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    input,
                    securityUser
            );
            return ApiResponse.success(response, "領収書を登録しました。");
        } catch (IOException exception) {
            throw ReceiptFileException.serviceUnavailable(
                    "領収書ファイルを読み込めません。",
                    exception
            );
        }
    }

    @DeleteMapping
    public ApiResponse<Void> delete(
            @PathVariable Long applicationId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        receiptFileService.delete(applicationId, itemId, securityUser);
        return ApiResponse.success(null, "領収書を削除しました。");
    }

    @GetMapping("/content")
    public ResponseEntity<StreamingResponseBody> getContent(
            @PathVariable Long applicationId,
            @PathVariable Long itemId,
            @RequestParam(defaultValue = "inline") String disposition,
            @AuthenticationPrincipal SecurityUser securityUser
    ) {
        ReceiptContentDisposition parsedDisposition = parseDisposition(disposition);
        AuthorizedReceiptContent authorized = receiptFileService.openContent(
                applicationId,
                itemId,
                parsedDisposition,
                securityUser
        );
        ReceiptFileResponse metadata = authorized.metadata();
        ContentDisposition contentDisposition = (
                parsedDisposition == ReceiptContentDisposition.ATTACHMENT
                        ? ContentDisposition.attachment()
                        : ContentDisposition.inline()
        )
                .filename(metadata.getOriginalFileName(), StandardCharsets.UTF_8)
                .build();

        StreamingResponseBody body = output -> {
            try (InputStream input = authorized.content()) {
                input.transferTo(output);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.getContentType()))
                .contentLength(metadata.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(body);
    }

    private ReceiptContentDisposition parseDisposition(String disposition) {
        if ("inline".equalsIgnoreCase(disposition)) {
            return ReceiptContentDisposition.INLINE;
        }
        if ("attachment".equalsIgnoreCase(disposition)) {
            return ReceiptContentDisposition.ATTACHMENT;
        }
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "dispositionはinlineまたはattachmentを指定してください。"
        );
    }
}
