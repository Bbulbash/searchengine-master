package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.config.Site;
import searchengine.crawlerPages.SiteMapManager;
import searchengine.model.SiteModel;
import searchengine.repositories.SiteRepository;

import java.util.List;

@Service
public class IndexingService {
    @Autowired
    private SitesList siteList;
    @Autowired
    private SiteRepository repository;
    @Autowired
    private SiteMapManager siteMapManager;

    @Transactional
    private void deleteSitesData() {
        List<Site> sites = siteList.getSites();
        for (Site site : sites) {
            SiteModel existingSite = repository.findByUrl(site.getUrl());
            if (existingSite != null) {
                repository.delete(existingSite);
            }
        }
    }

    @Transactional
    public void createSitesMaps() throws Exception {
        siteMapManager.start();
    }

    @Transactional
    public boolean isIndexingActive() {
        return siteMapManager.isIndexingActive();
    }

    @Transactional
    public void stopIndexing() {
        siteMapManager.stopIndexing();
    }

}
