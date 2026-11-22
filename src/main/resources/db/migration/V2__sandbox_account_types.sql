-- Controlled synthetic funding, not a real-money facility.
ALTER TABLE accounts ADD COLUMN account_type VARCHAR(12) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE accounts DROP CONSTRAINT ck_account_balance_nonnegative;
ALTER TABLE accounts ADD CONSTRAINT ck_account_type CHECK (account_type IN ('CUSTOMER','SYSTEM'));
ALTER TABLE accounts ADD CONSTRAINT ck_account_balance_nonnegative CHECK (account_type = 'SYSTEM' OR balance >= 0);
ALTER TABLE accounts ADD CONSTRAINT ck_system_account_unowned CHECK (account_type <> 'SYSTEM' OR owner_email IS NULL);
