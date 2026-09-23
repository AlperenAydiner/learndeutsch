package com.ichsprechedeutsch.placement;

import com.ichsprechedeutsch.content.Question;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Bir blok icin soru secer (SPEC 4.1: tekrar cozumde ayni sorular
 * gelmesin). Once kullanicinin daha once hic gormedigi sorular, havuz
 * yetmezse gorulmusler. Bu oturumda sorulmus soru asla tekrar gelmez.
 * Rastgelelik parametredir; testte sabit tohumla calisir.
 */
public final class QuestionPicker {

    private QuestionPicker() {
    }

    public static List<Question> pick(List<Question> pool, int count, Set<String> seenBefore,
                                      Collection<String> askedThisSession, Random random) {
        List<Question> fresh = new ArrayList<>();
        List<Question> seen = new ArrayList<>();
        for (Question q : pool) {
            if (askedThisSession.contains(q.id())) {
                continue;
            }
            (seenBefore.contains(q.id()) ? seen : fresh).add(q);
        }
        Collections.shuffle(fresh, random);
        Collections.shuffle(seen, random);

        List<Question> out = new ArrayList<>(count);
        for (Question q : fresh) {
            if (out.size() == count) {
                break;
            }
            out.add(q);
        }
        for (Question q : seen) {
            if (out.size() == count) {
                break;
            }
            out.add(q);
        }
        return out;
    }
}
