package normalmanv2.normalDiscGolf.impl.player;

import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Balance and loadout are saved as a single profile change. */
public record DiscBag(long credits, Set<DiscModel> unlocked, Map<DiscType, DiscModel> loadout) {
    public DiscBag {
        credits = Math.max(0, credits);
        unlocked = Set.copyOf(unlocked);
        loadout = Map.copyOf(loadout);
    }

    public DiscBag() {
        this(0, Set.of(), Map.of());
    }

    public boolean owns(DiscModel model) {
        return model.price() == 0 || unlocked.contains(model);
    }

    public DiscModel equipped(DiscType type) {
        DiscModel model = loadout.get(type);
        return model != null && model.type() == type && owns(model) ? model : DiscModel.starter(type);
    }

    public DiscBag unlock(DiscModel model) {
        if (owns(model) || credits < model.price()) throw new IllegalArgumentException("Disc cannot be unlocked");
        var next = EnumSet.noneOf(DiscModel.class);
        next.addAll(unlocked);
        next.add(model);
        return new DiscBag(credits - model.price(), next, loadout);
    }

    public DiscBag spend(long amount) {
        if (amount < 0 || amount > credits) throw new IllegalArgumentException("Insufficient credits");
        return new DiscBag(credits - amount, unlocked, loadout);
    }

    public DiscBag equip(DiscModel model) {
        if (!owns(model)) throw new IllegalArgumentException("Disc is locked");
        var next = new EnumMap<DiscType, DiscModel>(DiscType.class);
        next.putAll(loadout);
        next.put(model.type(), model);
        return new DiscBag(credits, unlocked, next);
    }

    public DiscBag earn(long amount) {
        if (amount < 0) throw new IllegalArgumentException("Credit reward must be nonnegative");
        long balance = amount > Long.MAX_VALUE - credits ? Long.MAX_VALUE : credits + amount;
        return new DiscBag(balance, unlocked, loadout);
    }
}
