CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    employee_code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    department VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_employee_code UNIQUE (employee_code),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'APPROVER', 'ADMIN'))
);

CREATE TABLE expense_applications (
    id BIGSERIAL PRIMARY KEY,
    applicant_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_amount NUMERIC(12, 0) NOT NULL DEFAULT 0,
    submitted_at TIMESTAMP NULL,
    approved_at TIMESTAMP NULL,
    approved_by BIGINT NULL,
    returned_at TIMESTAMP NULL,
    return_reason TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_expense_applications_applicant
        FOREIGN KEY (applicant_id) REFERENCES users (id),
    CONSTRAINT fk_expense_applications_approved_by
        FOREIGN KEY (approved_by) REFERENCES users (id),
    CONSTRAINT chk_expense_applications_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'RETURNED')),
    CONSTRAINT chk_expense_applications_total_amount
        CHECK (total_amount >= 0)
);

CREATE TABLE expense_items (
    id BIGSERIAL PRIMARY KEY,
    expense_application_id BIGINT NOT NULL,
    expense_date DATE NOT NULL,
    category VARCHAR(30) NOT NULL,
    amount NUMERIC(12, 0) NOT NULL,
    description VARCHAR(500) NOT NULL,
    receipt_object_key VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_expense_items_application
        FOREIGN KEY (expense_application_id) REFERENCES expense_applications (id) ON DELETE CASCADE,
    CONSTRAINT chk_expense_items_category
        CHECK (category IN ('TRANSPORTATION', 'MEAL', 'SUPPLIES', 'ACCOMMODATION', 'OTHER')),
    CONSTRAINT chk_expense_items_amount
        CHECK (amount > 0)
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    target_id BIGINT NOT NULL,
    detail TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_expense_applications_applicant_id
    ON expense_applications (applicant_id);

CREATE INDEX idx_expense_applications_status
    ON expense_applications (status);

CREATE INDEX idx_expense_applications_created_at
    ON expense_applications (created_at);

CREATE INDEX idx_expense_applications_applicant_status
    ON expense_applications (applicant_id, status);

CREATE INDEX idx_expense_items_application_id
    ON expense_items (expense_application_id);

CREATE INDEX idx_audit_logs_user_id
    ON audit_logs (user_id);

CREATE INDEX idx_audit_logs_created_at
    ON audit_logs (created_at);
