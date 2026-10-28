-- READ ONLY; do not delete accounts or related visit records automatically.
SELECT a.id,a.name,a.email,a.email_verified,p.id AS profile_id FROM account a LEFT JOIN visitor_profile p ON p.account_id=a.id WHERE a.email IN ('sikka.qa.leader@example.com','sikka.qa.member@example.com');
-- For visit cleanup: use application's DELETE /api/v1/visit/delete/{visitId} explicitly as organizer after review.
