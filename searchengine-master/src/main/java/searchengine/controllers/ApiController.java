package searchengine.controllers;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class ApiController {
    @Autowired
    private final StatisticsService statisticsService;
    @Autowired
    private final IndexingService indexingService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @SneakyThrows
    //@GetMapping("/api/startIndexing")//Метод запуска индексации
    @GetMapping("/startIndexing")
    public ResponseEntity<?> startIndexing() {
        indexingService.createSitesMaps();
        boolean isIndexingActive = indexingService.isIndexingActive();
        log.info("Is indexing active " + isIndexingActive);
        if (!isIndexingActive) {
            return new ResponseEntity<>(Map.of("result", true), HttpStatus.OK);
        } else {
            return new ResponseEntity<>
                    (Map.of("result", false, "error", "Индексация уже запущена"), HttpStatus.CONFLICT);
        }
    }

    @SneakyThrows
    @GetMapping("/stopIndexing")
    public ResponseEntity<?> stopIndexing() {
        boolean isIndexingActive = indexingService.isIndexingActive();
        if (isIndexingActive) {
            indexingService.stopIndexing();
            return new ResponseEntity<>(Map.of("result", true), HttpStatus.OK);
        } else {
            return new ResponseEntity<>
                    (Map.of("result", false, "error", "Индексация не запущена"), HttpStatus.CONFLICT);
        }
    }

}
