package de.igslandstuhl.database.attendance;

import de.igslandstuhl.database.plugins.Plugin;

public class AttendancePlugin extends Plugin {

    private AttendancePluginConfig config;

    @Override
    protected void onLoad() {
        config = new AttendancePluginConfig(this);
        AttendanceRequests.register();
        getLogger().info("Attendance plugin loaded");
    }

    @Override
    protected void onEnable() {
        getLogger().info("Attendance plugin enabled");
    }

    @Override
    protected void onDisable() {
        getLogger().info("Attendance plugin disabled");
    }

    @Override
    public AttendancePluginConfig getConfig() {
        return config;
    }
}
