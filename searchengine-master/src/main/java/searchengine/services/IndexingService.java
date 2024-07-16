package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.config.Site;
import searchengine.crawlerPages.PageIndexer;
import searchengine.crawlerPages.SiteMapManager;
import searchengine.model.SiteModel;
import java.util.List;
@Service
public class IndexingService {
    @Autowired
    private SitesList siteList;
    @Autowired
    private SiteCRUDService siteCRUDService;
    @Autowired
    private SiteMapManager siteMapManager;
    @Autowired
    private PageIndexer pageIndexer;

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
    @Async("taskExecutor")
    public void createSitesMaps() throws Exception {
        siteMapManager.setIndexingActive(true);
        deleteSitesData();
        siteMapManager.start();
    }
    @Transactional
    public boolean isIndexingActive() {
        return siteMapManager.isIndexingActive();
    }

    @Async("taskExecutor")
    @Transactional
    public void stopIndexing(){
        siteMapManager.stopIndexing();
    }
    @Transactional
    public boolean isAllowIndexingPage(String url){
        return pageIndexer.isIndexingAllow(url);
    }
    @Async("taskExecutor")
    public void startIndexingPage(String url){
        pageIndexer.indexPage(url);
    }

}
