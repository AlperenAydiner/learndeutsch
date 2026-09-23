package com.ichsprechedeutsch.srs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ichsprechedeutsch.config.TestConfig;
import com.ichsprechedeutsch.srs.SrsLadder.Next;
import com.ichsprechedeutsch.vocabulary.VocabularyPlanner;
import com.ichsprechedeutsch.vocabulary.VocabularyPlanner.DueCard;
import com.ichsprechedeutsch.vocabulary.VocabularyPlanner.Plan;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC 8.1 — merdiven ve gunun kelime oturumu. */
class SrsTest {

    private static final LocalDate BUGUN = LocalDate.of(2026, 3, 10);
    private final SrsLadder ladder = new SrsLadder(TestConfig.srs());

    @Test
    void ilkCalismaMerdiveninIlkAraligiylaGirer() {
        Next n = ladder.first(BUGUN);
        assertEquals(1, n.step());
        assertEquals(1, n.intervalDays());
        assertEquals(BUGUN.plusDays(1), n.due());
    }

    @Test
    void bildimBirUstAralik() {
        assertEquals(3, ladder.apply(1, Grade.BILDIM, BUGUN).intervalDays());
        assertEquals(7, ladder.apply(2, Grade.BILDIM, BUGUN).intervalDays());
        assertEquals(BUGUN.plusDays(7), ladder.apply(2, Grade.BILDIM, BUGUN).due());
    }

    @Test
    void zorlandimAyniAralikTekrar() {
        Next n = ladder.apply(4, Grade.ZORLANDIM, BUGUN);
        assertEquals(4, n.step());
        assertEquals(14, n.intervalDays());
    }

    @Test
    void bilemedimMerdivenBasina() {
        Next n = ladder.apply(6, Grade.BILEMEDIM, BUGUN);
        assertEquals(1, n.step());
        assertEquals(BUGUN.plusDays(1), n.due());
    }

    @Test
    void sonBasamaktanYukariCikilmaz() {
        int son = ladder.son();
        assertEquals(son, ladder.apply(son, Grade.BILDIM, BUGUN).step());
        assertEquals(120, ladder.intervalDays(son));
    }

    @Test
    void gucluKelimeEsikVeUstu() {
        assertFalse(ladder.strong(4), "14 gun esigin altinda");
        assertTrue(ladder.strong(5), "30 gun esik");
        assertTrue(ladder.strong(7));
    }

    @Test
    void gunlukYeniKelimeSureyeGore() {
        assertEquals(5, ladder.dailyNew(15));
        assertEquals(8, ladder.dailyNew(30));
        assertEquals(8, ladder.dailyNew(40), "ara deger bir alttaki esigi alir");
        assertEquals(15, ladder.dailyNew(90));
        assertEquals(15, ladder.dailyNew(180), "tablonun ustunde en yuksek deger");
        assertEquals(5, ladder.dailyNew(5), "en kucuk esigin altinda en kucuk deger");
    }

    // ---------- gunun oturumu ----------

    private static List<DueCard> kartlar(int adet, LocalDate due) {
        List<DueCard> out = new ArrayList<>();
        for (int i = 0; i < adet; i++) {
            out.add(new DueCard("w" + due.getDayOfMonth() + "-" + i, 2, due));
        }
        return out;
    }

    @Test
    void vadesiGelmeyenKelimeOturumaGirmez() {
        List<DueCard> due = new ArrayList<>(kartlar(2, BUGUN));
        due.addAll(kartlar(3, BUGUN.plusDays(2)));
        Plan p = VocabularyPlanner.plan(due, List.of("y1", "y2"), BUGUN, 5, 50);
        assertEquals(2, p.reviews().size());
        assertEquals(2, p.dueTotal());
    }

    @Test
    void birikmisTekrarEnGecikmistenBaslarVeGunlereYayilir() {
        List<DueCard> due = new ArrayList<>(kartlar(30, BUGUN));
        due.addAll(kartlar(30, BUGUN.minusDays(5)));
        Plan p = VocabularyPlanner.plan(due, List.of("y1"), BUGUN, 8, 50);

        assertEquals(60, p.dueTotal());
        assertEquals(50, p.reviews().size());
        assertEquals(10, p.postponed(), "kalan sonraki gunlere");
        assertTrue(p.reviews().stream().limit(30).allMatch(c -> c.due().equals(BUGUN.minusDays(5))),
                "once en gecikmisler");
    }

    @Test
    void tekrarSiniriDolduysaYeniKelimeVerilmez() {
        Plan p = VocabularyPlanner.plan(kartlar(60, BUGUN), List.of("y1", "y2"), BUGUN, 8, 50);
        assertTrue(p.newWords().isEmpty(), "yeni kelime tekrarin onune gecmez");
    }

    @Test
    void yerVarsaGunlukYeniKelimeEklenir() {
        Plan p = VocabularyPlanner.plan(kartlar(3, BUGUN), List.of("y1", "y2", "y3", "y4"), BUGUN, 2, 50);
        assertEquals(List.of("y1", "y2"), p.newWords());
        assertEquals(5, p.size());
    }
}
