package com.ichsprechedeutsch.article;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ichsprechedeutsch.article.ArticleDrill.History;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Artikel alistirmasinda soru sirasi. */
class ArticleDrillTest {

    private static final List<String> ADAYLAR = List.of("w1", "w2", "w3", "w4", "w5");

    private static Map<String, History> gecmis(Object... ciftler) {
        Map<String, History> m = new LinkedHashMap<>();
        for (int i = 0; i < ciftler.length; i += 3) {
            m.put((String) ciftler[i], new History((int) ciftler[i + 1], (int) ciftler[i + 2]));
        }
        return m;
    }

    @Test
    void yanlisYapilanlarOnceGelirEnCokYanlistanBaslayarak() {
        Map<String, History> h = gecmis("w1", 4, 4, "w2", 5, 1, "w3", 3, 2);
        List<String> secim = ArticleDrill.pick(ADAYLAR, h, 3, new Random(1));
        assertEquals("w2", secim.get(0), "4 yanlis");
        assertEquals("w3", secim.get(1), "1 yanlis");
        assertTrue(secim.get(2).equals("w4") || secim.get(2).equals("w5"), "sonra hic sorulmamislar");
    }

    @Test
    void dogruBilinenlerEnSona() {
        Map<String, History> h = gecmis("w1", 4, 4, "w2", 2, 2, "w3", 1, 1, "w4", 3, 3, "w5", 2, 2);
        List<String> secim = ArticleDrill.pick(ADAYLAR, h, 5, new Random(7));
        assertEquals(5, secim.size(), "aday kalmadiginda bilinenler de sorulur");
    }

    @Test
    void adetSiniriUygulanir() {
        assertEquals(2, ArticleDrill.pick(ADAYLAR, Map.of(), 2, new Random(3)).size());
        assertTrue(ArticleDrill.pick(ADAYLAR, Map.of(), 0, new Random(3)).isEmpty());
        assertEquals(5, ArticleDrill.pick(ADAYLAR, Map.of(), 99, new Random(3)).size());
    }

    @Test
    void ayniTohumAyniSirayiVerir() {
        assertEquals(ArticleDrill.pick(ADAYLAR, Map.of(), 5, new Random(42)),
                ArticleDrill.pick(ADAYLAR, Map.of(), 5, new Random(42)));
    }
}
