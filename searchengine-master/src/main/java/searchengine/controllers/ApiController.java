package searchengine.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.StatisticsCollector;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
@Slf4j
public class ApiController {

    @Autowired
    private StatisticsService statisticsService;
    @Autowired
    private IndexingService indexingService;
    @Autowired
    private StatisticsCollector statisticsCollector;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService, StatisticsCollector statisticsCollector) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.statisticsCollector = statisticsCollector;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsCollector.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<?> startIndexing() {
        return indexingService.startIndexingSync();
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<?> stopIndexing() {
        return indexingService.stopIndexingSites();
    }

    @PostMapping("/indexPage")
    public ResponseEntity<?> indexPage(@RequestParam(name = "url") String url) {
        return indexingService.indexPage(url);
    }
}