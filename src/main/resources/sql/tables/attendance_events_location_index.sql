CREATE INDEX IF NOT EXISTS idx_attendance_events_location_date_time ON attendance_events(location_id,school_date,checked_at);
