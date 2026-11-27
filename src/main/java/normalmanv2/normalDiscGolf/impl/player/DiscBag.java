package normalmanv2.normalDiscGolf.impl.player;

import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The credit balance, owned discs and ordered round bag are saved together. */
public record DiscBag(long credits, Set<DiscModel> unlocked, List<DiscModel> packed, int capacity) {
    public static final int STARTING_CAPACITY = 5;
    private static final List<DiscType> STARTER_ORDER = List.of(DiscType.PUTTER, DiscType.MID_RANGE, DiscType.DRIVER);

    public DiscBag {
        credits = Math.max(0, credits);
        unlocked = Set.copyOf(unlocked);
        capacity = capacity >= 9 ? 9 : capacity >= 7 ? 7 : STARTING_CAPACITY;
        List<DiscModel> valid = new ArrayList<>();
        for (DiscModel disc : packed) {
            if (valid.size() == capacity) break;
            if (disc != null && (disc.price() == 0 || unlocked.contains(disc)) && !valid.contains(disc)) valid.add(disc);
        }
        packed = valid.isEmpty() ? STARTER_ORDER.stream().map(DiscModel::starter).toList() : List.copyOf(valid);
    }

    public DiscBag() {
        this(0, Set.of(), List.of(), STARTING_CAPACITY);
    }

    public static DiscBag migrate(long credits, Set<DiscModel> unlocked, Map<DiscType, DiscModel> loadout) {
        return new DiscBag(credits, unlocked,
                STARTER_ORDER.stream().map(type -> loadout.getOrDefault(type, DiscModel.starter(type))).toList(),
                STARTING_CAPACITY);
    }

    public boolean owns(DiscModel model) {
        return model.price() == 0 || unlocked.contains(model);
    }

    public DiscBag unlock(DiscModel model) {
        if (owns(model) || credits < model.price()) throw new IllegalArgumentException("Disc cannot be unlocked");
        var next = new HashSet<>(unlocked);
        next.add(model);
        return new DiscBag(credits - model.price(), next, packed, capacity);
    }

    public DiscBag spend(long amount) {
        if (amount < 0 || amount > credits) throw new IllegalArgumentException("Insufficient credits");
        return new DiscBag(credits - amount, unlocked, packed, capacity);
    }

    public DiscBag pack(DiscModel model) {
        if (!owns(model) || packed.contains(model) || packed.size() >= capacity)
            throw new IllegalArgumentException("Disc cannot be packed");
        var next = new ArrayList<>(packed);
        next.add(model);
        return new DiscBag(credits, unlocked, next, capacity);
    }

    public DiscBag unpack(DiscModel model) {
        if (!packed.contains(model) || packed.size() == 1) throw new IllegalArgumentException("Disc cannot be removed");
        var next = new ArrayList<>(packed);
        next.remove(model);
        return new DiscBag(credits, unlocked, next, capacity);
    }

    public DiscBag moveToFront(DiscModel model) {
        if (!packed.contains(model)) throw new IllegalArgumentException("Disc must be packed");
        var next = new ArrayList<>(packed);
        next.remove(model);
        next.addFirst(model);
        return new DiscBag(credits, unlocked, next, capacity);
    }

    public int upgradeCost() {
        return switch (capacity) {
            case 5 -> 180;
            case 7 -> 320;
            default -> 0;
        };
    }

    public DiscBag upgrade() {
        int cost = upgradeCost();
        if (cost == 0 || credits < cost) throw new IllegalArgumentException("Bag cannot be upgraded");
        return new DiscBag(credits - cost, unlocked, packed, capacity + 2);
    }

    public DiscBag earn(long amount) {
        if (amount < 0) throw new IllegalArgumentException("Credit reward must be nonnegative");
        long balance = amount > Long.MAX_VALUE - credits ? Long.MAX_VALUE : credits + amount;
        return new DiscBag(balance, unlocked, packed, capacity);
    }
}
