package normalmanv2.normalDiscGolf.impl.player;

import normalmanv2.normalDiscGolf.impl.cosmetic.ScoreDecal;

import java.util.EnumSet;
import java.util.Set;

public record DecalLocker(Set<ScoreDecal> unlocked, ScoreDecal equipped) {
    public DecalLocker {
        unlocked = Set.copyOf(unlocked);
        if (equipped == null || !unlocked.contains(equipped)) equipped = ScoreDecal.NONE;
    }

    public DecalLocker() {
        this(Set.of(), ScoreDecal.NONE);
    }

    public boolean owns(ScoreDecal decal) {
        return decal == ScoreDecal.NONE || unlocked.contains(decal);
    }

    public DecalLocker unlock(ScoreDecal decal) {
        if (owns(decal)) throw new IllegalArgumentException("Decal already owned");
        var next = EnumSet.noneOf(ScoreDecal.class);
        next.addAll(unlocked);
        next.add(decal);
        return new DecalLocker(next, equipped);
    }

    public DecalLocker equip(ScoreDecal decal) {
        if (!owns(decal)) throw new IllegalArgumentException("Decal is locked");
        return new DecalLocker(unlocked, decal);
    }
}
