package de.igslandstuhl.database.attendance;

import de.igslandstuhl.database.plugins.Plugin;

public final class AttendancePlugin extends Plugin {
    private AttendancePluginConfig config;

    @Override protected void onLoad() {
        config = new AttendancePluginConfig(this);
        AttendanceRepository.initialize();
        AttendanceRequests.register(config);
    }
    @Override protected void onEnable() { getLogger().info("Attendance plugin started"); }
    @Override protected void onDisable() { getLogger().info("Attendance plugin stopped"); }
    @Override public AttendancePluginConfig getConfig() { return config; }
}
