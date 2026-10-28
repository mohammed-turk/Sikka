-- Fresh autocommit connection. Setup must already exist. Restores only backed-up ride fields.
USE sikka;
DROP PROCEDURE IF EXISTS sikka_qa_restore;
DELIMITER $$
CREATE PROCEDURE sikka_qa_restore()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 UPDATE ride r JOIN sikka_qa_new_ride_backup b ON b.id=r.id SET r.status=b.status,r.wait_minutes=b.wait_minutes,r.wait_observed_at=b.wait_observed_at,r.wait_source=b.wait_source,r.ride_duration_minutes=b.ride_duration_minutes,r.entry_exit_buffer_minutes=b.entry_exit_buffer_minutes;
 COMMIT;
END$$
DELIMITER ;
CALL sikka_qa_restore();
-- Backup and test visits are retained. No history, records or payment orders are deleted.
