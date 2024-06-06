package searchengine.lemmizer;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
        log.info("siteCRUDService.getByUrl(pageDto.getSite()).getId() " + siteCRUDService.getByUrl(pageDto.getSite()).getId());
        for (String lemmaName : lemmaCountMap.keySet()) {
            LemmaDto lemmaDto = lemmaCRUDService
                    .getByLemmaAndSiteId(lemmaName, siteCRUDService.getByUrl(pageDto.getSite()).getId());// Что-то ломается здесь
            if (lemmaDto == null) {
                log.info("Creating lemma ");
                lemmaDto = createLemma(pageDto.getSite(), lemmaName);
            }else{
                lemmaDto.setFrequency(lemmaDto.getFrequency() + 1);
                lemmaCRUDService.update(lemmaDto);
            }
            createIndex(lemmaCountMap.get(lemmaName), pageId, lemmaDto);//возвращается lemmaDto = null из-за этого выдается ошибка
        }

    }
    private void createIndex(int rankValue, Long pageId, LemmaDto limmaDto){
        IndexDto dto = new IndexDto();
        dto.setLemmaId(Math.toIntExact(limmaDto.getId()));
        dto.setPageId(pageId);// Почему-то pageId null
        dto.setRankValue(rankValue);
        indexCRUDService.create(dto);// Посмотреть че там с pageId
    }

    private LemmaDto createLemma(String siteUrl, String lemmaName) {
        Boolean isServiceEmpty = lemmaCRUDService.isServiceEmpty();
        Long idLemma = (isServiceEmpty) ? 1L : Long.parseLong(String.valueOf(lemmaCRUDService.getAll().size())) + 1L;
        LemmaDto dto = new LemmaDto();
        dto.setId(idLemma);
        dto.setSiteUrl(siteUrl);
        dto.setFrequency(1);
        dto.setLemma(lemmaName);
        lemmaCRUDService.create(dto);
        return dto;
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

    private List<String> getTextAsList(String text) throws IOException {
        String regex = "[^а-яА-Я ]";
        String regex1 = "[ ]{2,}";

        String cleanText0 = text.replaceAll(regex, "");
        String cleanText1 = cleanText0.replaceAll(regex1, " ");
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
