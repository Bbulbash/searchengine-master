package searchengine.crawlerPages;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.objects.SiteDto;
import searchengine.lemmizer.Lemmizer;
import searchengine.model.SiteModel;
import searchengine.model.Status;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingService;
import searchengine.services.PageCRUDService;
import searchengine.services.SiteCRUDService;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Service
@Slf4j
@Setter
public class SiteMapManager {
    @Autowired
    private SitesList sitesList;
    @Autowired
    private SiteCRUDService siteCRUDService;
    @Autowired
    private PageCRUDService pageCRUDService;
    @Autowired
    private Lemmizer lemmizer;
    public volatile boolean isIndexingActive = false;
    ForkJoinPool pool = new ForkJoinPool();

    public void start() throws Exception {
        Long start = System.currentTimeMillis();
        isIndexingActive = true;
        List<Site> listUrl;
        listUrl = sitesList.getSites();
        log.info("List of url size " + listUrl.size());
        for (Site site : listUrl) {
            String url = site.getUrl();
            log.info("Is site exist by url 1" + siteCRUDService.existsByUrl(url) + ". Url " + url);
            if(siteCRUDService.existsByUrl(url)) siteCRUDService.delete(siteCRUDService.findByUrl(url).getId());
            log.info("Is site exist by url 2" + siteCRUDService.existsByUrl(url) + ". Url " + url);
            if (!siteCRUDService.existsByUrl(url)) {
                log.warn("Create after exist by url false. Url - " + url);
                createSite(site);
                log.warn("After creating site. Repository size " + siteCRUDService.getAll().size());
            }
            log.info("Url from site map manager " + url);
            SiteMapTask task = new SiteMapTask(url, 0, pageCRUDService, siteCRUDService, lemmizer);
            TaskResult taskResult = pool.invoke(task);
            updateSiteStatus(url, taskResult);
        }
        isIndexingActive = false;
        System.err.println("Start time - finish time = " + (System.currentTimeMillis() - start));
    }

    private void createSite(Site site) {
        //Long siteId = getNewSiteId();
        SiteDto siteDto = new SiteDto();
        //siteDto.setId(siteId);
        log.info("New site creating");
        siteDto.setName(site.getName());
        siteDto.setUrl(site.getUrl());
        siteDto.setStatus(Status.INDEXING.name());
        log.info("Url of new site " + site.getUrl());
        siteCRUDService.create(siteDto);
    }

    private void updateSiteStatus(String url, TaskResult taskResult) throws Exception {
        SiteModel model = siteCRUDService.findByUrl(url);
        Boolean success = taskResult.getSuccess();
        String errorMessage = taskResult.getErrorMessage();
        if (success) {
            model.setStatus(Status.INDEXED);
        } else {
            model.setStatus(Status.FAILED);
        }
        model.setLastError(errorMessage);
        model.setStatusTime(LocalDateTime.now());
        try {
            siteCRUDService.update(SiteCRUDService.mapToDto(model));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void stopIndexing(){
        pool.shutdownNow();
        updateStatusAfterStop();
        isIndexingActive = false;
    }

    private void updateStatusAfterStop(){
        List<SiteModel> listModel = siteCRUDService.findAllByStatus(Status.INDEXING.name());
        for (SiteModel model : listModel) {
            model.setStatus(Status.FAILED);
            model.setLastError("Индексация остановлена пользователем");
            model.setStatusTime(LocalDateTime.now());
            siteCRUDService.update(SiteCRUDService.mapToDto(model));
        }
    }

    public boolean isIndexingActive() {
        return isIndexingActive;
    }
}
