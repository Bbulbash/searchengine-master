package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AsyncService {
   /* @Autowired
             private IndexingService indexingService;

    @Async("taskExecutor")
    public void startIndexing() {
        if (!indexingService.isIndexingActive()) {
            indexingService.createSitesMaps();
        } else {
            log.warn("Indexing is already running");
        }
    }*/
}
