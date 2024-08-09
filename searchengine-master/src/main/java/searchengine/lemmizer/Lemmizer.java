package searchengine.lemmizer;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.objects.IndexData;
import searchengine.dto.objects.IndexDto;
import searchengine.dto.objects.LemmaDto;
import searchengine.dto.objects.PageDto;
import searchengine.services.IndexCRUDService;
import searchengine.services.LemmaCRUDService;
import searchengine.services.SiteCRUDService;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
public class Lemmizer {
    private final LemmaCRUDService lemmaCRUDService;
    private final SiteCRUDService siteCRUDService;
    private final IndexCRUDService indexCRUDService;
    private static final String[] particleNames =
            new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "CONJ", "ARTICLE", "ADJECTIVE", "PART", "ADVERB"};

    public Lemmizer(LemmaCRUDService lemmaCRUDService, SiteCRUDService siteCRUDService, IndexCRUDService indexCRUDService) {
        this.lemmaCRUDService = lemmaCRUDService;
        this.siteCRUDService = siteCRUDService;
        this.indexCRUDService = indexCRUDService;
    }

    public void createLemmasAndIndex(PageDto pageDto) throws IOException {
        Long pageId = pageDto.getId();

        Map<String, Integer> lemmaCountMap = getLemmasList(pageDto.getContent());

        HashSet<IndexData> indexData = new HashSet<>();
        HashSet<LemmaDto> lemmaForUpdate = new HashSet<>();

        Set<Map.Entry<String, String>> lemmaForCreate = new HashSet<>();
        UUID siteId = UUID.fromString(siteCRUDService.getByUrl(pageDto.getSite()).getId());
        for (String lemmaName : lemmaCountMap.keySet()) {
            LemmaDto lemmaDto = lemmaCRUDService
                    .getByLemmaAndSiteId(lemmaName,
                            siteId);

            if (lemmaDto == null) {
                lemmaForCreate.add(new AbstractMap.SimpleEntry<>(pageDto.getSite(), lemmaName));
            } else {
                lemmaDto.setFrequency(lemmaDto.getFrequency() + 1);
                lemmaForUpdate.add(lemmaDto);
            }
            LemmaDto dto = new LemmaDto();
            dto.setLemma(lemmaName);
            indexData.add(new IndexData(lemmaCountMap.get(lemmaName), pageId, dto));
        }
        HashSet<LemmaDto> updateLemmas = updateLemmas(lemmaForUpdate);
        HashSet<LemmaDto> lemmaDtos = waitForLemmaCreation(lemmaForCreate);
        for (IndexData data : indexData) {

            Optional<LemmaDto> optionalLemmaDto = lemmaDtos.stream().filter(it -> it.getLemma().equals(data.getLemmaDto().getLemma())).findFirst();

            if (optionalLemmaDto.isPresent() && optionalLemmaDto.get().getId() != null) {
                data.setLemmaDto(optionalLemmaDto.get());
            } else {
                optionalLemmaDto = updateLemmas.stream().filter(it -> it.getLemma().equals(data.getLemmaDto().getLemma())).findFirst();
                if (optionalLemmaDto.isPresent() && optionalLemmaDto.get().getId() != null) {
                    data.setLemmaDto(optionalLemmaDto.get());
                } else {

                    LemmaDto dto = lemmaCRUDService.getByLemmaAndSiteId(data.getLemmaDto().getLemma(), siteId);
                    data.setLemmaDto(dto);
                    if (dto.getId() == null) {
                        throw new EntityNotFoundException("Lemma dto not found");
                    }
                }
            }
        }
        createIndex(indexData);

    }

    private HashSet<LemmaDto> createLemmas(Set<Map.Entry<String, String>> lemmaForCreate) {
        HashSet<LemmaDto> lemmaDtos = new HashSet<>();
        for (Map.Entry<String, String> entry : lemmaForCreate) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null || key == null) {
                continue;
            }
            LemmaDto lemmaDto = new LemmaDto();
            lemmaDto.setLemma(value);
            lemmaDto.setFrequency(1);
            lemmaDto.setSiteUrl(key);

            lemmaDtos.add(lemmaDto);
        }
        HashSet<LemmaDto> createdLemmas = lemmaCRUDService.createAll(lemmaDtos);

        for (LemmaDto lemma : createdLemmas) {
            if (lemma.getId() == null) {
                log.error("Failed to create lemma properly, lemma.id is null for " + lemma.getLemma());
            }
        }

        return createdLemmas;
    }

    private HashSet<LemmaDto> waitForLemmaCreation(Set<Map.Entry<String, String>> lemmaForCreate) {
        HashSet<LemmaDto> createdLemmas = new HashSet<>();
        boolean allLemmasCreated = false;

        while (!allLemmasCreated) {
            createdLemmas = createLemmas(lemmaForCreate);
            allLemmasCreated = createdLemmas.size() == lemmaForCreate.size();

            if (!allLemmasCreated) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    log.error("Interrupted while waiting for lemma creation", e);
                    Thread.currentThread().interrupt(); // восстанавливаем статус прерывания
                }
            }
        }

        return createdLemmas;
    }

    private HashSet<LemmaDto> updateLemmas(HashSet<LemmaDto> lemmaForUpdate) {
        return lemmaCRUDService.updateAll(lemmaForUpdate);
    }

    private void createIndex(HashSet<IndexData> indexData) {
        HashSet<IndexDto> dtos = new HashSet<>();
        for (IndexData data : indexData) {
            IndexDto dto = new IndexDto();
            dto.setPageId(data.getPageId());
            if (data.getLemmaDto().getId() == null) {
                throw new EntityNotFoundException("ID LemmaDto is null");
            }
            dto.setLemmaId(Math.toIntExact(data.getLemmaDto().getId()));
            dto.setRankValue(data.getLemmaCount());
            dtos.add(dto);
        }
        indexCRUDService.createAll(dtos);
    }

    public Map<String, Integer> getLemmasList(String text) throws IOException {
        Map<String, Integer> lemmaCountMap = new HashMap<>();

        List<String> cleanText = getTextAsList(text);
        List<String> wordList = getNormalWords(cleanText);
        for (String word : wordList) {
            if (lemmaCountMap.containsKey(word)) {
                lemmaCountMap.put(word, lemmaCountMap.get(word) + 1);
            } else {
                lemmaCountMap.put(word, 1);
            }
        }

        return lemmaCountMap;
    }

    public List<String> getNormalWords(String text) throws IOException {
        List<String> cleanText = getTextAsList(text);
        return getNormalWords(cleanText);
    }

    public List<String> getTextAsList(String text) throws IOException {
        String regex = "[^а-яА-Яa-zA-Z -]";
        String regex1 = "[ ]{2,}";
        String cleanText0 = text.replaceAll(regex, "");
        String cleanText1 = cleanText0.replaceAll(regex1, " ");
        log.info("Clean text: " + cleanText1);

        LuceneMorphology luceneMorphologyRU = new RussianLuceneMorphology();
        LuceneMorphology luceneMorphologyEN = new EnglishLuceneMorphology();
        List<String> words = new ArrayList<>();
        for (String word : cleanText1.split(" ")) {
            if (word == null || word.isEmpty()) continue;
            log.info("Get Text as List word: " + word);

            String lowerCaseWord = word.toLowerCase();
            log.info("Lowercase word: " + lowerCaseWord);

            if (isRussianWord(lowerCaseWord)) {
                try {
                    List<String> morphInfoList = luceneMorphologyRU.getMorphInfo(lowerCaseWord);
                    if (morphInfoList != null && !morphInfoList.isEmpty()) {
                        String wordWithInfo = morphInfoList.toString();
                        log.info("Word with info (RU): " + wordWithInfo);

                        if (!hasParticleProperty(wordWithInfo)) {
                            words.add(lowerCaseWord);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error processing Russian word: " + lowerCaseWord, e);
                }
            } else if (isEnglishWord(lowerCaseWord)) {

                try {
                    List<String> morphInfoList = luceneMorphologyEN.getMorphInfo(lowerCaseWord);
                    if (morphInfoList != null && !morphInfoList.isEmpty()) {
                        String wordWithInfo = morphInfoList.toString();
                        log.info("Word with info (EN): " + wordWithInfo);

                        if (!hasParticleProperty(wordWithInfo)) {
                            words.add(lowerCaseWord);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error processing English word: " + lowerCaseWord, e);
                }
            }
        }
        return words;
    }

    private boolean isRussianWord(String word) {
        return word.matches("[а-яА-Я]+");
    }

    private boolean isEnglishWord(String word) {
        return word.matches("[a-zA-Z]+");
    }

    public List<String> getNormalWords(List<String> words) throws IOException {//Макс: лучше переписать на регулярки
        LuceneMorphology luceneMorphologyRU = new RussianLuceneMorphology();
        LuceneMorphology luceneMorphologyEN = new EnglishLuceneMorphology();
        List<String> normalWords = new ArrayList<>();
        for (String word : words) {
            List<String> normalForms;
            String normalForm = new String();
            String lowerCaseWord = word.toLowerCase();
            if (isRussianWord(lowerCaseWord)) {
                normalForms = luceneMorphologyRU.getNormalForms(lowerCaseWord);
                log.warn("Normal forms " + normalForms);
                normalForm = normalForms.get(0);
            } else if (isEnglishWord(lowerCaseWord)) {
                normalForms = luceneMorphologyEN.getNormalForms(lowerCaseWord);

                log.warn("Normal forms " + normalForms);
                normalForm = normalForms.get(0);//getFirst();
            }
            if (normalForm.isEmpty()) {
                continue;
            }
            normalWords.add(normalForm);
        }
        return normalWords;
    }

    private boolean hasParticleProperty(String word) {
        for (String property : particleNames) {
            if (word.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

}
