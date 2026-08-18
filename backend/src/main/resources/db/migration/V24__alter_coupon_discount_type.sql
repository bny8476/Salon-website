-- MySQL syntax: ALTER TABLE coupons MODIFY COLUMN discount_type VARCHAR(255) NOT NULL;
-- H2 syntax: ALTER TABLE coupons ALTER COLUMN discount_type VARCHAR(255) NOT NULL;
-- Use H2 syntax for compatibility with in-memory database
ALTER TABLE coupons ALTER COLUMN discount_type VARCHAR(255) NOT NULL;
