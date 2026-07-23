CREATE TABLE receipt_files (
    id BIGSERIAL PRIMARY KEY,
    expense_item_id BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NULL,
    size_bytes BIGINT NULL,
    sha256_checksum CHAR(64) NULL,
    state VARCHAR(30) NOT NULL,
    uploaded_by BIGINT NOT NULL,
    activated_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receipt_files_storage_key
        UNIQUE (storage_key),
    CONSTRAINT fk_receipt_files_expense_item
        FOREIGN KEY (expense_item_id) REFERENCES expense_items (id),
    CONSTRAINT fk_receipt_files_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT chk_receipt_files_content_type
        CHECK (
            content_type IS NULL
            OR content_type IN ('image/jpeg', 'image/png', 'application/pdf')
        ),
    CONSTRAINT chk_receipt_files_size
        CHECK (
            size_bytes IS NULL
            OR size_bytes BETWEEN 1 AND 10485760
        ),
    CONSTRAINT chk_receipt_files_checksum
        CHECK (
            sha256_checksum IS NULL
            OR sha256_checksum ~ '^[0-9a-f]{64}$'
        ),
    CONSTRAINT chk_receipt_files_state
        CHECK (
            state IN (
                'UPLOADING',
                'PENDING_SCAN',
                'ACTIVE',
                'REJECTED',
                'PENDING_DELETE'
            )
        ),
    CONSTRAINT chk_receipt_files_complete_metadata
        CHECK (
            state NOT IN ('PENDING_SCAN', 'ACTIVE')
            OR (
                content_type IS NOT NULL
                AND size_bytes IS NOT NULL
                AND sha256_checksum IS NOT NULL
            )
        ),
    CONSTRAINT chk_receipt_files_active_timestamp
        CHECK (
            state <> 'ACTIVE'
            OR activated_at IS NOT NULL
        )
);

CREATE UNIQUE INDEX uk_receipt_files_active_expense_item
    ON receipt_files (expense_item_id)
    WHERE state = 'ACTIVE';

CREATE INDEX idx_receipt_files_expense_item_id
    ON receipt_files (expense_item_id);

CREATE INDEX idx_receipt_files_state_updated_at
    ON receipt_files (state, updated_at);
