-- "What were you trying to do?" can now be many paragraphs, not one line.
ALTER TABLE sessions ALTER COLUMN reason SET DATA TYPE VARCHAR(20000);
