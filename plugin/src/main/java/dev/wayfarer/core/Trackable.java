package dev.wayfarer.core;

/** Something a player can watch on their sidebar: what it is called and how far along they are. */
public record Trackable(String id, String title, long progress, long target) {
    /** Finished things leave the sidebar. */
    public boolean done() {
        return progress >= target;
    }
}
