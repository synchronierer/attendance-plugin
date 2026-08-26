CREATE TABLE IF NOT EXISTS attendance_teacher_preferences (principal TEXT PRIMARY KEY, location_code TEXT, class_ids TEXT NOT NULL DEFAULT '[]', updated_at TEXT NOT NULL);
