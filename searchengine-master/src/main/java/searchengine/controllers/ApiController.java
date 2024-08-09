package searchengine.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.StatisticsCollector;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.SearchServise2;
import searchengine.services.SearchServise3;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class ApiController {
    private final IndexingService indexingService;
    private final StatisticsCollector statisticsCollector;
    private final SearchService searchService;
    private final SearchServise3 searchServise3;

    public ApiController(IndexingService indexingService, StatisticsCollector statisticsCollector, SearchService searchService, SearchServise3 searchServise3) {
        this.indexingService = indexingService;
        this.statisticsCollector = statisticsCollector;
        this.searchService = searchService;
        this.searchServise3 = searchServise3;
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
    public ResponseEntity<?> indexPage(@RequestParam(name = "url") String url) throws Exception {
        return indexingService.indexPage(url);
    }
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(name = "query") String query,
            @RequestParam(name = "site", required = false) String site,
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {

        try {
            Map<String, Object> response = searchServise3.search(query, site, offset, limit);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("result", false, "error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("result", false, "error", "Внутренняя ошибка сервера"));
        }
    }
}