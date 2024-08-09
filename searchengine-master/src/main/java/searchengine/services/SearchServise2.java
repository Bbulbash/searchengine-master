package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.buf.StringUtils;
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
import searchengine.dto.statistics.WordMapper;
import searchengine.lemmizer.Lemmizer;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static searchengine.dto.statistics.SnippetBuilder.getSnippetFromPage;

@Service
@Slf4j
public class SearchServise2 {
    @Autowired
    private SitesList sitesList;
    private final PageCRUDService pageCRUDService;
    private final SiteCRUDService siteCRUDService;
    private final Lemmizer lemmizer;
    private final IndexCRUDService indexCRUDService;
    private final LemmaCRUDService lemmaCRUDService;
    private MorphologyService morphologyService;
    private static final int SNIPPET_LENGTH = 300;

    //Идея - получать леммы из поискового запроса. Из бд подтягивать леммы по леммам и сайту,, по леммам подтягисвать их индексы, от индексов брать страницы.
    // Со страницы брать текст, искать сниппет (мб по страрому алгоритму), возвращать ответ.
    // TODO: получать леммы из поискового запроса +
    // TODO: подтягивать леммы по всем сайтам или по конкретному +
    // TODO: отсеять леммы которые встречаются слишком часто +
    // TODO: отсортировать от самой редкой до самой частой по полю frequency+
    // TODO: по первой (самой редкой) лемме найти страницы, на которых она встречается+
    // TODO: найти страницу из списка выше, где есть вторая лемма из списка И ТАК ДЛЯ КАЖДОЙ ПОСЛЕДУЮЩЕЙ
    // TODO: если не найдено страниц - выводить пустой список, если найдено - расчитывать релевантность
    // TODO: для каждой страницы из списка высчитать абсолютную релевантность(См ТЗ)
    // TODO: Сортировать страницы по убыванию
    // TODO: Для каждой страницы из списка найти снипет
    // TODO: Вернуть результаты поиска
    public SearchServise2(PageCRUDService pageCRUDService, SiteCRUDService siteCRUDService, Lemmizer lemmizer, IndexCRUDService indexCRUDService, LemmaCRUDService lemmaCRUDService) {
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

    public Set<SearchResult> search(String text, String url) throws IOException{
        Set<LemmaDto> lemmaDtos = getLemmasList(text, url);
        Set<LemmaDto> lessPopularLemmas = getLessPopularLemmas(lemmaDtos).stream()
                .sorted(Comparator.comparingInt(LemmaDto::getFrequency))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<PageDto> matchingPages = filterPagesByAllLemmas(lessPopularLemmas);
        HashMap<PageDto, Float> sortedByRelevancePage = getPagesSortByRelevance(matchingPages);

        return convertToSearchResult(sortedByRelevancePage, text);
    }

    private Set<SearchResult> convertToSearchResult(HashMap<PageDto, Float> sortedPages, String query) throws IOException {
        Set<SearchResult> result = new HashSet<>();
        morphologyService = new MorphologyService();
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
            if (searchResult.getSnippet().equals("")) break;

            result.add(searchResult);
        }
        return result;
    }

    public String generateSnippet(String text, String searchQuery) throws IOException {// Макс: Регуляркой находим совпадения с корнями лем в переданой строке и по ним искать
        List<String> queryAsList = List.of(searchQuery.split("\\s+"));
        List<String> cleanQuery = removePunctuation(queryAsList);
        List<String> queryNormalForm = new ArrayList<>();
        List<String> words = Arrays.stream(text.split("\\s+"))
                .map(word -> word.replaceAll("[^a-zA-Zа-яА-Я]", ""))
                .collect(Collectors.toList());
        List<String> wordsForSearch = new ArrayList<>();
        List<String> wordsForSnippet = new ArrayList<>();
        List<Integer> foundWordsIndexes = new ArrayList<>();
        for(String w : words){
            if(!w.equals("")) wordsForSearch.add(w);
        }
        for(String key : wordsForSearch){
            List<String> normalForms = lemmizer.getNormalWords(key);
            if(!normalForms.isEmpty()){
                wordsForSnippet.add(key);
            }
        }
        if(!cleanQuery.isEmpty()){
            queryNormalForm.addAll(lemmizer.getNormalWords(cleanQuery));
        }

        for(String query : queryNormalForm){
           foundWordsIndexes.add(wordsForSnippet.indexOf(query));
        }
        return constructSnippetWithHighlight(foundWordsIndexes, new ArrayList<>(wordsForSearch));
    }

    public static String constructSnippetWithHighlight(List<Integer> foundWordsIndexes, List<String> words) {
        List<String> snippetCollector = new ArrayList<>();
        int beginning, end, before, after, index, prevIndex;
        before = 12;
        after = 6;

        foundWordsIndexes.sort(Integer::compareTo);

        for (int i : foundWordsIndexes) {
            words.set(i, "<b>" + words.get(i) + "</b>");
        }

        index = foundWordsIndexes.get(0);
        beginning = Math.max(0, index - before);
        end = Math.min(words.size() - 1, index + after);

        for (int i = 1; i <= foundWordsIndexes.size(); i++) {
            if (i == foundWordsIndexes.size()) {
                snippetCollector.add(String.join("", words.subList(beginning, end)));
                break;
            }
            prevIndex = index;
            index = foundWordsIndexes.get(i);
            if (index - before <= prevIndex) {
                end = Math.min(words.size() - 1, index + after);
                continue;
            }
            snippetCollector.add(String.join("", words.subList(beginning, end)));
            beginning = Math.max(0, index - before);
            end = Math.min(words.size() - 1, index + after);
        }
        return String.join("...", snippetCollector);
    }

    public static List<String> removePunctuation(List<String> words) {
        return words.stream()
                .map(word -> word.replaceAll("[.,]", ""))
                .collect(Collectors.toList());
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

    private Set<PageDto> filterPagesByAllLemmas(Set<LemmaDto> lemmaDtos) {
        Iterator<LemmaDto> lemmaIterator = lemmaDtos.iterator();
        if (!lemmaIterator.hasNext()) {
            return Collections.emptySet();
        }

        LemmaDto firstLemma = lemmaIterator.next();
        Set<PageDto> pages = getPagesWithFirstLemma(firstLemma);
        Set<PageDto> pagesWithFirstLemma = new HashSet<>(pages);

        while (lemmaIterator.hasNext()) {
            LemmaDto nextLemma = lemmaIterator.next();
            Set<PageDto> nextPages = getPagesWithFirstLemma(nextLemma);
            pages.retainAll(nextPages);
        }
        if (pages.size() == 0) return pagesWithFirstLemma;

        return pages;
    }

    private Set<PageDto> getPagesWithFirstLemma(LemmaDto dto) {
        Set<Long> lemmaId = new HashSet<>();
        lemmaId.add(dto.getId());
        Set<IndexDto> indexDtos = indexCRUDService.findAllByLemmaId(lemmaId);
        Set<Long> pageIds = new HashSet<>();
        for (IndexDto index : indexDtos) {
            pageIds.add(index.getPageId());
        }
        return pageCRUDService.findPagesByIds(pageIds);
    }

    private Set<LemmaDto> getLessPopularLemmas(Set<LemmaDto> lemmaDtos) {
        Map<String, Integer> urlPageCountMap = getUrlPageCountMap();
        Set<LemmaDto> lessPopularLemmas = new HashSet<>();
        for (LemmaDto dto : lemmaDtos) {
            String url = dto.getSiteUrl();
            Integer pageCount = urlPageCountMap.get(url);
            if (dto.getFrequency() <= 0.8 * pageCount) {
                lessPopularLemmas.add(dto);
            }
        }
        return lessPopularLemmas;

    }

    private Map<String, Integer> getUrlPageCountMap() {
        List<Site> sites = sitesList.getSites();
        Map<String, Integer> urlPageCountMap = new HashMap<>();
        for (Site site : sites) {
            Integer pageCount = siteCRUDService.getPagesCount(site.getUrl());
            urlPageCountMap.put(site.getUrl(), pageCount);
        }
        return urlPageCountMap;
    }

    private Set<LemmaDto> getLemmasList(String text, String uuid) throws IOException {
        List<String> normalWords = lemmizer.getNormalWords(text);
        Set<LemmaDto> lemmaDtos = new HashSet<>();
        if (uuid == null) {
            for (String normalWord : normalWords) {
                lemmaDtos.addAll(lemmaCRUDService.findByLemma(normalWord));
            }
        } else {
            for (String normalWord : normalWords) {
                lemmaDtos.add(lemmaCRUDService.findByLemmaAndSiteUrl(normalWord, uuid));
            }
        }

        return lemmaDtos;
    }

    private String getPageTitle(PageDto pageDto) {
        String html = pageDto.getContent();
        Document doc = Jsoup.parse(html);
        return doc.title();
    }

}
