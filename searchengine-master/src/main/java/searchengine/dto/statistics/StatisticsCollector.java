package searchengine.dto.statistics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.SiteModel;
import searchengine.services.IndexingService;
import searchengine.services.LemmaCRUDService;
import searchengine.services.PageCRUDService;
import searchengine.services.SiteCRUDService;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StatisticsCollector {
    @Autowired
    PageCRUDService pageCRUDService;
    @Autowired
    SiteCRUDService siteCRUDService;
    @Autowired
    IndexingService indexingService;
    @Autowired
    LemmaCRUDService lemmaCRUDService;
    public StatisticsResponse getStatistics() {
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData statisticsData = new StatisticsData();

        TotalStatistics total = new TotalStatistics();
        total.setSites(siteCRUDService.getSitesCount());
        total.setPages(pageCRUDService.getPagesCount());
        total.setLemmas(lemmaCRUDService.getLemmasCount());
        total.setIndexing(indexingService.isIndexingActive());

        List<SiteModel> sites = siteCRUDService.findAll();
        List<DetailedStatisticsItem> detailedItems = sites.stream().map(site -> {
            ZonedDateTime zoneDateTime = site.getStatusTime().atZone(ZoneId.of("Europe/Moscow"));
            long sec = zoneDateTime.toInstant().toEpochMilli();
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setUrl(site.getUrl());
            item.setName(site.getName());
            item.setStatus(site.getStatus().toString());
            item.setStatusTime(sec);
            item.setPages(site.getPages().size());
            item.setLemmas(siteCRUDService.getLemmasCountBySiteId(site.getId()));

            if (site.getLastError() != null && !site.getLastError().isEmpty()) {
                item.setError(site.getLastError());
            }

            return item;
        }).collect(Collectors.toList());

        statisticsData.setTotal(total);
        statisticsData.setDetailed(detailedItems);

        response.setResult(true);
        response.setStatistics(statisticsData);

        return response;
    }
}
