-- MUTATION: LOCAL TEST DATABASE ONLY. Run after both registration requests succeed.
-- Verify expected email + role + ACTIVE and profiles before enabling. No password changes.
START TRANSACTION;
UPDATE account SET email_verified=1 WHERE email IN ('sikka.qa.leader@example.com','sikka.qa.member@example.com') AND role='USER' AND status='ACTIVE' AND email_verified=0 AND EXISTS (SELECT 1 FROM visitor_profile p WHERE p.account_id=account.id);
SELECT ROW_COUNT() AS changed_accounts;
SELECT a.id,a.email,a.role,a.status,a.email_verified,p.id AS visitor_profile_id FROM account a LEFT JOIN visitor_profile p ON p.account_id=a.id WHERE a.email IN ('sikka.qa.leader@example.com','sikka.qa.member@example.com');
-- Review before COMMIT (replace ROLLBACK only if correct).
ROLLBACK;
-- Once reviewed, execute UPDATE above again and COMMIT instead of ROLLBACK.
