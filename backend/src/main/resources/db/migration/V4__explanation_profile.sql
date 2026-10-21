-- Which profile (coding level and goal) an explanation was written for, e.g.
-- "NEW_TO_CODING/UNDERSTAND_APP". NULL for explanations made before profiles existed.
ALTER TABLE explanations ADD COLUMN profile VARCHAR(64);
