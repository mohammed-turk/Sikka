-- READ ONLY. Run on LOCAL MySQL database. Do not share hashed passwords or private user data.
SELECT DATABASE() AS selected_database, VERSION() AS mysql_version;
SELECT table_name, column_name, column_type, is_nullable, collation_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('account','visitor_profile','visit','visit_group','visit_member','visit_plan','plan_item','plan_approval','ride','place','payment_order','activity_session','visit_record','visit_meal_break') ORDER BY table_name,ordinal_position;
