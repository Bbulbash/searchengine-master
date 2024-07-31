package searchengine.services;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.config.Site;
import searchengine.crawlerPages.PageIndexer;
import searchengine.crawlerPages.SiteMapManager;
import searchengine.model.SiteModel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
@Slf4j
public class IndexingService {
    @Autowired
    private SitesList siteList;
    private final SiteCRUDService siteCRUDService;
    private final SiteMapManager siteMapManager;
    private final PageIndexer pageIndexer;
    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(10);
    }
    public IndexingService(SiteCRUDService siteCRUDService, SiteMapManager siteMapManager, PageIndexer pageIndexer) {
        this.siteCRUDService = siteCRUDService;
        this.siteMapManager = siteMapManager;
        this.pageIndexer = pageIndexer;
    }


    @Transactional
    public void deleteSitesData() {
        List<Site> sites = siteList.getSites();
        for (Site site : sites) {
            SiteModel existingSite = siteCRUDService.findByUrl(site.getUrl());
            if (existingSite != null) {
                siteCRUDService.delete(existingSite.getId());
            }
        }
    }

    public void createSitesMaps() {
        try {
            siteMapManager.isIndexingActive.set(true);
            deleteSitesData();
            siteMapManager.start();
        } catch (Exception e) {
            log.error("Exception during site maps creation", e);
        }
    }
    public boolean isIndexingActive() {
        return siteMapManager.isIndexingActive();
    }

    @Async("taskExecutor")
    public void stopIndexing() {
        siteMapManager.stopIndexing();
    }

    public boolean isAllowIndexingPage(String url) {
        return pageIndexer.isIndexingAllow(url);
    }

    @Async("taskExecutor")
    public void startIndexingPage(String url) throws Exception {
        pageIndexer.indexPage(url);
    }

    public void startIndexing(){
        executorService.submit(() -> {
            createSitesMaps();
        });
    }

    public ResponseEntity<?> startIndexingSync() {
        if (!isIndexingActive()) {
            startIndexing();
            return ResponseEntity.ok(Map.of("result", true));
        } else {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("result", false, "error", "Индексация уже запущена"));
        }
    }

    public ResponseEntity<?> stopIndexingSites() {
        boolean isIndexingActive = isIndexingActive();
        if (isIndexingActive) {
            stopIndexing();
            return new ResponseEntity<>(Map.of("result", true), HttpStatus.OK);
        } else {
            return new ResponseEntity<>
                    (Map.of("result", false, "error", "Индексация не запущена"), HttpStatus.CONFLICT);
        }
    }

    public ResponseEntity<?> indexPage(String url) throws Exception {
        log.info("URL inside API " + url);
        boolean isAllowIndexingPage = isAllowIndexingPage(url);
        if (isAllowIndexingPage) {
            startIndexingPage(url);
            return new ResponseEntity<>(Map.of("result", true), HttpStatus.OK);
        } else {
            return new ResponseEntity<>
                    (Map.of("result", false, "error", "Данная страница находится за пределами сайтов,\n" +
                            "указанных в конфигурационном файле"), HttpStatus.CONFLICT);
        }
    }

}
