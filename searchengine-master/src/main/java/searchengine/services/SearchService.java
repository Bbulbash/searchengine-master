package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
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
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static searchengine.dto.statistics.SnippetBuilder.getSnippetFromPage;

@Service
@Slf4j
public class SearchService {
    @Autowired
    private SitesList sitesList;
    private final PageCRUDService pageCRUDService;
    private final SiteCRUDService siteCRUDService;
    private final Lemmizer lemmizer;
    private final IndexCRUDService indexCRUDService;
    private final LemmaCRUDService lemmaCRUDService;
    private static final int SNIPPET_LENGTH = 300;
    private MorphologyService morphologyService;

    public SearchService(PageCRUDService pageCRUDService, SiteCRUDService siteCRUDService, Lemmizer lemmizer, IndexCRUDService indexCRUDService, LemmaCRUDService lemmaCRUDService) {
        this.pageCRUDService = pageCRUDService;
        this.siteCRUDService = siteCRUDService;
        this.lemmizer = lemmizer;
        this.indexCRUDService = indexCRUDService;
        this.lemmaCRUDService = lemmaCRUDService;
    }

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
        for (SearchResult result : pagedResults) {
            if (!result.getSnippet().equals("")) {
                cleanPagedResults.add(result);
            }
        }
        total = cleanPagedResults.size();

        response.put("result", true);
        response.put("count", total);
        response.put("data", cleanPagedResults);

        return response;
    }

    public Set<SearchResult> search(String text, String url) throws IOException, InterruptedException {
        HashMap<PageDto, Float> sortedPages = new HashMap<>();
        if (url != null) {
            sortedPages = getSortedPages(text, url);
        } else {
            List<Site> sites = sitesList.getSites();
            for (Site site : sites) {
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

    private Set<SearchResult> convertToSearchResult(HashMap<PageDto, Float> sortedPages, String query)
            throws IOException {
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
            searchResult.setRelevance(relevance);
            if (searchResult.getSnippet().equals("")) break;

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

        for (String keyword : keywords) {
            snippet = highlightKeyword(snippet, keyword);
        }

        return snippet;
    }

    private static String highlightKeyword(String text, String keyword) {
        if (keyword.isEmpty()) {
            return text;
        }
        String regex = "(?i)(" + Pattern.quote(keyword) + ")";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, "<b>" + matcher.group(1) + "</b>");
        }
        matcher.appendTail(result);
        if (!result.isEmpty()) {
            return result.toString();
        }


        Set<String> variations = getWordVariations(keyword);

        result = new StringBuffer();
        boolean found = processTextWithVariations(text, variations, result);

        if (found) {
            return result.toString();
        } else {
            return highlightKeyword(text, keyword.substring(0, keyword.length() - 1));
        }
    }

    private static boolean processTextWithVariations(String text, Set<String> variations, StringBuffer result) {
        for (String variation : variations) {
            String regex = "(?i)(" + Pattern.quote(variation) + ")";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);

            boolean found = false;
            while (matcher.find()) {
                found = true;
                matcher.appendReplacement(result, "<b>" + matcher.group(1) + "</b>");
            }
            matcher.appendTail(result);

            if (found) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> getWordVariations(String keyword) {
        Set<String> variations = new HashSet<>();
        variations.add(keyword);

        Analyzer analyzer = getAnalyzerForWord(keyword);

        try (TokenStream tokenStream = analyzer.tokenStream(null, new StringReader(keyword))) {
            CharTermAttribute attr = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();

            while (tokenStream.incrementToken()) {
                variations.add(attr.toString());
            }

            tokenStream.end();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return variations;
    }

    private static Analyzer getAnalyzerForWord(String word) {
        if (isRussian(word)) {
            return new RussianAnalyzer();
        } else {
            return new EnglishAnalyzer();
        }
    }

    private static boolean isRussian(String word) {
        return word.matches("[А-Яа-яЁё]+");
    }

    private String createSnippet(String text, String[] keywords) throws IOException {
        String snippet = "";
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
            if (snippet.isEmpty()) {
                List<String> textAsList = lemmizer.getTextAsList(text);
                List<String> normalFormText = lemmizer.getNormalWords(textAsList);
                for (String keyword1 : keywords) {
                    int index1 = normalFormText.indexOf(keyword1.toLowerCase());
                    if (index1 != -1) {


                        int indexOf = text.indexOf(textAsList.get(index1));
                        int start = text.indexOf(textAsList.get(index1)) - SNIPPET_LENGTH;
                        int end = text.indexOf(textAsList.get(index1)) + SNIPPET_LENGTH;

                        snippet = text.substring(start, end);
                        if (start > 0) snippet = "..." + snippet;
                        if (end < text.length()) snippet += "...";
                        break;
                    }
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
            Set<LemmaDto> lemmasByPage = lemmasByPages.get(dto.getId());
            if (lemmasByPage != null) {
                Set<String> lemmasName = lemmasByPage.stream()
                        .map(LemmaDto::getLemma)
                        .collect(Collectors.toSet());
                for (String lemma : dirtyLemmas) {
                    if (lemmasName.contains(lemma)) {
                        dirtyMap.put(lemma, dirtyMap.getOrDefault(lemma, 0) + 1);
                    }
                }
            }

        }
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

        boolean firstLemma = true;
        Set<Long> pageIds = new HashSet<>();
        pagesBySite.stream().forEach(it -> pageIds.add(it.getId()));
        Map<Long, Set<LemmaDto>> mapPageLemmas = lemmaCRUDService.findLemmasByPageIds(pageIds);
        for (String lemma : sortedLemmas.keySet()) {
            Set<PageDto> lemmaPages = new HashSet<>();
            for (PageDto pageDto : pagesBySite) {

                Set<LemmaDto> lemmaDtos = mapPageLemmas.get(pageDto.getId());
                Set<String> lemmaNames = new HashSet<>();
                if (lemmaDtos != null) {
                    lemmaNames = lemmaDtos.stream().map(LemmaDto::getLemma).collect(Collectors.toSet());
                }

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
