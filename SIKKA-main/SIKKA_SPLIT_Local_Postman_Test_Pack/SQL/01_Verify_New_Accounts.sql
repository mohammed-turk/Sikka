-- READ ONLY. After POST /account/register, verify exactly 2 test accounts + visitor profiles.
SELECT a.id AS account_id,a.name,a.email,a.role,a.status,a.email_verified,p.id AS visitor_profile_id,p.name AS profile_name FROM account a LEFT JOIN visitor_profile p ON p.account_id=a.id WHERE a.email IN ('sikka.qa.leader@example.com','sikka.qa.member@example.com') ORDER BY a.id;
