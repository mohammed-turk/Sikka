-- Run 00_Setup.sql once, then this complete file before Postman folder 07.
-- Match runTag in Postman. New tag required after any generation attempt.
USE sikka;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
CALL sikka_qa_prepare('20261026-b',7);
