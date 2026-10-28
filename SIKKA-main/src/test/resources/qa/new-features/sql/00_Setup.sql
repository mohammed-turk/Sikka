-- LOCAL TEST DATABASE ONLY. Use a fresh connection with autocommit ON.
-- Execute this complete file with the client's script runner (DELIMITER supported).
USE sikka;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS sikka_qa_new_ride_backup (
 id INT NOT NULL PRIMARY KEY, status VARCHAR(20), wait_minutes INT,
 wait_observed_at DATETIME(6), wait_source VARCHAR(255),
 ride_duration_minutes INT, entry_exit_buffer_minutes INT
);
DROP PROCEDURE IF EXISTS sikka_qa_prepare;
DELIMITER $$
CREATE PROCEDURE sikka_qa_prepare(IN p_tag VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, IN p_scenario INT)
BEGIN
 DECLARE v_now DATETIME;
 DECLARE v_name VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
 DECLARE v_visit INT DEFAULT NULL;
 DECLARE v_user INT DEFAULT NULL;
 DECLARE v_admin INT DEFAULT NULL;
 DECLARE v_r1 INT DEFAULT NULL;
 DECLARE v_r2 INT DEFAULT NULL;
 DECLARE v_r3 INT DEFAULT NULL;
 DECLARE v_duration INT;
 DECLARE v_group INT DEFAULT NULL;
 DECLARE v_lock INT DEFAULT 0;
 DECLARE v_old_timeout INT DEFAULT 50;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION
 BEGIN
  ROLLBACK;
  IF v_lock = 1 THEN DO RELEASE_LOCK(CONCAT('sikka_qa_',LEFT(SHA2(DATABASE(),256),40))); END IF;
  SET SESSION innodb_lock_wait_timeout=v_old_timeout;
  RESIGNAL;
 END;
 SET v_old_timeout=@@SESSION.innodb_lock_wait_timeout;
 IF @@SESSION.autocommit <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Use a fresh QA connection with autocommit ON'; END IF;
 IF p_tag IS NULL OR p_tag NOT REGEXP '^[a-zA-Z0-9-]{1,40}$' OR p_scenario IS NULL OR p_scenario NOT BETWEEN 1 AND 10 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Valid QA tag and scenario 1..10 required';
 END IF;
 SELECT GET_LOCK(CONCAT('sikka_qa_',LEFT(SHA2(DATABASE(),256),40)),0) INTO v_lock;
 IF v_lock IS NULL OR v_lock <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Another QA setup is running; do not run two fixtures concurrently'; END IF;
 SET SESSION innodb_lock_wait_timeout=5;
 SET v_now=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 3 HOUR);
 SET v_name=CONCAT('SIKKA-QA-NEW-',p_tag,'-',LPAD(p_scenario,2,'0'));
 SET v_duration=CASE p_scenario WHEN 1 THEN 180 WHEN 2 THEN 35 WHEN 3 THEN 1 WHEN 4 THEN 35 WHEN 5 THEN 120 WHEN 6 THEN 180 WHEN 7 THEN 180 WHEN 8 THEN 240 WHEN 9 THEN 35 ELSE 180 END;
 START TRANSACTION;
 SELECT MAX(id) INTO v_user FROM account WHERE email='sikka.tester@example.test' AND status='ACTIVE';
 SELECT MAX(id) INTO v_admin FROM account WHERE email='admin@sikka.test' AND status='ACTIVE';
 SELECT MAX(id) INTO v_r1 FROM ride WHERE source_ride_id='adrena-line';
 SELECT MAX(id) INTO v_r2 FROM ride WHERE source_ride_id='windrider';
 SELECT MAX(id) INTO v_r3 FROM ride WHERE source_ride_id='saw-mill-falls';
 IF v_user IS NULL OR v_admin IS NULL OR v_r1 IS NULL OR v_r2 IS NULL OR v_r3 IS NULL THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing QA accounts or source-linked rides; no fixture changes committed';
 END IF;
 -- Initial backup is retained, including when this procedure is rerun.
 INSERT INTO sikka_qa_new_ride_backup SELECT r.id,r.status,r.wait_minutes,r.wait_observed_at,r.wait_source,r.ride_duration_minutes,r.entry_exit_buffer_minutes FROM ride r LEFT JOIN sikka_qa_new_ride_backup b ON b.id=r.id WHERE b.id IS NULL;
 UPDATE account SET email_verified=1 WHERE id IN (v_user,v_admin);
 INSERT INTO visitor_profile (account_id,name,birth_date,height_cm,weight_kg,gender,thrill_preference,preferred_ride_types,disliked_ride_features,food_preferences,created_at,updated_at)
 SELECT a.id,a.name,'1998-01-01',170,70,'MALE','EXTREME',JSON_ARRAY(),JSON_ARRAY(),JSON_ARRAY(),v_now,v_now FROM account a LEFT JOIN visitor_profile p ON p.account_id=a.id WHERE a.id IN (v_user,v_admin) AND p.id IS NULL;
 -- Explicit local collation avoids connection-dependent @variable comparisons.
 SELECT MAX(id) INTO v_visit FROM visit WHERE CONVERT(name USING utf8mb4) COLLATE utf8mb4_unicode_ci=v_name AND organizer_account_id=v_user;
 IF v_visit IS NOT NULL AND (EXISTS(SELECT 1 FROM visit_plan WHERE visit_id=v_visit) OR EXISTS(SELECT 1 FROM plan_history WHERE visit_id=v_visit) OR EXISTS(SELECT 1 FROM visit WHERE id=v_visit AND (status<>'DRAFT' OR started_at IS NOT NULL))) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Scenario already used; choose a new runTag in SQL and Postman';
 END IF;
 IF v_visit IS NULL THEN
  IF p_scenario IN (9,10) THEN
   INSERT INTO visit_group (name,invitations_enabled,created_at,organizer_account_id) VALUES(v_name,0,v_now,v_user);
   SET v_group=LAST_INSERT_ID();
  END IF;
  INSERT INTO visit (name,visit_date,max_participants,trip_type,group_mode,goals,top_goal,prayer_enabled,status,members_locked,created_at,updated_at,organizer_account_id,duration_minutes,free_slot_reserved,group_id)
  VALUES(v_name,DATE(v_now),IF(p_scenario IN (9,10),2,1),IF(p_scenario IN (9,10),'FRIENDS','SOLO'),CASE WHEN p_scenario=9 THEN 'SPLIT' WHEN p_scenario=10 THEN 'TOGETHER' ELSE NULL END,JSON_ARRAY('MAX_RIDES'),'MAX_RIDES',0,'DRAFT',0,v_now,v_now,v_user,v_duration,1,v_group);
  SET v_visit=LAST_INSERT_ID();
 END IF;
 IF v_visit IS NULL OR v_visit<=0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fixture visit ID was not assigned'; END IF;
 INSERT INTO visit_member (name,birth_date,height_cm,weight_kg,gender,rsvp_status,visit_id,visitor_profile_id,answer_status,preferences,snapshot_at)
 SELECT p.name,'1998-01-01',170,70,'MALE','GOING',v_visit,p.id,'PENDING',JSON_OBJECT('dislikedFeatures',JSON_ARRAY()),v_now FROM visitor_profile p WHERE (p.account_id=v_user OR (p_scenario IN (9,10) AND p.account_id=v_admin)) AND NOT EXISTS(SELECT 1 FROM visit_member m WHERE m.visit_id=v_visit AND m.visitor_profile_id=p.id);
 IF (SELECT COUNT(*) FROM visit_member WHERE visit_id=v_visit AND rsvp_status='GOING')<>IF(p_scenario IN (9,10),2,1) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fixture member count mismatch; all changes rolled back'; END IF;
 -- Controlled catalog. All changed columns are backed up; geometry/safety rules remain untouched.
 UPDATE ride SET status='CLOSED' WHERE id IN (SELECT id FROM sikka_qa_new_ride_backup);
 UPDATE ride SET status='OPEN',wait_minutes=CASE id WHEN v_r1 THEN 15 WHEN v_r2 THEN 10 ELSE 20 END,ride_duration_minutes=CASE id WHEN v_r1 THEN 5 WHEN v_r2 THEN 4 ELSE 6 END,entry_exit_buffer_minutes=3,wait_observed_at=v_now,wait_source='SIKKA_LOCAL_QA_ESTIMATE' WHERE id IN (v_r1,v_r2,v_r3);
 IF p_scenario=6 THEN UPDATE ride SET status='CLOSED' WHERE id=v_r1; END IF;
 IF p_scenario=7 THEN UPDATE ride SET wait_minutes=NULL,wait_observed_at=NULL WHERE id=v_r1; END IF;
 COMMIT;
 DO RELEASE_LOCK(CONCAT('sikka_qa_',LEFT(SHA2(DATABASE(),256),40)));
 SET v_lock=0;
 SET SESSION innodb_lock_wait_timeout=v_old_timeout;
 SELECT id,name,status,duration_minutes,free_slot_reserved FROM visit WHERE id=v_visit;
 SELECT m.id AS visit_member_id,a.email FROM visit_member m JOIN visitor_profile p ON p.id=m.visitor_profile_id JOIN account a ON a.id=p.account_id WHERE m.visit_id=v_visit ORDER BY m.id;
END$$
DELIMITER ;
SELECT id,email,email_verified FROM account WHERE email IN ('admin@sikka.test','sikka.tester@example.test');
SELECT id,name,source_ride_id FROM ride WHERE source_ride_id IN ('adrena-line','windrider','saw-mill-falls');
-- Setup installed. Execute ONE scenario file next. Never KILL a hardcoded connection ID.
