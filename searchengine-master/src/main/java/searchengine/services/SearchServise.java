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
public class SearchServise {
    @Autowired
    private SitesList sitesList;
    private final PageCRUDService pageCRUDService;
    private final SiteCRUDService siteCRUDService;
    private final Lemmizer lemmizer;
    private final IndexCRUDService indexCRUDService;
    private final LemmaCRUDService lemmaCRUDService;

    public SearchServise(PageCRUDService pageCRUDService, SiteCRUDService siteCRUDService, Lemmizer lemmizer, IndexCRUDService indexCRUDService, LemmaCRUDService lemmaCRUDService) {
        this.pageCRUDService = pageCRUDService;
        this.siteCRUDService = siteCRUDService;
        this.lemmizer = lemmizer;
        this.indexCRUDService = indexCRUDService;
        this.lemmaCRUDService = lemmaCRUDService;
    }

    public Map<String, Object> search(String query, String site, int offset, int limit)
            throws IOException {
        Map<String, Object> response = new HashMap<>();
        Set<SearchResult> results = search(query, site);

        if (results.isEmpty()) {
            response.put("result", false);
            response.put("error", "Лемма не найдена");
            return response;
        }

        List<SearchResult> pagedResults = results.stream().toList();
        int total = pagedResults.size();
        List<SearchResult> listToReturn = pagedResults.subList(offset, Math.min(total, offset + limit));
        response.put("result", true);
        response.put("count", total);
        response.put("data", listToReturn);

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

    private Set<PageDto> filterPagesByAllLemmas(Set<LemmaDto> lemmaDtos) {
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
            searchResult.setSite(siteUrl);
            searchResult.setSiteName(siteName);
            searchResult.setUri(page.getPath());
            searchResult.setTitle(getPageTitle(page));
            searchResult.setSnippet(findQueryInText(page.getContent(), query));
            searchResult.setRelevance(relevance);
            if (searchResult.getSnippet() == null) break;

            result.add(searchResult);
        }
        return result;
    }

    private String findQueryInText(String text, String query) throws IOException {
        List<String> normalQueryList = lemmizer.getNormalWords(query);
        String[] textWords = text.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : textWords) {
            List<String> normalizedWords = lemmizer.getNormalWords(word);
            for (String normalizedWord : normalizedWords) {
                if (normalQueryList.contains(normalizedWord)) {
                    System.out.println("Contains ");
                    result.append("<b>").append(word).append("</b>");
                } else {
                    result.append(word);
                }
            }
            result.append(" ");
            System.out.println(result);
        }
        return getSnippet(result.toString().trim());
    }

    private String getSnippet(String text) {
        Integer indexWord = 0;
        String patternString = "<b>(.*?)</b>";
        Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        while  (matcher.find()) {
            String word = matcher.group(1);
            indexWord = text.indexOf("<b>" + word + "</b>");
            break;
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
