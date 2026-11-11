-- Stage 1 add-on to module5_skill_gap_schema.sql  (run AFTER its Steps 1-5)
--
-- Why: when AI is off, fails, or gives no text for a skill, the app writes a rule-based template
-- recommendation. Labelling that "AI_GENERATED" would mislead L&K reviewers (FRD Module 5:
-- AI suggestions must be reviewed against approved resources), so it gets its own value.
--
-- Run this BEFORE starting the app with the stage 1 code, otherwise saving a matching run
-- fails with "Data truncated for column 'recommendation_source'".

ALTER TABLE `learning_recommendations`
  MODIFY COLUMN `recommendation_source`
    enum('LK_REPOSITORY','AI_GENERATED','RULE_BASED','MANUAL') NOT NULL DEFAULT 'AI_GENERATED';
