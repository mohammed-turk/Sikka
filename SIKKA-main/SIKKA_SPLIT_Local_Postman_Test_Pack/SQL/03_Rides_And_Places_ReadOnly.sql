-- READ ONLY, never modify live ride status just to make scenarios pass.
SELECT id,name,source_ride_id,status,wait_minutes FROM ride ORDER BY id;
SELECT id,name,kind FROM place ORDER BY id;
