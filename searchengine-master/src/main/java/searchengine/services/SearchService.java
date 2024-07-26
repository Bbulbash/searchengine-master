package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.objects.IndexDto;
import searchengine.dto.objects.LemmaDto;
import searchengine.dto.objects.PageDto;
import searchengine.dto.statistics.SearchResult;
import searchengine.lemmizer.Lemmizer;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SearchService {
    @Autowired
    private SitesList sitesList;
    @Autowired
    private PageCRUDService pageCRUDService;
    @Autowired
    private Lemmizer lemmizer;
    @Autowired
    private IndexCRUDService indexCRUDService;
    @Autowired
    private LemmaCRUDService lemmaCRUDService;
    private static final int SNIPPET_LENGTH = 300;
    public Map<String, Object> search(String query, String site, int offset, int limit)
            throws IOException, InterruptedException {
        Map<String, Object> response = new HashMap<>();
        Set<SearchResult> results = search(query, site);

        if(results.isEmpty()){
            response.put("result", false);
            response.put("error", "Лемма не найдена");
            return response;
        }

        int total = results.size();
        int endIndex = Math.min(offset + limit, total);
        List<SearchResult> pagedResults = results.stream().toList().subList(offset, endIndex);

        response.put("result", true);
        response.put("total", total);
        response.put("offset", offset);
        response.put("limit", limit);
        response.put("data", pagedResults);

        return response;
    }

    public Set<SearchResult> search(String text, String url) throws IOException, InterruptedException {
        List<PageDto> pagesBySite = pageCRUDService.getPagesBySiteURL(url);
        Map<String, Integer> lemmasList = getLemmasList(text, url, pagesBySite);
        Set<PageDto> relevantPages = findPagesByLemmas(lemmasList, pagesBySite);
        if (relevantPages.size() == 0) {
            return new HashSet<>();
        }
        HashMap<PageDto, Float> sortedPages = getPagesSortByRelevance(relevantPages);
        return convertToSearchResult(sortedPages, text);
    }
    private Set<SearchResult> convertToSearchResult(HashMap<PageDto, Float> sortedPages, String query) throws IOException, InterruptedException {
        //List<SearchResult> result = new ArrayList<>();
        Set<SearchResult> result = new HashSet<>();
        for (Map.Entry<PageDto, Float> entry : sortedPages.entrySet()) {
            PageDto page = entry.getKey();
            float relevance = entry.getValue();
            String siteUrl = page.getSite();
            String siteName = sitesList.getSites().stream()
                    .filter(it -> it.getUrl().equals(siteUrl)).toList().get(0).getName();
            SearchResult searchResult = new SearchResult();
            searchResult.setUrl(siteUrl);
            searchResult.setSiteName(siteName);
            searchResult.setUrl(page.getPath());
            searchResult.setTitle(getPageTitle(page));
            searchResult.setSnippet(generateSnippet(page.getContent(), query));
            searchResult.setRelevance(relevance);

            result.add(searchResult);
        }
        return result;
    }
    private String getPageTitle(PageDto pageDto){
        String html = pageDto.getContent();
        Document doc = Jsoup.parse(html);
        return doc.title();
    }
    private String generateSnippet(String content, String query) {

        String[] keywords = query.split("\\s+");

        String snippet = createSnippet(content, keywords);

        // Выделение ключевых слов в сниппете
        for (String keyword : keywords) {
            snippet = snippet.replaceAll("(?i)" + keyword, "<b>" + keyword + "</b>");
        }

        return snippet;
    }

    private String createSnippet(String text, String[] keywords) {
        String snippet = "";

        // Поиск упоминания ключевого слова и создание фрагмента текста вокруг него
        for (String keyword : keywords) {
            int index = text.toLowerCase().indexOf(keyword.toLowerCase());
            if (index != -1) {
                int start = Math.max(0, index - SNIPPET_LENGTH / 2);
                int end = Math.min(text.length(), index + SNIPPET_LENGTH / 2);

                snippet = text.substring(start, end);
                if (start > 0) snippet = "..." + snippet;
                if (end < text.length()) snippet += "...";
                break;
            }
        }

        // Если ни одно из ключевых слов не найдено, просто обрезаем текст
        if (snippet.isEmpty()) {
            snippet = text.length() > SNIPPET_LENGTH ? text.substring(0, SNIPPET_LENGTH) + "..." : text;
        }

        return snippet;
    }

    private HashMap<PageDto, Float> getPagesSortByRelevance(Set<PageDto> pagesSet) {
        HashMap<PageDto, Float> sortedPagesDto = new HashMap<>();
        HashMap<PageDto, Float> absoluteRelevancies = new HashMap<>();
        float maxRelevancy = 0.0f;

        for(PageDto page : pagesSet) {
            if(page.getId() != null) {
                Set<IndexDto> indexDtoSet = indexCRUDService.findByPageId(page.getId());
                float rankSum = calculateAbsoluteRelevancy(indexDtoSet);
                absoluteRelevancies.put(page, rankSum);
                if (rankSum > maxRelevancy) {
                    maxRelevancy = rankSum;
                }
            } else {
                log.error("PAGE ID IS NULL");
            }
        }

        for(Map.Entry<PageDto, Float> entry : absoluteRelevancies.entrySet()) {
            PageDto page = entry.getKey();
            float absoluteRelevancy = entry.getValue();
            float relativeRelevancy = absoluteRelevancy / maxRelevancy;
            sortedPagesDto.put(page, relativeRelevancy);
        }

        return sortedPagesDto.entrySet()
                .stream()
                .sorted((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }
    /*private HashMap<PageDto, Float> getPagesSortByRelevance(Set<PageDto> pagesSet) {
        HashMap<PageDto, Float> absoluteRelevancies = new HashMap<>();
        float maxRelevancy = 0.0f;

        // Вычисление абсолютной релевантности каждой страницы
        for (PageDto page : pagesSet) {
            if (page.getId() != null) {
                Set<IndexDto> indexDtoSet = indexCRUDService.findByPageId(page.getId());
                float rankSum = calculateAbsoluteRelevancy(indexDtoSet);
                absoluteRelevancies.put(page, rankSum);

                // Обновление максимальной релевантности
                if (rankSum > maxRelevancy) {
                    maxRelevancy = rankSum;
                }
            } else {
                log.error("PAGE ID IS NULL");
            }
        }

        // Вычисление относительной релевантности страниц
        float finalMaxRelevancy = maxRelevancy;
        HashMap<PageDto, Float> sortedPagesDto = absoluteRelevancies.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> finalMaxRelevancy == 0 ? 0 : entry.getValue() / finalMaxRelevancy,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        // Сортировка страниц по относительной релевантности
        return sortedPagesDto.entrySet()
                .stream()
                .sorted((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }*/
    private float calculateAbsoluteRelevancy(Set<IndexDto> indexByPage) {
        float relevancySum = 0.0f;
        for (IndexDto lemma : indexByPage) {
            relevancySum += lemma.getRankValue();
        }
        return relevancySum;
    }

    private Map<String, Integer> getLemmasList(String text, String url, List<PageDto> pagesBySite) throws IOException {
        Map<String, Integer> mapLemma = lemmizer.getLemmasList(text);
        Set<String> lemmaSet = new HashSet<>();
        for (String lemma : mapLemma.keySet()) {
            lemmaSet.add(lemma);
        }
        Map<String, Integer> cleanLemmas = getCleanLemmas(url, lemmaSet, pagesBySite);
        Map<String, Integer> sortedLemmas = sortLemmas(cleanLemmas);
        return sortedLemmas;
    }

    private Map<String, Integer> getCleanLemmas(String url, Set<String> dirtyLemmas, List<PageDto> pagesBySite) {
        int pagesCount = pagesBySite.size();
        Map<String, Integer> dirtyMap = new HashMap<>();
        Map<String, Integer> cleanMap = new HashMap<>();
        for (PageDto dto : pagesBySite) {
            for (String lemma : dirtyLemmas) {
                if (dto.getContent().contains(lemma)) {
                    dirtyMap.put(lemma, dirtyMap.getOrDefault(lemma, 0) + 1);
                }
            }
        }

        // Проверяем, какие леммы встречаются на 80% и менее страницах
        for (String lemma : dirtyMap.keySet()) {
            int count = dirtyMap.get(lemma);
            if (count <= 0.8 * pagesCount) {
                cleanMap.put(lemma, count);
            }
        }
        return cleanMap;
    }

    private Map<String, Integer> sortLemmas(Map<String, Integer> cleanLemmas) {
        return cleanLemmas.entrySet()
                .stream()
                .sorted((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    private Set<PageDto> findPagesByLemmas(Map<String, Integer> sortedLemmas, List<PageDto> pagesBySite) {
        Set<PageDto> relevantPages = new HashSet<>();
        // Вытаскивать индексы с конкретной страницы, вытаскивать от туда леммы  и их сравнивать с леммами
        boolean firstLemma = true;

        for (String lemma : sortedLemmas.keySet()) {
            Set<PageDto> lemmaPages = new HashSet<>();
            for (PageDto pageDto : pagesBySite) {
                Set<LemmaDto> lemmaDtos = lemmaCRUDService.getLemmasByPage(pageDto.getId());
                Set<String> lemmaNames = lemmaDtos.stream().map(LemmaDto::getLemma).collect(Collectors.toSet());

                if (lemmaNames.contains(lemma)) {
                    lemmaPages.add(pageDto);
                }
            }

            if (firstLemma) {
                relevantPages.addAll(lemmaPages);
                firstLemma = false;
            } else {
                relevantPages.retainAll(lemmaPages);
            }

            if (relevantPages.isEmpty()) {
                break;
            }
        }

        return relevantPages;
    }
}
