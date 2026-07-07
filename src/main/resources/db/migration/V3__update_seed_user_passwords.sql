UPDATE users
SET password = '$2a$10$y.tRhZ0tZY4s/LKxR027bO4W9THCyw/Co.QiSMROVSSCLIDjC3cNi',
    updated_at = CURRENT_TIMESTAMP
WHERE email IN (
    'user@example.com',
    'approver@example.com',
    'admin@example.com'
);
