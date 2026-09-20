CREATE INDEX IF NOT EXISTS idx_attendance_events_student_date_time ON attendance_events(student_id,school_date,checked_at DESC,id DESC);
