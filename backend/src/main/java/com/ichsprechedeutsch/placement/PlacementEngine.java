package com.ichsprechedeutsch.placement;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.config.PlacementProperties;
import java.util.List;

/**
 * Adaptif yerlestirme algoritmasi (SPEC 4.1, K-003). Saf ve aciklanabilir:
 * o ana kadarki blok sonuclarindan bir sonraki adimi hesaplar.
 *
 * <p><b>Yukari asama</b> (baslangic seviyesinden): blok basarisi >= yukari
 * esik -> bir ust seviye (C1'de dur, sonuc C1). Gecme esigi <= basari <
 * yukari esik -> sonuc bu seviye. Basari < gecme esigi -> yukari cikarak
 * gelindiyse sonuc bir onceki seviye; baslangic seviyesindeysen asagi asama.
 *
 * <p><b>Asagi asama:</b> bir alt seviyeye in; gecme esigini gecen ilk seviye
 * sonuctur. Asagi inerken yukari cikilmaz. A1'de de gecilemezse sonuc
 * "A1'in altinda" (Level.A0).
 */
public final class PlacementEngine {

    /** Tamamlanmis bir blok. */
    public record Block(Level level, int correct, int total) {
        double ratio() {
            return total == 0 ? 0 : (double) correct / total;
        }
    }

    /**
     * Sonraki adim: ya bir blok daha (level) ya da sonuc (result).
     *
     * @param result A0 = "A1'in altinda"
     * @param reason sonuca nasil varildigi, kullaniciya gosterilebilir aciklama icin
     */
    public record Step(boolean finished, Level nextLevel, Level result, String reason) {
        static Step next(Level level) {
            return new Step(false, level, null, null);
        }

        static Step done(Level result, String reason) {
            return new Step(true, null, result, reason);
        }
    }

    private final PlacementProperties config;

    public PlacementEngine(PlacementProperties config) {
        if (config.blockSize() <= 0 || config.maxQuestions() < config.blockSize()) {
            throw new IllegalArgumentException("Yerlestirme ayarlari gecersiz");
        }
        this.config = config;
    }

    public Step decide(List<Block> blocks) {
        Level level = config.startLevel();
        boolean down = false;
        boolean cameUp = false;
        Level highestPassed = null;

        for (Block b : blocks) {
            if (b.level() != level) {
                throw new IllegalStateException("Blok sirasi algoritmayla uyusmuyor: beklenen "
                        + level + ", gelen " + b.level());
            }
            double r = b.ratio();
            boolean passed = r >= config.passThreshold();
            if (passed && (highestPassed == null || level.compareTo(highestPassed) > 0)) {
                highestPassed = level;
            }

            if (!down) {
                if (r >= config.upThreshold()) {
                    if (level == Level.C1) {
                        return Step.done(Level.C1, "C1 bloğunu yukarı eşikle geçtin; en üst seviye.");
                    }
                    level = level.next();
                    cameUp = true;
                } else if (passed) {
                    return Step.done(level, level + " bloğunu geçtin ama bir üst seviyeye çıkacak kadar değil.");
                } else if (cameUp) {
                    Level result = level.previous();
                    return Step.done(result, level + " bloğunu geçemedin; bir önceki seviye " + result + ".");
                } else if (level == Level.A1) {
                    return Step.done(Level.A0, "A1 bloğunu geçemedin.");
                } else {
                    down = true;
                    level = level.previous();
                }
            } else {
                if (passed) {
                    return Step.done(level, level + " bloğunu geçtin (aşağı aşama).");
                }
                if (level == Level.A1) {
                    return Step.done(Level.A0, "A1 bloğunu da geçemedin.");
                }
                level = level.previous();
            }
        }

        int asked = blocks.stream().mapToInt(Block::total).sum();
        if (asked + config.blockSize() > config.maxQuestions()) {
            // Soru siniri: o ana kadar gecilen en yuksek seviye (alt sinir niteliginde).
            Level result = highestPassed != null ? highestPassed : Level.A0;
            return Step.done(result, "Soru sınırına ulaşıldı; geçilen en yüksek seviye.");
        }
        return Step.next(level);
    }
}
