package com.mahghuuuls.critworks.combat;

import java.util.HashMap;
import java.util.Map;

/**
 * The linking rule between a bow release and its arrow. A release is recorded per shooter with the world
 * time; the next arrow of that same shooter in that same tick takes it. Another shooter's arrow, an
 * arrow of a later tick, or a second arrow of the same release finds nothing.
 */
final class PendingShots<K, V> {

    private final Map<K, Entry<V>> shots = new HashMap<K, Entry<V>>();

    void record(K shooter, V shot, long worldTime) {
        shots.put(shooter, new Entry<V>(shot, worldTime));
    }

    /** The shooter's release from this tick, removed so it links to one arrow only; null when there is none. */
    V take(K shooter, long worldTime) {
        Entry<V> entry = shots.get(shooter);
        if (entry == null) {
            return null;
        }
        shots.remove(shooter);
        return entry.worldTime == worldTime ? entry.shot : null;
    }

    boolean isEmpty() {
        return shots.isEmpty();
    }

    void clear() {
        shots.clear();
    }

    private static final class Entry<V> {
        final V shot;
        final long worldTime;

        Entry(V shot, long worldTime) {
            this.shot = shot;
            this.worldTime = worldTime;
        }
    }
}
