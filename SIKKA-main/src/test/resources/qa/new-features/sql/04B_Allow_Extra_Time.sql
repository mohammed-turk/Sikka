-- After Postman 04.12 only. Match this tag to Postman and the scenario SQL.
USE sikka;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @qa_tag=CONVERT('20261026-b' USING utf8mb4) COLLATE utf8mb4_unicode_ci;
UPDATE visit SET duration_minutes=120,planned_end_time=ADDTIME(planned_start_time,'02:00:00'),updated_at=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 3 HOUR)
WHERE CONVERT(name USING utf8mb4) COLLATE utf8mb4_unicode_ci=CONCAT('SIKKA-QA-NEW-',@qa_tag,'-04') COLLATE utf8mb4_unicode_ci
AND organizer_account_id=(SELECT id FROM account WHERE email='sikka.tester@example.test') AND status='IN_PROGRESS';
SELECT ROW_COUNT() AS updated_visits; -- must be 1
