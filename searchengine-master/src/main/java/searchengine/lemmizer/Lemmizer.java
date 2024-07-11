package searchengine.lemmizer;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
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
    @Autowired
    private LemmaCRUDService lemmaCRUDService;
    @Autowired
    private SiteCRUDService siteCRUDService;
    @Autowired
    private IndexCRUDService indexCRUDService;
    private static final String[] particleNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    public void createLemmasAndIndex(PageDto pageDto) throws IOException {
        Long pageId = pageDto.getId();
        log.info("Page id = " + pageId);
        log.info("Page dto path " + pageDto.getPath());
        Map<String, Integer> lemmaCountMap = getLemmasList(pageDto.getContent());
        log.info("Lemma count map " + lemmaCountMap.isEmpty());
        HashSet<IndexData> indexData = new HashSet<>();
        HashSet<LemmaDto> lemmaForUpdate = new HashSet<>();
        //HashMap<String, String> lemmaForCreate = new HashMap<>();//siteUrl : lemmaName
        Set<Map.Entry<String, String>> lemmaForCreate = new HashSet<>();
        UUID siteId = UUID.fromString(siteCRUDService.getByUrl(pageDto.getSite()).getId());
        for (String lemmaName : lemmaCountMap.keySet()) {
            LemmaDto lemmaDto = lemmaCRUDService
                    .getByLemmaAndSiteId(lemmaName,
                            siteId);// Что-то ломается здесь

            if (lemmaDto == null) {
                //lemmaForCreate.put(pageDto.getSite().toString(), lemmaName.toString());
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
        HashSet<LemmaDto> lemmaDtos = waitForLemmaCreation(lemmaForCreate);// Все леммы с id возвращаются
        for (IndexData data : indexData) {
            log.info("Data index data before setting lemmaDto " + data.getLemmaDto().getLemma());
            Optional<LemmaDto> optionalLemmaDto = lemmaDtos.stream().filter(it -> it.getLemma().equals(data.getLemmaDto().getLemma())).findFirst();

            if (optionalLemmaDto.isPresent() && optionalLemmaDto.get().getId() != null) {
                data.setLemmaDto(optionalLemmaDto.get());
                log.info("Data index data after setting lemmaDto " + data.getLemmaDto().getLemma());
            } else {
                optionalLemmaDto = updateLemmas.stream().filter(it -> it.getLemma().equals(data.getLemmaDto().getLemma())).findFirst();
                if (optionalLemmaDto.isPresent() && optionalLemmaDto.get().getId() != null) {
                   data.setLemmaDto(optionalLemmaDto.get());
                } else {
                    log.info("No LemmaDto found for lemma: " + data.getLemmaDto().getLemma());
                    LemmaDto dto = lemmaCRUDService.getByLemmaAndSiteId(data.getLemmaDto().getLemma(), siteId);
                    data.setLemmaDto(dto);
                    if(dto.getId() == null){
                        throw new EntityNotFoundException("Lemma dto not found");
                    }
                }
            }
        }
        //lemmaDtos = waitForLemmaCreation(lemmaForCreate);

        createIndex(indexData);

    }

    private HashSet<LemmaDto> createLemmas(Set<Map.Entry<String, String>> lemmaForCreate) {
        HashSet<LemmaDto> lemmaDtos = new HashSet<>();
        for (Map.Entry<String, String> entry : lemmaForCreate) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null || key == null) {
                log.error("ID of Lemma is null for lemma: ");
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

    private void createIndex(HashSet<IndexData> indexData) {// метод должен преобразовать в HashSet<IndexDto>
        HashSet<IndexDto> dtos = new HashSet<>();
        for (IndexData data : indexData) {
            IndexDto dto = new IndexDto();
            dto.setPageId(data.getPageId());
            if (data.getLemmaDto().getId() == null) {
                log.info("Lemma id почему-то null");// Все леммы приходят без id
                throw new EntityNotFoundException("ID LemmaDto is null");
            }
            dto.setLemmaId(Math.toIntExact(data.getLemmaDto().getId()));
            dto.setRankValue(data.getLemmaCount());
            dtos.add(dto);
        }
        indexCRUDService.createAll(dtos);
    }

    private LemmaDto createLemma(String siteUrl, String lemmaName) {
        LemmaDto dto = new LemmaDto();
        dto.setSiteUrl(siteUrl);
        dto.setLemma(lemmaName);
        lemmaCRUDService.create(dto);
        UUID siteId = siteCRUDService.findByUrl(siteUrl).getId();
        LemmaDto dtoForReturn = lemmaCRUDService.getByLemmaAndSiteId(lemmaName, siteId);
        return dtoForReturn;
    }

    public Map<String, Integer> getLemmasList(String text) throws IOException {
        Map<String, Integer> lemmaCountMap = new HashMap<>();
        log.info("Text " + text);
        List<String> cleanText = getTextAsList(text);
        List<String> wordList = getNormalWords(cleanText);
        for (String word : wordList) {
            if (lemmaCountMap.containsKey(word)) {
                log.info("Word " + word + " lemma count " + lemmaCountMap.get(word));
                lemmaCountMap.put(word, lemmaCountMap.get(word) + 1);
            } else {
                lemmaCountMap.put(word, 1);
            }
        }

        return lemmaCountMap;
    }

    /*private List<String> getTextAsList(String text) throws IOException {
        String regex = "[^а-яА-Я ]";
        String regex1 = "[ ]{2,}";

        String cleanText0 = text.replaceAll(regex, "");
        String cleanText1 = cleanText0.replaceAll(regex1, " ");
        log.info("Clean text" + cleanText1);
        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
        List<String> words = new ArrayList<>();
        for (String word : cleanText1.split(" ")) {
            log.info("Get Text as List word " + word);
            String lowerCaseWord = word.toLowerCase();
            String wordWithInfo = luceneMorphology.getMorphInfo(lowerCaseWord).toString();
            log.info("Word with info " + wordWithInfo);
            if (!lowerCaseWord.isEmpty() && !hasParticleProperty(wordWithInfo)) {
                words.add(lowerCaseWord);
            }
        }
        return words;
    }*/
    public List<String> getTextAsList(String text) throws IOException {
        String regex = "[^а-яА-Я -]";
        String regex1 = "[ ]{2,}";

        String cleanText0 = text.replaceAll(regex, "");
        String cleanText1 = cleanText0.replaceAll(regex1, " ");
        log.info("Clean text: " + cleanText1);

        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
        List<String> words = new ArrayList<>();

        for (String word : cleanText1.split(" ")) {
            if (word == null || word.isEmpty()) continue;
            log.info("Get Text as List word: " + word);

            String lowerCaseWord = word.toLowerCase();
            log.info("Lower case word: " + lowerCaseWord);

            try {
                List<String> morphInfoList = luceneMorphology.getMorphInfo(lowerCaseWord);
                if (morphInfoList == null || morphInfoList.isEmpty()) {
                    log.warn("Morph info list is null or empty for word: " + lowerCaseWord);
                    continue;
                }

                String wordWithInfo = morphInfoList.toString();
                log.info("Word with info: " + wordWithInfo);

                if (!hasParticleProperty(wordWithInfo)) {
                    words.add(lowerCaseWord);
                }
            } catch (Exception e) {
                log.error("Error processing word: " + lowerCaseWord, e);
            }
        }
        return words;
    }

    private List<String> getNormalWords(List<String> words) throws IOException {
        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
        List<String> normalWords = new ArrayList<>();
        for (String word : words) {
            String normalForm = luceneMorphology.getNormalForms(word).get(0);
            if (normalForm.isEmpty()) {
                continue;
            }
            normalWords.add(normalForm);
        }
        return normalWords;
    }

    private boolean hasParticleProperty(String word) {//wordBase
        for (String property : particleNames) {
            if (word.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private String removeHTMLTags(String html) {// Очищает html от тегов
        Document doc = Jsoup.parse(html);
        return doc.text();
    }

}
