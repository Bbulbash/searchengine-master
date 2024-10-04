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
public class SearchServise3 {
    @Autowired
    private SitesList sitesList;
    private final PageCRUDService pageCRUDService;
    private final SiteCRUDService siteCRUDService;
    private final Lemmizer lemmizer;
    private final IndexCRUDService indexCRUDService;
    private final LemmaCRUDService lemmaCRUDService;

    public SearchServise3(PageCRUDService pageCRUDService, SiteCRUDService siteCRUDService, Lemmizer lemmizer, IndexCRUDService indexCRUDService, LemmaCRUDService lemmaCRUDService) {
        this.pageCRUDService = pageCRUDService;
        this.siteCRUDService = siteCRUDService;
        this.lemmizer = lemmizer;
        this.indexCRUDService = indexCRUDService;
        this.lemmaCRUDService = lemmaCRUDService;
    }

    public Map<String, Object> search(String query, String site, int offset, int limit)
            throws IOException{
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

    public Set<SearchResult> search(String text, String url) throws IOException {
        Set<LemmaDto> lemmaDtos = getLemmasList(text, url);
        Set<LemmaDto> lessPopularLemmas = getLessPopularLemmas(lemmaDtos).stream()
                .sorted(Comparator.comparingInt(LemmaDto::getFrequency))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<PageDto> matchingPages = filterPagesByAllLemmas(lessPopularLemmas);
        HashMap<PageDto, Float> sortedByRelevancePage = getPagesSortByRelevance(matchingPages);

        return convertToSearchResult(sortedByRelevancePage, text);
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
                LemmaDto lemma = lemmaCRUDService.findByLemmaAndSiteUrl(normalWord, uuid);
                if (lemma != null) lemmaDtos.add(lemma);
            }
        }
        return lemmaDtos;
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

    private Set<PageDto> filterPagesByAllLemmas(Set<LemmaDto> lemmaDtos) throws IOException {
        Iterator<LemmaDto> lemmaIterator = lemmaDtos.iterator();
        if (!lemmaIterator.hasNext()) {
            return Collections.emptySet();
        }

        LemmaDto firstLemma = lemmaIterator.next();
        Set<PageDto> pages = getPagesWithLemma(firstLemma);
        Set<PageDto> pagesWithFirstLemma = new HashSet<>(pages);

        while (lemmaIterator.hasNext()) {
            LemmaDto nextLemma = lemmaIterator.next();
            Set<PageDto> nextPages = getPagesWithNextLemma(pages, nextLemma);
            pages.retainAll(nextPages);
        }
        if (pages.size() == 0) return pagesWithFirstLemma;
        return pages;
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

    private Set<SearchResult> convertToSearchResult(HashMap<PageDto, Float> sortedPages, String query) throws IOException {
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
            searchResult.setSnippet(findQueryInText(page.getContent(), query));
            searchResult.setRelevance(relevance);
            if (searchResult.getSnippet() == null) break;

            result.add(searchResult);
        }
        return result;
    }

    private String findQueryInText(String text, String query) throws IOException {
        List<String> normalFormsText = lemmizer.getNormalWords(text);
        List<String> normalQuery = lemmizer.getNormalWords(query);
        List<String> commonElements = new ArrayList<>(normalFormsText);
        commonElements.retainAll(normalQuery);
        List<String> textAsList = lemmizer.getTextAsList(text);
        boolean isFirst = false;
        Integer indexWord = 0;
        for (String word : textAsList) {
            List<String> wordAsList = new ArrayList<>();
            wordAsList.add(word);
            if (commonElements.contains(lemmizer.getNormalWords(wordAsList).get(0))) {
                text = text.replace(word, "<b>" + word + "</b>");
                if (!isFirst) {
                    isFirst = true;
                    indexWord = text.indexOf(word);
                }

            }
        }
        int snippetStart = Math.max(0, indexWord - 100);
        int snippetEnd = Math.min(text.length(), indexWord + 100);
        String snippet = text.substring(snippetStart, snippetEnd).trim();

        if (snippet != null) {
            snippet = "..." + snippet + "...";
        }
        return snippet;

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

    private Set<PageDto> getPagesWithLemma(LemmaDto dto) {
        Set<Long> lemmaId = Collections.singleton(dto.getId());
        Set<IndexDto> indexDtos = indexCRUDService.findAllByLemmaId(lemmaId);
        Set<Long> pageIds = new HashSet<>();
        for (IndexDto index : indexDtos) {
            pageIds.add(index.getPageId());
        }
        return pageCRUDService.findPagesByIds(pageIds);
    }

    private Set<PageDto> getPagesWithNextLemma(Set<PageDto> pages, LemmaDto lemmaDto) {
        Set<PageDto> pagesWithOldAndNextLemmas = new HashSet<>();
        Set<PageDto> pagesThisLemma = getPagesWithLemma(lemmaDto);

        for (PageDto pageDto : pagesThisLemma) {
            Long pageDtoId = pageDto.getId();
            if (pages.stream().anyMatch(it -> it.getId().equals(pageDtoId))) pagesWithOldAndNextLemmas.add(pageDto);
        }
        if (pagesWithOldAndNextLemmas.isEmpty()) return pages;
        return pagesWithOldAndNextLemmas;
    }

    private float calculateAbsoluteRelevancy(Set<IndexDto> indexByPage) {
        float relevancySum = 0.0f;
        for (IndexDto lemma : indexByPage) {
            relevancySum += lemma.getRankValue();
        }
        return relevancySum;
    }

    private String getPageTitle(PageDto pageDto) {
        String html = pageDto.getContent();
        Document doc = Jsoup.parse(html);
        return doc.title();
    }

}
