package com.ichsprechedeutsch.vocabulary;

/**
 * Kelime hangi yonde sorulacak?
 *
 * Yon rastgele secilmez, kademeli ilerler: once tanima (Almanca gorup
 * anlamini bilmek), sonra uretme (Turkce gorup Almancasini yazmak).
 * Uretme daha zordur; kelimeyi henuz taniyamayan birine sormak
 * cesaret kirici olur ve olcum de vermez.
 *
 * Isimlerde ARTIKEL ayri bir soru tipi olarak araya girer. Sebebi
 * dilbilimsel: Turkcede dilbilgisel cinsiyet yoktur. Bir Turk icin
 * "Bahnhof" kelimesini bilmek ile "der Bahnhof" demeyi bilmek iki ayri
 * beceridir; ikincisi digerinden bagimsiz olarak calisilmadan oturmaz.
 */
public final class ReviewDirection {

    /** Almanca gorulur, Turkcesi sorulur. Taniyabilme. */
    public static final String DE_TR = "DE_TR";

    /** Turkce gorulur, Almancasi sorulur. Uretebilme. */
    public static final String TR_DE = "TR_DE";

    /** Kelime gorulur, artikeli sorulur. */
    public static final String ARTICLE = "ARTICLE";

    private ReviewDirection() {
    }

    /**
     * @param repetition kelimenin kacinci basarili tekrari (0 = hic)
     * @param isNoun     isim mi (artikeli var mi)
     */
    public static String pick(int repetition, boolean isNoun) {
        if (repetition == 0) {
            return DE_TR;          // ilk karsilasma: once tani
        }
        if (isNoun && repetition % 3 == 1) {
            return ARTICLE;        // uc tekrarda bir artikel sorulur
        }
        return repetition % 2 == 0 ? DE_TR : TR_DE;
    }
}
