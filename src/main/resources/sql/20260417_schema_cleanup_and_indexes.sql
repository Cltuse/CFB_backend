-- Batch F: schema cleanup and index suggestions
-- Execute manually after validating data usage in the target environment.

-- 1) facility table legacy columns
-- These columns are not mapped by the current Facility entity and should be removed
-- only after confirming no remaining business dependency or historical backfill task.
-- ALTER TABLE facility
--   DROP COLUMN capacity,
--   DROP COLUMN damage_description,
--   DROP COLUMN damage_image_url,
--   DROP COLUMN equipment;

-- 2) reservation hot-path indexes
CREATE INDEX idx_reservation_created_at ON reservation (created_at);
CREATE INDEX idx_reservation_status_created_at ON reservation (status, created_at);
CREATE INDEX idx_reservation_checkin_status ON reservation (checkin_status);
CREATE INDEX idx_reservation_facility_time_status ON reservation (facility_id, start_time, end_time, status);
CREATE INDEX idx_reservation_user_status ON reservation (user_id, status);
CREATE INDEX idx_reservation_verification_code ON reservation (verification_code);

-- 3) violation record hot-path indexes
CREATE INDEX idx_violation_user_reported_time ON violation_record (user_id, reported_time);
CREATE INDEX idx_violation_status_reported_time ON violation_record (status, reported_time);
CREATE INDEX idx_violation_reported_by_reported_time ON violation_record (reported_by, reported_time);
CREATE INDEX idx_violation_reservation_type ON violation_record (reservation_id, violation_type);

-- 4) operation log search indexes
CREATE INDEX idx_operation_log_created_at ON operation_log (created_at);
CREATE INDEX idx_operation_log_operator_created_at ON operation_log (operator_id, created_at);
CREATE INDEX idx_operation_log_type_created_at ON operation_log (operation_type, created_at);

-- 5) other common list-page indexes
CREATE INDEX idx_feedback_user_created_at ON feedback (user_id, created_at);
CREATE INDEX idx_feedback_status_created_at ON feedback (status, created_at);
CREATE INDEX idx_notice_status_publish_time ON notice (status, publish_time);
CREATE INDEX idx_maintenance_facility_created_at ON maintenance (facility_id, created_at);
CREATE INDEX idx_maintenance_maintainer_created_at ON maintenance (maintainer_id, created_at);

-- 6) facility maintainer binding
-- Add this column before enabling the facility-to-maintainer binding in production.
-- ALTER TABLE facility
--   ADD COLUMN maintainer_id BIGINT NULL;
CREATE INDEX idx_facility_maintainer_id ON facility (maintainer_id);
