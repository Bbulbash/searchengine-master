package searchengine.crawlerPages;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.objects.SiteDto;
import searchengine.model.SiteModel;
import searchengine.model.Status;
import searchengine.repositories.SiteRepository;
import searchengine.services.PageCRUDService;
import searchengine.services.SiteCRUDService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
@Service
@Slf4j
public class SiteMapManager {
    @Autowired
    private SitesList sitesList;
    @Autowired
    private SiteCRUDService siteCRUDService;
    @Autowired
    private PageCRUDService pageCRUDService;
    private volatile boolean isIndexingActive = false;
    ForkJoinPool pool = new ForkJoinPool();

    public void start() throws Exception {
        isIndexingActive = true;
        List<Site> listUrl;
        listUrl = sitesList.getSites();
        log.info("List of url size " + listUrl.size());
        for (Site site : listUrl) {
            String url = site.getUrl();
            log.info("Is site exist by url " + siteCRUDService.existsByUrl(url) + ".Url " + url);
            if(!siteCRUDService.existsByUrl(url)){
                log.warn("Create after exist by url false. Url - " + url);
                createSite(site);
                log.warn("After creating site. Repository size " + siteCRUDService.getAll().size());
            }
            log.info("Url from site map manager " + url);
            SiteMapTask task = new SiteMapTask(url, 0, pageCRUDService, siteCRUDService);
            //pool.invoke(task);
            TaskResult taskResult = pool.invoke(task);
            //isIndexingActive = false;
            updateSiteStatus(url, taskResult);
        }
         isIndexingActive = false;
    }
    private void createSite(Site site){
        Long siteId = getNewSiteId();
        SiteDto siteDto = new SiteDto();
        siteDto.setId(siteId);
        log.info("New site id is " + siteId);
        siteDto.setName(site.getName());
        siteDto.setUrl(site.getUrl());
        siteDto.setStatus(Status.INDEXING.name());
        log.info("Url of new site " + site.getUrl());
        siteCRUDService.create(siteDto);
    }
    private Long getNewSiteId(){
        if(siteCRUDService.count() == 0){
            return 1L;
        }
        return siteCRUDService.count() + 1L;
    }

    private void updateSiteStatus(String url, TaskResult taskResult) {
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
        siteCRUDService.update(SiteCRUDService.mapToDto(model));
    }
    public void stopIndexing(){
        pool.shutdownNow();
        updateStatusAfterStop();
        isIndexingActive = false;
    }
    private void updateStatusAfterStop(){
        List<SiteModel> listModel = siteCRUDService.findAllByStatus(Status.INDEXING.name());
        for(SiteModel model : listModel){
            model.setStatus(Status.FAILED);
            model.setLastError("Индексация остановлена пользователем");
            model.setStatusTime(LocalDateTime.now());
            siteCRUDService.update(SiteCRUDService.mapToDto(model));
        }
    }
    public boolean isIndexingActive(){
        return isIndexingActive;
    }
}
