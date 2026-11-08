package com.mahghuuuls.critworks.integration.crafttweaker;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which script mistakes were already reported in this game session. CraftTweaker shows every error line in
 * the chat of the players online, so a mistake in a handler that runs every second must be reported once, not every
 * second. A mistake is the same when the call, the script file and line, and the message are the same.
 */
final class ScriptMistakes {

    private final Set<String> reported = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /** True the first time this call, script place, and message come up; false for every repeat. */
    boolean firstTime(String call, String where, String message) {
        return reported.add(call + '\u0000' + where + '\u0000' + message);
    }
}
