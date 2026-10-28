-- For existing local MySQL databases only. Start the integrated application once
-- so Hibernate adds new columns, then run this script in the Sikka database.
-- New databases get the correct nullable relationship from the entity mapping.
ALTER TABLE visit_member MODIFY visitor_profile_id INT NULL;

-- Fill missing display snapshots without overwriting historical participant data.
UPDATE visit_member vm
JOIN visitor_profile vp ON vm.visitor_profile_id = vp.id
SET vm.name = vp.name,
    vm.birth_date = vp.birth_date,
    vm.height_cm = vp.height_cm,
    vm.weight_kg = vp.weight_kg,
    vm.gender = vp.gender
WHERE vm.name IS NULL AND vm.snapshot_at IS NULL;
