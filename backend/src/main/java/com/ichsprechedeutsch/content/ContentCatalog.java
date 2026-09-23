package com.ichsprechedeutsch.content;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.Skill;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Icerik katalogu: acilista classpath:content/ altindaki JSON dosyalarini
 * okur ve bellekte salt okunur tutar. Icerik veritabanina yazilmaz (K-004,
 * SPEC 9.2); kullanici durumu icerige yalniz id ile baglanir.
 */
@Component
public class ContentCatalog {

    private static final Logger log = LoggerFactory.getLogger(ContentCatalog.class);

    private final Map<String, GrammarTopic> topics = new LinkedHashMap<>();
    private final Map<String, Word> words = new LinkedHashMap<>();
    private final Map<String, Question> questions = new LinkedHashMap<>();
    private final Map<String, GrammarLesson> lessons = new LinkedHashMap<>();
    private final Map<String, ReadingText> readings = new LinkedHashMap<>();
    private final Map<String, ListeningItem> listenings = new LinkedHashMap<>();
    private final Map<String, WritingTask> writings = new LinkedHashMap<>();
    private final Map<String, SpeakingTask> speakings = new LinkedHashMap<>();
    private final Map<String, CanDo> canDos = new LinkedHashMap<>();
    private final Map<String, ExamLink> examLinks = new LinkedHashMap<>();
    private final Map<Level, List<Question>> placementByLevel = new EnumMap<>(Level.class);

    public ContentCatalog(ObjectMapper mapper) {
        try {
            load(mapper);
        } catch (IOException e) {
            throw new UncheckedIOException("Icerik okunamadi", e);
        }
        log.info("Icerik katalogu: {} konu ({} ders), {} kelime, {} soru ({} yerlestirme), "
                        + "{} okuma, {} dinleme, {} yazma, {} konusma, {} can-do",
                topics.size(), lessons.size(), words.size(), questions.size(),
                placementByLevel.values().stream().mapToInt(List::size).sum(),
                readings.size(), listenings.size(), writings.size(), speakings.size(), canDos.size());
    }

    private void load(ObjectMapper mapper) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (Resource r : resolver.getResources("classpath*:content/**/*.json")) {
            String name = r.getFilename();
            if (name == null) {
                continue;
            }
            try (InputStream in = r.getInputStream()) {
                switch (name) {
                    case "grammar-tree.json" -> read(mapper, in, new TypeReference<List<GrammarTopic>>() {})
                            .forEach(t -> topics.put(t.id(), t));
                    case "words.json" -> read(mapper, in, new TypeReference<List<Word>>() {})
                            .forEach(w -> words.put(w.id(), w));
                    case "questions.json" -> read(mapper, in, new TypeReference<List<Question>>() {})
                            .forEach(q -> questions.put(q.id(), q));
                    case "grammar.json" -> read(mapper, in, new TypeReference<List<GrammarLesson>>() {})
                            .forEach(l -> lessons.put(l.topicId(), l));
                    case "reading.json" -> read(mapper, in, new TypeReference<List<ReadingText>>() {})
                            .forEach(r2 -> readings.put(r2.id(), r2));
                    case "listening.json" -> read(mapper, in, new TypeReference<List<ListeningItem>>() {})
                            .forEach(l -> listenings.put(l.id(), l));
                    case "writing.json" -> read(mapper, in, new TypeReference<List<WritingTask>>() {})
                            .forEach(w -> writings.put(w.id(), w));
                    case "speaking.json" -> read(mapper, in, new TypeReference<List<SpeakingTask>>() {})
                            .forEach(sp -> speakings.put(sp.id(), sp));
                    case "cando.json" -> read(mapper, in, new TypeReference<List<CanDo>>() {})
                            .forEach(c -> canDos.put(c.id(), c));
                    case "exam-links.json" -> read(mapper, in, new TypeReference<List<ExamLink>>() {})
                            .forEach(e -> examLinks.put(e.id(), e));
                    case "placement.json" -> read(mapper, in, new TypeReference<List<Question>>() {})
                            .forEach(q -> {
                                questions.put(q.id(), q);
                                placementByLevel.computeIfAbsent(q.level(), l -> new ArrayList<>()).add(q);
                            });
                    default -> log.warn("Bilinmeyen icerik dosyasi atlandi: {}", r.getDescription());
                }
            }
        }
    }

    private static <T> List<T> read(ObjectMapper mapper, InputStream in, TypeReference<List<T>> type) {
        return mapper.readValue(in, type);
    }

    public Optional<Question> question(String id) {
        return Optional.ofNullable(questions.get(id));
    }

    /** Bir seviyenin yerlestirme soru havuzu (degistirilemez). */
    public List<Question> placementPool(Level level) {
        return Collections.unmodifiableList(placementByLevel.getOrDefault(level, List.of()));
    }

    public Map<String, GrammarTopic> topics() {
        return Collections.unmodifiableMap(topics);
    }

    public Map<String, Word> words() {
        return Collections.unmodifiableMap(words);
    }

    public Map<String, GrammarLesson> lessons() {
        return Collections.unmodifiableMap(lessons);
    }

    public Optional<GrammarLesson> lesson(String topicId) {
        return Optional.ofNullable(lessons.get(topicId));
    }

    public Map<String, ReadingText> readings() {
        return Collections.unmodifiableMap(readings);
    }

    public Map<String, ListeningItem> listenings() {
        return Collections.unmodifiableMap(listenings);
    }

    public Map<String, WritingTask> writings() {
        return Collections.unmodifiableMap(writings);
    }

    public Map<String, SpeakingTask> speakings() {
        return Collections.unmodifiableMap(speakings);
    }

    /** Bir seviyede sinava girilebilecek kurumlar (SPEC 7). */
    public List<ExamLink> examLinks(Level level) {
        return examLinks.values().stream()
                .filter(e -> e.levels().contains(level))
                .toList();
    }

    /** Bir becerinin bir seviyedeki can-do ifadeleri (SPEC 4.2). */
    public List<CanDo> canDos(Skill skill, Level level) {
        return canDos.values().stream()
                .filter(c -> c.skill() == skill && c.level() == level)
                .toList();
    }

    /** Bir konunun mini sorulari: o konunun etiketini tasiyan ogrenme sorulari. */
    public List<Question> questionsForTopic(String topicId) {
        return questions.values().stream()
                .filter(q -> q.placementKind() == null)
                .filter(q -> q.tags() != null && q.tags().contains(topicId))
                .toList();
    }
}
