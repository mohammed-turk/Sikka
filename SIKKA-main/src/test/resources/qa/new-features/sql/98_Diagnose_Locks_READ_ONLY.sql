-- Optional diagnostic, may require MySQL PROCESS / performance_schema read permissions.
-- These queries do not kill connections or change application data.
SELECT trx_id,trx_state,trx_started,trx_mysql_thread_id,trx_query FROM information_schema.innodb_trx ORDER BY trx_started;
SELECT * FROM performance_schema.data_lock_waits;
SHOW FULL PROCESSLIST;
-- Confirm owner and pending work before using COMMIT/ROLLBACK in the blocking connection.
-- Never reuse connection 135 or KILL an unidentified application connection.
