INSERT INTO users (
    employee_code,
    name,
    email,
    password,
    role,
    department
) VALUES
(
    'E0001',
    '山田 太郎',
    'user@example.com',
    '$2a$10$7QJ8g5m9n3G/7zG0kKk.8O3tY8cE4y0L1rIGg1mPjM6f8pSxXGfYe',
    'USER',
    '営業部'
),
(
    'E0002',
    '佐藤 花子',
    'approver@example.com',
    '$2a$10$7QJ8g5m9n3G/7zG0kKk.8O3tY8cE4y0L1rIGg1mPjM6f8pSxXGfYe',
    'APPROVER',
    '経理部'
),
(
    'E0003',
    '管理 太郎',
    'admin@example.com',
    '$2a$10$7QJ8g5m9n3G/7zG0kKk.8O3tY8cE4y0L1rIGg1mPjM6f8pSxXGfYe',
    'ADMIN',
    '情報システム部'
);

INSERT INTO expense_applications (
    applicant_id,
    title,
    status,
    total_amount
) VALUES
(
    1,
    '大阪出張交通費',
    'DRAFT',
    0
);

INSERT INTO expense_items (
    expense_application_id,
    expense_date,
    category,
    amount,
    description,
    receipt_object_key
) VALUES
(
    1,
    DATE '2026-07-01',
    'TRANSPORTATION',
    13820,
    '東京駅から新大阪駅までの新幹線代',
    'receipts/2026/07/E0001/osaka-business-trip.pdf'
);

UPDATE expense_applications
SET total_amount = (
    SELECT COALESCE(SUM(amount), 0)
    FROM expense_items
    WHERE expense_application_id = expense_applications.id
)
WHERE id = 1;
