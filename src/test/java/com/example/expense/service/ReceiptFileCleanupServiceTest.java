package com.example.expense.service;

import com.example.expense.common.enums.ReceiptFileState;
import com.example.expense.entity.ReceiptFile;
import com.example.expense.repository.ReceiptFileMapper;
import com.example.expense.storage.ReceiptStorage;
import com.example.expense.storage.ReceiptStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptFileCleanupServiceTest {

    @Mock
    private ReceiptFileMapper receiptFileMapper;

    @Mock
    private ReceiptStorage receiptStorage;

    private ReceiptFileCleanupService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptFileCleanupService(
                receiptFileMapper,
                receiptStorage,
                new TransactionTemplate(new TestTransactionManager())
        );
    }

    @Test
    void cleanupNonActive_正常系_PENDING_SCANをclaimしてobjectとmetadataを削除する() {
        ReceiptFile receipt = receipt(50L, ReceiptFileState.PENDING_SCAN);
        when(receiptFileMapper.findByIdForUpdate(50L)).thenReturn(receipt);
        when(receiptFileMapper.transitionState(
                50L,
                ReceiptFileState.PENDING_SCAN,
                ReceiptFileState.PENDING_DELETE,
                null
        )).thenReturn(1);
        when(receiptFileMapper.deleteByIdAndState(50L, ReceiptFileState.PENDING_DELETE)).thenReturn(1);

        assertThat(service.cleanupNonActive(50L, receipt.getStorageKey())).isTrue();

        verify(receiptStorage).delete(receipt.getStorageKey());
        verify(receiptFileMapper).deleteByIdAndState(50L, ReceiptFileState.PENDING_DELETE);
    }

    @Test
    void cleanupNonActive_正常系_ACTIVEは削除しない() {
        ReceiptFile receipt = receipt(50L, ReceiptFileState.ACTIVE);
        when(receiptFileMapper.findByIdForUpdate(50L)).thenReturn(receipt);

        assertThat(service.cleanupNonActive(50L, receipt.getStorageKey())).isFalse();

        verify(receiptStorage, never()).delete(receipt.getStorageKey());
        verify(receiptFileMapper, never()).deleteByIdAndState(50L, ReceiptFileState.PENDING_DELETE);
    }

    @Test
    void cleanupNonActive_異常系_object削除失敗時はmetadataを保持する() {
        ReceiptFile receipt = receipt(50L, ReceiptFileState.PENDING_DELETE);
        when(receiptFileMapper.findByIdForUpdate(50L)).thenReturn(receipt);
        org.mockito.Mockito.doThrow(new ReceiptStorageException("unavailable"))
                .when(receiptStorage).delete(receipt.getStorageKey());

        assertThatThrownBy(() -> service.cleanupNonActive(50L, receipt.getStorageKey()))
                .isInstanceOf(ReceiptStorageException.class);

        verify(receiptFileMapper, never()).deleteByIdAndState(50L, ReceiptFileState.PENDING_DELETE);
    }

    private ReceiptFile receipt(Long id, ReceiptFileState state) {
        ReceiptFile receipt = new ReceiptFile();
        receipt.setId(id);
        receipt.setStorageKey("receipts/2026/07/10/20/test");
        receipt.setState(state);
        return receipt;
    }
}
