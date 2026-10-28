-- READ ONLY. Replace visit ID with actual value from Postman.
SET @qa_visit_id=0; -- FILL ACTUAL visitId; 0 deliberately returns none.
SELECT id,name,visit_date,trip_type,group_mode,status,organizer_account_id,duration_minutes,free_slot_reserved FROM visit WHERE id=@qa_visit_id;
SELECT m.id,m.visit_id,m.name,m.rsvp_status,m.visitor_profile_id FROM visit_member m WHERE m.visit_id=@qa_visit_id ORDER BY m.id;
SELECT p.id,p.visit_id,p.plan_kind,p.is_active FROM visit_plan p WHERE p.visit_id=@qa_visit_id ORDER BY p.id;
