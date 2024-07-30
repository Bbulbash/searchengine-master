package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.objects.IndexDto;
import searchengine.dto.objects.LemmaDto;
import searchengine.dto.objects.PageDto;
import searchengine.dto.statistics.SearchResult;
import searchengine.lemmizer.Lemmizer;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SearchService {
    @Autowired
    private SitesList sitesList;
    @Autowired
    private PageCRUDService pageCRUDService;
    @Autowired
    private SiteCRUDService siteCRUDService;
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

        if (results.isEmpty()) {
            response.put("result", false);
            response.put("error", "Лемма не найдена");
            return response;
        }

        int total = results.size();
        int endIndex = Math.min(offset + limit, total);
        List<SearchResult> pagedResults = results.stream().toList().subList(offset, endIndex);
        List<SearchResult> cleanPagedResults = new ArrayList<>();
        for (SearchResult result : pagedResults){
            //result = result;
            if(!result.getSnippet().equals("")){
                cleanPagedResults.add(result);
            }
        }
        total = cleanPagedResults.size();

        response.put("result", true);
        response.put("count", total);
        //response.put("total", total);
        //response.put("offset", offset);
       // response.put("limit", limit);
        response.put("data", cleanPagedResults);

        return response;
    }

    public Set<SearchResult> search(String text, String url) throws IOException, InterruptedException {
        HashMap<PageDto, Float> sortedPages = new HashMap<>();
        if(url != null){
            sortedPages = getSortedPages(text, url);
        }else{
            List<Site> sites = sitesList.getSites();
            for(Site site : sites){
                sortedPages.putAll(getSortedPages(text, site.getUrl()));
            }
        }

        return convertToSearchResult(sortedPages, text);
    }
    private HashMap<PageDto, Float> getSortedPages(String text, String url) throws IOException {
        List<PageDto> pagesBySite = pageCRUDService.getPagesBySiteURL(url);
        Map<String, Integer> lemmasList = getLemmasList(text, url, pagesBySite);
        Set<PageDto> relevantPages = findPagesByLemmas(lemmasList, pagesBySite);
        if (relevantPages.size() == 0) {
            return new HashMap<>();
        }
        return getPagesSortByRelevance(relevantPages);
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
            if(searchResult.getSnippet() == "") break;

            result.add(searchResult);
        }
        return result;
    }

    private String getPageTitle(PageDto pageDto) {
        String html = pageDto.getContent();
        Document doc = Jsoup.parse(html);
        return doc.title();
    }

    private String generateSnippet(String content, String query) throws IOException {

        String[] keywords = query.split("\\s+");

        String snippet = createSnippet(content, keywords);

        // Выделение ключевых слов в сниппете
        for (String keyword : keywords) {
            snippet = highlightKeyword(snippet, keyword);
        }

        return snippet;
    }

    private String highlightKeyword(String text, String keyword) {
        // Компиляция регулярного выражения для ключевого слова с учетом регистра
        String regex = "(?i)(" + Pattern.quote(keyword) + ")";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        // Замена с сохранением оригинального регистра
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, "<b>" + matcher.group(1) + "</b>");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private String createSnippet(String text, String[] keywords) throws IOException {
        String snippet = "";
        //lemmizer.getNormalWords
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

        // Если ни одно из ключевых слов не найдено, прописать поиск по другим формам слова
        if (snippet.isEmpty()) {
            // snippet = text.length() > SNIPPET_LENGTH ? text.substring(0, SNIPPET_LENGTH) + "..." : text;
            List<String> textAsList = lemmizer.getTextAsList(text);
            List<String> normalFormText = lemmizer.getNormalWords(textAsList);
            for (String keyword : keywords) {
                int index = normalFormText.indexOf(keyword.toLowerCase());
                if (index != -1) {
                    int start = Math.max(0, index - SNIPPET_LENGTH / 2);
                    int end = Math.min(text.length(), index + SNIPPET_LENGTH / 2);

                    snippet = text.substring(start, end);
                    if (start > 0) snippet = "..." + snippet;
                    if (end < text.length()) snippet += "...";
                    break;
                }
            }
        }

        return snippet;
    }

    private HashMap<PageDto, Float> getPagesSortByRelevance(Set<PageDto> pagesSet) {
        HashMap<PageDto, Float> sortedPagesDto = new HashMap<>();
        HashMap<PageDto, Float> absoluteRelevancies = new HashMap<>();
        float maxRelevancy = 0.0f;
        Set<Long> pageIds = pagesSet.stream().map(PageDto::getId).collect(Collectors.toSet());
        Map<Long, Set<IndexDto>> indexesByPageId = indexCRUDService.findIndexesByPageIds(pageIds);
        for (PageDto page : pagesSet) {
            if (page.getId() != null) {
                Set<IndexDto> indexDtoSet = indexesByPageId.get(page.getId());
                float rankSum = calculateAbsoluteRelevancy(indexDtoSet);
                absoluteRelevancies.put(page, rankSum);
                if (rankSum > maxRelevancy) {
                    maxRelevancy = rankSum;
                }
            } else {
                log.error("PAGE ID IS NULL");
            }
        }

        for (Map.Entry<PageDto, Float> entry : absoluteRelevancies.entrySet()) {
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
        Set<Long> pageIds = pagesBySite.stream().map(PageDto::getId).collect(Collectors.toSet());
        Map<Long, Set<LemmaDto>> lemmasByPages = lemmaCRUDService.findLemmasByPageIds(pageIds);
        for (PageDto dto : pagesBySite) {
            Set<LemmaDto> lemmasByPage = lemmasByPages.get(dto.getId());// Для ускорения работы можно вытягивать инфу в массив Страница - набор лемм
            Set<String> lemmasName = lemmasByPage.stream()
                    .map(LemmaDto::getLemma)
                    .collect(Collectors.toSet());
            for (String lemma : dirtyLemmas) {
                if (lemmasName.contains(lemma)) {
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
        return cleanMap;// Почему-то cleanMap пустой
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
        Set<Long> pageIds = new HashSet<>();
        pagesBySite.stream().forEach(it -> pageIds.add(it.getId()));
        Map<Long, Set<LemmaDto>> mapPageLemmas = lemmaCRUDService.findLemmasByPageIds(pageIds);
        for (String lemma : sortedLemmas.keySet()) {
            Set<PageDto> lemmaPages = new HashSet<>();
            for (PageDto pageDto : pagesBySite) {
                //Set<LemmaDto> lemmaDtos = lemmaCRUDService.getLemmasByPage(pageDto.getId());
                Set<LemmaDto> lemmaDtos = mapPageLemmas.get(pageDto.getId());
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
