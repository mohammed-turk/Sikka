-- READ ONLY. Diagnose genuine planning entitlement. Never forge payment_order or free_slot_reserved.
SET @qa_visit_id=0; -- fill visitId
SELECT v.id,v.name,v.status,v.free_slot_reserved FROM visit v WHERE v.id=@qa_visit_id;
SELECT o.id,o.visit_id,o.status,o.amount_sar,o.verified_at FROM payment_order o WHERE o.visit_id=@qa_visit_id ORDER BY o.id;
