package com.example.expense.controller;

import com.example.expense.common.GlobalExceptionHandler;
import com.example.expense.common.enums.RoleType;
import com.example.expense.config.SecurityConfig;
import com.example.expense.dto.response.ReceiptFileResponse;
import com.example.expense.entity.User;
import com.example.expense.security.RestAccessDeniedHandler;
import com.example.expense.security.RestAuthenticationEntryPoint;
import com.example.expense.security.SecurityUser;
import com.example.expense.service.AuthorizedReceiptContent;
import com.example.expense.service.ReceiptContentDisposition;
import com.example.expense.service.ReceiptFileException;
import com.example.expense.service.ReceiptFileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReceiptFileController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class ReceiptFileControllerTest {

    private static final String BASE_PATH = "/api/expense-applications/10/items/20/receipt";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReceiptFileService receiptFileService;

    @Test
    void getMetadata_正常系_storage情報を含まないmetadataを返す() throws Exception {
        when(receiptFileService.getMetadata(eq(10L), eq(20L), any())).thenReturn(response());

        mockMvc.perform(get(BASE_PATH).with(user(securityUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(50))
                .andExpect(jsonPath("$.data.originalFileName").value("領収書.pdf"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist());
    }

    @Test
    void uploadOrReplace_正常系_CSRF付きmultipartをServiceへ渡す() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                "%PDF-1.7\nreceipt".getBytes(StandardCharsets.US_ASCII)
        );
        when(receiptFileService.uploadOrReplace(
                eq(10L),
                eq(20L),
                eq("receipt.pdf"),
                eq("application/pdf"),
                any(),
                any()
        )).thenReturn(response());

        mockMvc.perform(multipart(BASE_PATH)
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(csrf())
                        .with(user(securityUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("領収書を登録しました。"))
                .andExpect(jsonPath("$.data.id").value(50));

        verify(receiptFileService).uploadOrReplace(
                eq(10L),
                eq(20L),
                eq("receipt.pdf"),
                eq("application/pdf"),
                any(),
                any()
        );
    }

    @Test
    void uploadOrReplace_異常系_CSRF不足を拒否する() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)
        );

        mockMvc.perform(multipart(BASE_PATH)
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user(securityUser())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        verifyNoInteractions(receiptFileService);
    }

    @Test
    void uploadOrReplace_異常系_filePart不足はINVALID_FILE() throws Exception {
        mockMvc.perform(multipart(BASE_PATH)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(csrf())
                        .with(user(securityUser())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));

        verifyNoInteractions(receiptFileService);
    }

    @Test
    void uploadOrReplace_異常系_malwareは専用codeを返す() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)
        );
        when(receiptFileService.uploadOrReplace(any(), any(), any(), any(), any(), any()))
                .thenThrow(ReceiptFileException.malwareDetected("危険なファイルを検出しました。"));

        mockMvc.perform(multipart(BASE_PATH)
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(csrf())
                        .with(user(securityUser())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MALWARE_DETECTED"))
                .andExpect(jsonPath("$.message").value("危険なファイルを検出しました。"));
    }

    @Test
    void uploadOrReplace_異常系_multipart以外は415を返す() throws Exception {
        mockMvc.perform(put(BASE_PATH)
                        .with(csrf())
                        .with(user(securityUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        verifyNoInteractions(receiptFileService);
    }

    @Test
    void uploadOrReplace_異常系_fileService停止は503専用codeを返す() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)
        );
        when(receiptFileService.uploadOrReplace(any(), any(), any(), any(), any(), any()))
                .thenThrow(ReceiptFileException.serviceUnavailable("領収書サービスを利用できません。", null));

        mockMvc.perform(multipart(BASE_PATH)
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(csrf())
                        .with(user(securityUser())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FILE_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("領収書サービスを利用できません。"));
    }

    @Test
    void getContent_正常系_inlineでsecurityHeaderとbinaryを返す() throws Exception {
        byte[] bytes = "%PDF-1.7\nreceipt".getBytes(StandardCharsets.US_ASCII);
        when(receiptFileService.openContent(
                eq(10L),
                eq(20L),
                eq(ReceiptContentDisposition.INLINE),
                any()
        )).thenReturn(new AuthorizedReceiptContent(
                response(),
                new ByteArrayInputStream(bytes),
                ReceiptContentDisposition.INLINE
        ));

        MvcResult started = mockMvc.perform(get(BASE_PATH + "/content")
                        .param("disposition", "inline")
                        .with(user(securityUser())))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''")
                ));
    }

    @Test
    void getContent_正常系_attachmentをServiceへ渡す() throws Exception {
        byte[] bytes = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        ReceiptFileResponse response = response();
        response.setSizeBytes((long) bytes.length);
        when(receiptFileService.openContent(
                eq(10L),
                eq(20L),
                eq(ReceiptContentDisposition.ATTACHMENT),
                any()
        )).thenReturn(new AuthorizedReceiptContent(
                response,
                new ByteArrayInputStream(bytes),
                ReceiptContentDisposition.ATTACHMENT
        ));

        MvcResult started = mockMvc.perform(get(BASE_PATH + "/content")
                        .param("disposition", "attachment")
                        .with(user(securityUser())))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")
                ));
    }

    @Test
    void delete_正常系_CSRF付きで削除する() throws Exception {
        mockMvc.perform(delete(BASE_PATH)
                        .with(csrf())
                        .with(user(securityUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("領収書を削除しました。"));

        verify(receiptFileService).delete(eq(10L), eq(20L), any());
    }

    @Test
    void getContent_異常系_不正dispositionはServiceを呼ばない() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/content")
                        .param("disposition", "open")
                        .with(user(securityUser())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(receiptFileService, never()).openContent(any(), any(), any(), any());
    }

    private ReceiptFileResponse response() {
        ReceiptFileResponse response = new ReceiptFileResponse();
        response.setId(50L);
        response.setOriginalFileName("領収書.pdf");
        response.setContentType("application/pdf");
        response.setSizeBytes(16L);
        response.setSha256Checksum("a".repeat(64));
        response.setUploadedAt(LocalDateTime.of(2026, 7, 23, 10, 0));
        response.setPreviewAvailable(true);
        return response;
    }

    private SecurityUser securityUser() {
        User user = new User();
        user.setId(1L);
        user.setEmployeeCode("E001");
        user.setName("テストユーザー");
        user.setEmail("user@example.com");
        user.setPassword("password");
        user.setRole(RoleType.USER);
        user.setEnabled(true);
        return new SecurityUser(user);
    }
}
