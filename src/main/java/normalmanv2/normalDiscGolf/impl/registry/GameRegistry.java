package normalmanv2.normalDiscGolf.impl.registry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class GameRegistry<K, V> {
    private final Map<K, V> entries = new HashMap<>();
    private final Map<K, V> view = Collections.unmodifiableMap(entries);
    private boolean frozen;

    public Map<K, V> getBackingMap() { return view; }
    public V get(K key) { return entries.get(key); }
    public boolean isFrozen() { return frozen; }
    public void freeze() { frozen = true; }
    public void unfreeze() { frozen = false; }

    public void register(K key, V value) {
        requireMutable();
        entries.put(Objects.requireNonNull(key), Objects.requireNonNull(value));
    }

    public void unregister(K key) {
        requireMutable();
        entries.remove(Objects.requireNonNull(key));
    }

    public void clearEntries() {
        requireMutable();
        entries.clear();
    }

    public void set(Map<K, V> values) {
        var copy = new HashMap<>(values);
        entries.clear();
        entries.putAll(copy);
    }

    private void requireMutable() {
        if (frozen) throw new UnsupportedOperationException("This registry is frozen");
    }
}
