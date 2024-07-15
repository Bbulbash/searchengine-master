package searchengine.controllers;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.StatisticsCollector;
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
    @Autowired
    private final StatisticsCollector statisticsCollector;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService, StatisticsCollector statisticsCollector) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.statisticsCollector = statisticsCollector;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsCollector.getStatistics());
    }

    @SneakyThrows
    @GetMapping("/startIndexing")
    public ResponseEntity<?> startIndexing() {
        boolean isIndexingActive = indexingService.isIndexingActive();
        log.info("Is indexing active " + isIndexingActive);
        if (!isIndexingActive) {
            indexingService.createSitesMaps();
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

    @SneakyThrows
    @PostMapping("/indexPage")
    public ResponseEntity<?> indexPage(@RequestParam(name = "url") String url) {// Если индексация уже запущена - выкидывать ошибку
        log.info("URL inside API " + url);
        boolean isAllowIndexingPage = indexingService.isAllowIndexingPage(url);
        if (isAllowIndexingPage) {
            indexingService.startIndexingPage(url);
            return new ResponseEntity<>(Map.of("result", true), HttpStatus.OK);
        } else {
            return new ResponseEntity<>
                    (Map.of("result", false, "error", "Данная страница находится за пределами сайтов,\n" +
                            "указанных в конфигурационном файле"), HttpStatus.CONFLICT);
        }
    }

}
