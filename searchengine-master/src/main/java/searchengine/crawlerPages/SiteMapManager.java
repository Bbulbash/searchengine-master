package searchengine.crawlerPages;

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
import searchengine.services.PageCRUDService;
import searchengine.services.SiteCRUDService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;


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
    public AtomicBoolean isIndexingActive = new AtomicBoolean(false);
    ForkJoinPool pool;

    public void start() throws Exception {
        pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        Long start = System.currentTimeMillis();
        isIndexingActive.set(true);
        List<Site> listUrl;
        listUrl = sitesList.getSites();
        siteCRUDService.deleteAll();
        for (Site site : listUrl) {
            String url = site.getUrl();
            String siteId = null;
            if (!siteCRUDService.existsByUrl(url) && isIndexingActive.get() == true) {
                SiteDto siteDto = createSite(site);
                siteId = siteDto.getId();
            }
            if(isIndexingActive.get() == true){
                SiteMapTask task = new SiteMapTask(url, 0, pageCRUDService, siteCRUDService, lemmizer, siteId, this);
                TaskResult taskResult = pool.invoke(task);
                updateSiteStatus(url, taskResult);
                if(isIndexingActive.get() == false){
                    updateSiteStatus(url, new TaskResult(false, "Индексация остановлена пользователем"));
                }
            }
        }

            isIndexingActive.set(false);


        System.err.println("Start time - finish time = " + (System.currentTimeMillis() - start));
    }

    private SiteDto createSite(Site site) {
        SiteDto siteDto = new SiteDto();

        siteDto.setName(site.getName());
        siteDto.setUrl(site.getUrl());
        siteDto.setStatus(Status.INDEXING.name());

        return siteCRUDService.create(siteDto);
    }

    private void updateSiteStatus(String url, TaskResult taskResult){
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

    public void stopIndexing() {
        try {
            isIndexingActive.set(false);
            pool.shutdownNow();
            updateStatusAfterStop();
        } catch (Exception e) {
            log.error("Проблема с остановкой индексации ", e);
            Thread.currentThread().interrupt();
        } finally {
            isIndexingActive.set(false);
        }
    }

    private void updateStatusAfterStop() throws Exception {
        List<SiteModel> listModel = siteCRUDService.findAllByStatus(Status.INDEXING);
        for (SiteModel model : listModel) {
            model.setStatus(Status.FAILED);
            model.setLastError("Индексация остановлена пользователем");
            model.setStatusTime(LocalDateTime.now());
            siteCRUDService.update(SiteCRUDService.mapToDto(model));
        }
    }

    public boolean isIndexingActive() {
        return isIndexingActive.get();
    }

}
