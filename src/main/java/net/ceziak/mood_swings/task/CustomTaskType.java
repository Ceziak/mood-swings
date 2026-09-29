package net.ceziak.mood_swings.task;

/**
 * Legacy marker retained only so copying an update over a 1.0.x source tree
 * replaces the former hard-coded task enum. JSON task identifiers are now
 * represented by Minecraft identifiers and loaded by {@link TaskDefinitionRegistry}.
 */
@Deprecated(forRemoval = true)
public final class CustomTaskType {
    private CustomTaskType() {
    }
}
