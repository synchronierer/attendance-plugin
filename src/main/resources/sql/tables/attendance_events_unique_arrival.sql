CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_arrival_day ON attendance_events(student_id,school_date) WHERE is_arrival=1;
