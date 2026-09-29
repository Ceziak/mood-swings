package net.ceziak.mood_swings.task;

/**
 * Compatibility placeholder for projects upgraded from Mood Swings 1.0.x.
 *
 * <p>The old hard-coded condition engine was replaced by
 * {@link TaskConditionEvaluator} and JSON-backed task definitions in 1.1.0.
 * This empty class intentionally overwrites the obsolete source file when an
 * update is copied over an existing repository.</p>
 */
@Deprecated(forRemoval = true)
public final class CustomTaskConditions {
    private CustomTaskConditions() {
    }
}
