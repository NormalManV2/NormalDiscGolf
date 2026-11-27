package normalmanv2.normalDiscGolf.impl.round;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;

final class BasketCelebration {
    private static final Particle.DustOptions CHAIN = new Particle.DustOptions(Color.fromRGB(212, 226, 233), .95f);
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 203, 64), 1.45f);

    private BasketCelebration() {}

    static void beat(Location basket, boolean ace, int tick) {
        var world = basket.getWorld();
        switch (tick) {
            case 0 -> {
                world.playSound(basket, Sound.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, .9f, 1.3f);
                world.spawnParticle(Particle.ELECTRIC_SPARK, basket, 12, .46, .32, .46, .03);
                ring(basket, .65, .35, CHAIN);
            }
            case 4 -> {
                world.playSound(basket, Sound.BLOCK_CHAIN_FALL, SoundCategory.PLAYERS, .65f, 1.55f);
                ring(basket, .95, .67, CHAIN);
            }
            case 8 -> {
                world.playSound(basket, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.PLAYERS, .36f, 1.22f);
                ring(basket, 1.3, .95, CHAIN);
                if (ace) {
                    world.spawnParticle(Particle.FIREWORK, basket.clone().add(0, 2.5, 0), 30, .2, .18, .2, .19);
                    ring(basket, 1.3, 2.45, GOLD);
                }
            }
            case 12, 16 -> {
                if (ace) ring(basket, 1.3 + (tick - 8) * .1, 2 + tick * .08, GOLD);
            }
        }
    }

    private static void ring(Location basket, double radius, double height, Particle.DustOptions color) {
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.TAU / 12;
            var point = basket.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            basket.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, color);
        }
    }
}
