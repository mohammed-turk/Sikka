"""MySQL 8 regression for local QA SQL. Requires pymysql and an isolated MySQL server.
Run: SIKKA_QA_MYSQL_SOCKET=/path/to/test.sock python3 -m unittest discover -s src/test/qa -v
Never point this suite at a production server: it creates/drops only its own random database.
"""
import os
from pathlib import Path
import re
import unittest
import uuid

try:
    import pymysql
except ImportError:
    pymysql = None

SQL = Path(__file__).resolve().parents[1] / 'resources/qa/new-features/sql'


@unittest.skipUnless(pymysql and (os.environ.get('SIKKA_QA_MYSQL_SOCKET') or os.environ.get('SIKKA_QA_MYSQL_HOST')), 'Requires isolated MySQL connection and pymysql')
class NewFeaturesSqlRegressionTest(unittest.TestCase):
    def connect(self, database=None):
        return pymysql.connect(unix_socket=os.environ.get('SIKKA_QA_MYSQL_SOCKET'), host=os.environ.get('SIKKA_QA_MYSQL_HOST', 'localhost'), port=int(os.environ.get('SIKKA_QA_MYSQL_PORT', '3306')), user=os.environ.get('SIKKA_QA_MYSQL_USER', 'root'), password=os.environ.get('SIKKA_QA_MYSQL_PASSWORD', ''), database=database, autocommit=True, charset='utf8mb4')

    def script(self, connection, filename):
        delimiter = ';'
        pending = []
        with connection.cursor() as cursor:
            for line in (SQL / filename).read_text().splitlines():
                if line.strip().startswith('--'):
                    continue
                if line.strip().upper().startswith('USE SIKKA;'):
                    continue
                if line.strip().upper().startswith('DELIMITER '):
                    delimiter = line.strip().split()[1]
                    continue
                line = re.sub(r';\s*--.*$', ';', line)
                pending.append(line)
                if line.rstrip().endswith(delimiter):
                    statement = '\n'.join(pending).rstrip()[:-len(delimiter)].strip()
                    pending = []
                    if statement:
                        cursor.execute(statement)
                        while cursor.nextset():
                            pass
        self.assertFalse(''.join(pending).strip())

    def value(self, sql, args=None, connection=None):
        with (connection or self.db).cursor() as cursor:
            cursor.execute(sql, args)
            return cursor.fetchone()[0]

    def prepare(self, tag, scenario, connection=None):
        with (connection or self.db).cursor() as cursor:
            cursor.execute('CALL sikka_qa_prepare(%s,%s)', (tag, scenario))
            while cursor.nextset():
                pass

    def setUp(self):
        self.database = 'sikka_qa_regression_' + uuid.uuid4().hex
        self.root = self.connect()
        self.root.cursor().execute(f'CREATE DATABASE `{self.database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
        self.db = self.connect(self.database)
        statements = [
            "CREATE TABLE account (id INT PRIMARY KEY AUTO_INCREMENT,name VARCHAR(100),email VARCHAR(150),status VARCHAR(20),email_verified BOOLEAN)",
            "CREATE TABLE visitor_profile (id INT PRIMARY KEY AUTO_INCREMENT,account_id INT UNIQUE,name VARCHAR(100),birth_date DATE,height_cm DECIMAL(5,2),weight_kg DECIMAL(6,2),gender VARCHAR(10),thrill_preference VARCHAR(30),preferred_ride_types JSON,disliked_ride_features JSON,food_preferences JSON,created_at DATETIME,updated_at DATETIME)",
            "CREATE TABLE ride (id INT PRIMARY KEY AUTO_INCREMENT,name VARCHAR(150),source_ride_id VARCHAR(150),status VARCHAR(20),wait_minutes INT,wait_observed_at DATETIME(6),wait_source VARCHAR(255),ride_duration_minutes INT,entry_exit_buffer_minutes INT)",
            "CREATE TABLE visit_group (id INT PRIMARY KEY AUTO_INCREMENT,name VARCHAR(150),invitations_enabled BOOLEAN,created_at DATETIME,organizer_account_id INT)",
            "CREATE TABLE visit (id INT PRIMARY KEY AUTO_INCREMENT,name VARCHAR(150) COLLATE utf8mb4_unicode_ci,visit_date DATE,max_participants INT,trip_type VARCHAR(10),group_mode VARCHAR(10),goals JSON,top_goal VARCHAR(50),prayer_enabled BOOLEAN,status VARCHAR(20),members_locked BOOLEAN,created_at DATETIME,updated_at DATETIME,organizer_account_id INT,duration_minutes INT,free_slot_reserved BOOLEAN,group_id INT,started_at DATETIME,planned_start_time TIME,planned_end_time TIME)",
            "CREATE TABLE visit_member (id INT PRIMARY KEY AUTO_INCREMENT,name VARCHAR(100),birth_date DATE,height_cm DECIMAL(5,2),weight_kg DECIMAL(6,2),gender VARCHAR(10),rsvp_status VARCHAR(15),visit_id INT NOT NULL,visitor_profile_id INT,answer_status VARCHAR(15),preferences JSON,snapshot_at DATETIME,UNIQUE(visit_id,visitor_profile_id),FOREIGN KEY(visit_id) REFERENCES visit(id))",
            "CREATE TABLE visit_plan (id INT PRIMARY KEY AUTO_INCREMENT,visit_id INT)",
            "CREATE TABLE plan_history (id INT PRIMARY KEY AUTO_INCREMENT,visit_id INT)",
            "INSERT INTO account(name,email,status,email_verified) VALUES ('User','sikka.tester@example.test','ACTIVE',1),('Admin','admin@sikka.test','ACTIVE',1)",
            "INSERT INTO ride(name,source_ride_id,status,wait_minutes,ride_duration_minutes,entry_exit_buffer_minutes) VALUES ('Adrena','adrena-line','OPEN',4,3,1),('Wind','windrider','OPEN',5,4,1),('Saw','saw-mill-falls','OPEN',6,5,1)"
        ]
        with self.db.cursor() as cursor:
            for statement in statements:
                cursor.execute(statement)
        self.script(self.db, '00_Setup.sql')

    def tearDown(self):
        self.db.close()
        self.root.cursor().execute(f'DROP DATABASE `{self.database}`')
        self.root.close()

    def test_both_collations_and_missing_session_variable_create_exact_members(self):
        self.db.cursor().execute('SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci')
        self.db.cursor().execute('SET @qa_visit=NULL')
        self.prepare('fresh', 1)
        self.assertEqual(1, self.value('SELECT COUNT(*) FROM visit'))
        self.assertEqual(1, self.value('SELECT COUNT(*) FROM visit_member'))
        self.assertEqual(0, self.value('SELECT COUNT(*) FROM visit_member m LEFT JOIN visit v ON v.id=m.visit_id WHERE v.id IS NULL'))
        self.db.cursor().execute('SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci')
        self.prepare('other', 9)
        self.assertEqual(3, self.value('SELECT COUNT(*) FROM visit_member'))

    def test_setup_and_unused_draft_are_repeatable_but_generated_visit_is_protected(self):
        self.script(self.db, '00_Setup.sql')
        self.prepare('repeat', 1)
        self.prepare('repeat', 1)
        self.assertEqual(1, self.value('SELECT COUNT(*) FROM visit'))
        self.assertEqual(1, self.value('SELECT COUNT(*) FROM visit_member'))
        self.db.cursor().execute('INSERT INTO visit_plan(visit_id) SELECT id FROM visit')
        with self.assertRaisesRegex(pymysql.MySQLError, 'already used'):
            self.prepare('repeat', 1)
        self.assertEqual(1, self.value('SELECT COUNT(*) FROM visit_member'))

    def test_missing_catalog_does_not_create_partial_visit(self):
        self.db.cursor().execute("DELETE FROM ride WHERE source_ride_id='windrider'")
        with self.assertRaisesRegex(pymysql.MySQLError, 'Missing QA'):
            self.prepare('missing', 1)
        self.assertEqual(0, self.value('SELECT COUNT(*) FROM visit'))
        self.assertEqual(0, self.value('SELECT COUNT(*) FROM visitor_profile'))

    def test_lock_timeout_rolls_back_releases_guard_and_can_retry(self):
        blocker = self.connect(self.database)
        try:
            blocker.begin()
            blocker.cursor().execute("UPDATE ride SET status='CLOSED' WHERE id=1")
            with self.assertRaises(pymysql.MySQLError) as failure:
                self.prepare('locked', 1)
            self.assertEqual(1205, failure.exception.args[0])
            self.assertEqual(0, self.value('SELECT COUNT(*) FROM visit'))
            self.assertEqual(0, self.value('SELECT COUNT(*) FROM visitor_profile'))
            self.assertEqual(50, self.value('SELECT @@SESSION.innodb_lock_wait_timeout'))
            blocker.rollback()
            self.prepare('locked', 1)
            self.assertEqual(1, self.value('SELECT COUNT(*) FROM visit_member'))
        finally:
            blocker.rollback()
            blocker.close()

    def test_all_ten_scenarios_restore_original_ride_values(self):
        for scenario in range(1, 11):
            self.prepare('complete', scenario)
        self.assertEqual(10, self.value('SELECT COUNT(*) FROM visit'))
        self.assertEqual(12, self.value('SELECT COUNT(*) FROM visit_member'))
        self.assertEqual(3, self.value('SELECT COUNT(*) FROM sikka_qa_new_ride_backup'))
        self.script(self.db, '99_Restore_Rides.sql')
        self.assertEqual(4, self.value('SELECT wait_minutes FROM ride WHERE id=1'))
        self.assertEqual(3, self.value('SELECT ride_duration_minutes FROM ride WHERE id=1'))
        self.assertEqual(3, self.value("SELECT COUNT(*) FROM ride WHERE status='OPEN'"))
        self.assertEqual(10, self.value('SELECT COUNT(*) FROM visit'))


if __name__ == '__main__':
    unittest.main()
