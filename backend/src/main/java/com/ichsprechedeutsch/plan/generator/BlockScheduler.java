package com.ichsprechedeutsch.plan.generator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Gunun bloklarini kullanicinin ayirabildigi sureye gore olcekler.
 *
 * Referans sablon 390 dakikaya (6,5 saat) gore yazilmistir. Gunde 2 saati
 * olan kullanici icin her blogu ayni oranda kucultmek yanlis olurdu:
 * 23 dakikalik Schreiben ile 15 dakikalik Sprechen kimseye bir sey
 * kazandirmaz.
 *
 * Bunun yerine 15 dakikanin altina dusen bloklar GUN ASIRI verilir:
 * blok m gunde bir gelir ama suresi m katidir. Haftalik toplam sure
 * korunur, gunluk blok sayisi makul kalir. Landing sayfasindaki
 * "cılız 7 blok yerine dolu 4 blok" sozu tam olarak budur.
 *
 * Secim bittikten sonra gunun toplami tam olarak vaat edilen sureye
 * oturtulur: kullaniciya "2 saat" dediysek gun 2 saat olmali.
 */
public class BlockScheduler {

    /** Referans sablonun dayandigi gunluk sure. */
    public static final int REFERENCE_DAY_MINUTES = 390;

    /** Bundan kisa blok anlamli calisma saglamaz. */
    public static final int MIN_BLOCK_MINUTES = 15;

    /** Sureler bu katlara yuvarlanir; 37 dakika kimsenin isine yaramaz. */
    private static final int STEP = 5;

    /**
     * @param blocks       birimin referans bloklari
     * @param dailyMinutes kullanicinin o gun icin ayirdigi sure
     * @param dayNumber    1'den baslayan gun numarasi (rotasyon icin)
     * @param unitId       gorevlerin baglandigi icerik birimi (null olabilir)
     */
    public List<PlannedTask> schedule(List<BlockSpec> blocks, int dailyMinutes,
                                      int dayNumber, UUID unitId) {
        if (blocks.isEmpty() || dailyMinutes < MIN_BLOCK_MINUTES) {
            return List.of();
        }

        List<BlockSpec> chosen = chooseBlocks(blocks, dailyMinutes, dayNumber);
        int[] minutes = apportion(chosen, dailyMinutes);

        List<PlannedTask> tasks = new ArrayList<>(chosen.size());
        for (int i = 0; i < chosen.size(); i++) {
            tasks.add(new PlannedTask(
                    chosen.get(i).taskType(), minutes[i], i + 1,
                    chosen.get(i).instructionTr(), unitId));
        }
        return tasks;
    }

    // ------------------------------------------------------------------

    /**
     * O gun hangi bloklarin gelecegini secer.
     *
     * Olcekli suresi esigin uzerinde olan blok her gun gelir. Altinda
     * kalan blok m gunde bir gelir; m, esigi asmaya yetecek en kucuk kat.
     * Blogun sirasi rotasyona karisir, boylece ayni gun hepsi birden
     * gelmez ve gunler birbirine benzemez.
     */
    private List<BlockSpec> chooseBlocks(List<BlockSpec> blocks, int dailyMinutes,
                                         int dayNumber) {
        double scale = (double) dailyMinutes / REFERENCE_DAY_MINUTES;
        List<BlockSpec> chosen = new ArrayList<>();

        for (int i = 0; i < blocks.size(); i++) {
            double scaled = Math.max(1.0, blocks.get(i).referenceMinutes() * scale);

            if (scaled >= MIN_BLOCK_MINUTES) {
                chosen.add(blocks.get(i));
                continue;
            }
            int every = (int) Math.ceil(MIN_BLOCK_MINUTES / scaled);
            if ((dayNumber + i) % every == 0) {
                chosen.add(blocks.get(i));
            }
        }

        // Hicbiri denk gelmediyse gun bos kalmasin: en uzun blogu ver.
        if (chosen.isEmpty()) {
            chosen.add(blocks.stream()
                    .max(Comparator.comparingInt(BlockSpec::referenceMinutes))
                    .orElseThrow());
        }

        return dropUntilFits(chosen, dailyMinutes);
    }

    /**
     * Her blok en az 15 dakika almali. Secilen blok sayisi gune sigmiyorsa
     * en kisa referansli olanlar dusurulur - onlar zaten rotasyondaki
     * bloklardir ve baska gunlerde gelecekler.
     */
    private List<BlockSpec> dropUntilFits(List<BlockSpec> chosen, int dailyMinutes) {
        int maxBlocks = Math.max(1, dailyMinutes / MIN_BLOCK_MINUTES);
        if (chosen.size() <= maxBlocks) {
            return chosen;
        }

        List<BlockSpec> kept = new ArrayList<>(chosen);
        while (kept.size() > maxBlocks) {
            BlockSpec smallest = kept.stream()
                    .min(Comparator.comparingInt(BlockSpec::referenceMinutes))
                    .orElseThrow();
            kept.remove(smallest);
        }
        return kept;
    }

    /**
     * Gunluk sureyi bloklara referans agirliklarina gore paylastirir.
     *
     * Toplam TAM olarak dailyMinutes olur: once oransal pay, sonra 15
     * dakika alt siniri, sonra kalan fark en uzun bloktan alinip verilir.
     */
    private int[] apportion(List<BlockSpec> blocks, int dailyMinutes) {
        int n = blocks.size();
        int[] minutes = new int[n];

        int totalReference = blocks.stream().mapToInt(BlockSpec::referenceMinutes).sum();

        for (int i = 0; i < n; i++) {
            double share = (double) dailyMinutes * blocks.get(i).referenceMinutes()
                    / totalReference;
            minutes[i] = Math.max(MIN_BLOCK_MINUTES, roundToStep(share));
        }

        balance(minutes, dailyMinutes);
        return minutes;
    }

    /** Yuvarlama ve alt sinir yuzunden olusan farki kapatir. */
    private void balance(int[] minutes, int target) {
        int total = sum(minutes);

        while (total > target) {
            int index = indexOfLongestAbove(minutes, MIN_BLOCK_MINUTES);
            if (index < 0) {
                return;   // hepsi alt sinirda; daha fazla kisilamaz
            }
            minutes[index] -= STEP;
            total -= STEP;
        }

        while (total < target) {
            int index = indexOfLongest(minutes);
            minutes[index] += STEP;
            total += STEP;
        }
    }

    private int indexOfLongest(int[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    private int indexOfLongestAbove(int[] values, int floor) {
        int best = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > floor && (best < 0 || values[i] > values[best])) {
                best = i;
            }
        }
        return best;
    }

    private int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    private int roundToStep(double minutes) {
        return (int) (Math.round(minutes / STEP) * STEP);
    }
}
