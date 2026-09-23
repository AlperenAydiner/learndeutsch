"""
Tek seferlik donusturucu: eski CSV icerigini SPEC 9.1'deki seviye bazli
JSON yapisina cevirir (Faz 1, K-004).

    python -X utf8 tools/csv_to_json.py

Uretilen her oge verified:false tasir (SPEC 10.2); verified:true'yu
yalnizca kullanici isaretler. Calistiktan sonra CSV'ler silinir; bu betik
yalnizca gecmis kaydi olarak durur.
"""
import csv
import io
import json
import re
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "content"

# Eski hata kategorileri -> gramer agaci dugumleri. Seviye ve core bayragi
# burada verilir; Faz 3b agaci genisletir.
TOPIC_LEVEL = {
    "SEIN_HABEN": ("A1", True), "VERBKONJUGATION": ("A1", True),
    "ARTIKEL": ("A1", True), "PLURAL": ("A1", True), "NEGATION": ("A1", True),
    "W_FRAGEN": ("A1", True), "WORTSTELLUNG": ("A1", True),
    "AKKUSATIV": ("A1", True), "POSSESSIV": ("A1", True),
    "MODALVERBEN": ("A1", True), "TRENNBARE_VERBEN": ("A1", True),
    "ZEITANGABEN": ("A1", False), "IMPERATIV": ("A1", False),
    "DATIV": ("A1", True), "PERFEKT": ("A1", True),
    "PRAETERITUM": ("A2", True), "WECHSELPRAEPOSITIONEN": ("A2", True),
    "NEBENSATZ": ("A2", True), "KOMPARATIV": ("A2", True),
    "REFLEXIV": ("A2", False), "ADJEKTIVDEKLINATION": ("A2", True),
    "KONJUNKTIV2": ("A2", False),
}

# B1-C1 icin agac iskeleti (Faz 1: yerlestirme sorularinin etiketleri).
EXTRA_TOPICS = [
    ("RELATIVSATZ", "Relativsatz (ilgi cümlesi)", "B1", True),
    ("PASSIV", "Passiv (edilgen)", "B1", True),
    ("INFINITIV_ZU", "Infinitiv mit zu", "B1", True),
    ("GENITIV", "Genitiv (-in hâli)", "B1", False),
    ("KONJUNKTIV2_VERGANGENHEIT", "Konjunktiv II geçmiş zaman", "B1", False),
    ("KONNEKTOREN", "Bağlaçlar (obwohl, trotzdem, deshalb)", "B1", True),
    ("PLUSQUAMPERFEKT", "Plusquamperfekt", "B1", False),
    ("VERBEN_MIT_PRAEPOSITION", "Edatlı fiiller (warten auf …)", "B1", True),
    ("PARTIZIP_ATTRIBUT", "Sıfat-fiil (Partizip) öbekleri", "B2", True),
    ("NOMEN_VERB_VERBINDUNGEN", "İsim-fiil birleşimleri", "B2", True),
    ("ZWEITEILIGE_KONNEKTOREN", "İki parçalı bağlaçlar", "B2", False),
    ("PRAEPOSITIONEN_GENITIV", "Genitiv alan edatlar (trotz, wegen)", "B2", False),
    ("ALS_OB", "als ob / als wenn", "B2", False),
    ("NOMINALISIERUNG", "İsimleştirme", "C1", True),
]

NON_GRAMMAR = {
    "WORTSCHATZ": ("VOCABULARY", "Kelime bilgisi"),
    "LESEVERSTEHEN": ("READING", "Okuduğunu anlama"),
    "HOERVERSTEHEN": ("LISTENING", "Dinlediğini anlama"),
}

PLACEMENT_KIND = {"WORTSCHATZ": "VOCABULARY", "LESEVERSTEHEN": "READING"}


def read(name):
    with io.open(SRC / name, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def slug(text):
    t = text.lower().replace("ß", "ss").replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
    t = unicodedata.normalize("NFKD", t).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "-", t).strip("-")


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"yazildi {path.relative_to(ROOT)}  ({len(data) if isinstance(data, list) else 'nesne'})")


def grammar_tree():
    names = {r["code"]: r["name_tr"] for r in read("mistake_categories.csv")}
    nodes = []
    for code, (level, core) in TOPIC_LEVEL.items():
        nodes.append({"id": code, "kind": "GRAMMAR", "level": level, "titleTr": names[code],
                      "core": core, "estimatedMinutes": 20, "verified": False})
    for code, title, level, core in EXTRA_TOPICS:
        nodes.append({"id": code, "kind": "GRAMMAR", "level": level, "titleTr": title,
                      "core": core, "estimatedMinutes": 20, "verified": False})
    for code, (kind, title) in NON_GRAMMAR.items():
        nodes.append({"id": code, "kind": kind, "level": None, "titleTr": title,
                      "core": False, "estimatedMinutes": 0, "verified": False})
    return nodes


def words():
    by_level = defaultdict(list)
    seen = defaultdict(int)
    for fname in ("words_a1.csv", "words_a2.csv"):
        for r in read(fname):
            level = r["level"]
            base = f"w-{level.lower()}-{slug(r['lemma'])}"
            seen[base] += 1
            wid = base if seen[base] == 1 else f"{base}-{seen[base]}"
            by_level[level].append({
                "id": wid,
                "lemma": r["lemma"],
                "article": r["article"].lower() or None,
                "plural": r["plural_form"] or None,
                "partOfSpeech": r["word_type"],
                "meaningTr": r["meaning_tr"],
                "level": level,
                "theme": r["theme"] or None,
                "exampleDe": r["example_de"],
                "exampleTr": r["example_tr"],
                "estimatedMinutes": 1,
                "verified": False,
            })
    return by_level


def questions():
    options = defaultdict(list)
    for o in read("question_options.csv"):
        options[o["question_code"]].append(o)

    learning, placement = defaultdict(list), defaultdict(list)
    for q in read("questions.csv"):
        opts = sorted(options[q["code"]], key=lambda o: int(o["order_no"]))
        correct = [o["option_text"] for o in opts if o["is_correct"] == "true"]
        assert len(correct) == 1, q["code"]
        tag = q["mistake_category_code"]
        item = {
            "id": q["code"].lower(),
            "level": q["level"],
            "type": "MULTIPLE_CHOICE",
            "prompt": q["prompt_de"],
            "options": [o["option_text"] for o in opts],
            "answer": correct[0],
            "acceptedAnswers": [],
            "tags": [tag],
            "explanationTr": q["explanation_tr"] or None,
            "estimatedMinutes": 1,
            "verified": False,
        }
        if q["test_code"] == "PLACEMENT":
            item["placementKind"] = PLACEMENT_KIND.get(tag, "GRAMMAR")
            placement[q["level"]].append(item)
        else:
            learning[q["level"]].append(item)
    return learning, placement


def main():
    out = ROOT / "content"
    write(out / "grammar-tree.json", grammar_tree())
    for level, items in words().items():
        write(out / level.lower() / "words.json", items)
    learning, placement = questions()
    for level, items in learning.items():
        write(out / level.lower() / "questions.json", items)
    for level, items in placement.items():
        write(out / level.lower() / "placement.json", items)


if __name__ == "__main__":
    main()
