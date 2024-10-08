package searchengine.crawlerPages;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import searchengine.dto.objects.PageDto;
import searchengine.lemmizer.Lemmizer;
import searchengine.services.PageCRUDService;
import searchengine.services.SiteCRUDService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ForkJoinTask;
import java.net.URL;

@Slf4j
public class SiteMapTask extends RecursiveTask<TaskResult> {
    private final SiteMapManager siteMapManager;
    private static final String USER_AGENT = "SEARCH_BOT";
    private final String url;
    private final int level;
    protected static Set<String> visited;
    private static final Map<String, Set<String>> siteVisitedMap = new ConcurrentHashMap<>();
    private PageCRUDService pageCRUDService;
    private SiteCRUDService siteCRUDService;
    private Lemmizer lemmizer;
    private final String siteId;

    public SiteMapTask(String url, int level, PageCRUDService pageCRUDService,
                       SiteCRUDService siteCRUDService, Lemmizer lemmizer, String siteId, SiteMapManager siteMapManager) {
        this.url = url;
        this.level = level;
        this.pageCRUDService = pageCRUDService;
        this.siteCRUDService = siteCRUDService;
        this.lemmizer = lemmizer;
        this.siteId = siteId;
        siteVisitedMap.putIfAbsent(siteId, ConcurrentHashMap.newKeySet());

        this.visited = siteVisitedMap.get(siteId);
        this.siteMapManager = siteMapManager;

    }

    @Override
    protected TaskResult compute() {

        if (!visited.add(url)) {
            return new TaskResult(true, "URL already visited");
        }

        Boolean success = true;
        String errorMessage = null;
        if (siteMapManager.isIndexingActive() == true) {

            try {
                Thread.sleep(100);
                Connection.Response response = Jsoup.connect(url).execute();
                Document doc = Jsoup.connect(url).userAgent(USER_AGENT).referrer("http://www.google.com").get();
                URL urlAsURL = null;
                try {
                    urlAsURL = new URL(url);
                } catch (Exception e) {
                    System.err.println("Ошибка при обработке URL: " + e.getMessage());
                }
                log.warn("url 1111" + url);
                String pathFromRoot = normalizePath(urlAsURL.getPath());
                log.info("Path from url " + pathFromRoot);
                String rootUrl = urlAsURL.getProtocol() + "://" + urlAsURL.getHost();
                log.info("Root url " + rootUrl);
                PageDto pageDto = new PageDto();
                pageDto.setSite(rootUrl);
                pageDto.setCode(response.statusCode());

                pageDto.setContent(doc.toString());
                pageDto.setPath(pathFromRoot);

                if (!pageCRUDService.isPageExists(pathFromRoot, siteId)) {
                    pageCRUDService.create(pageDto);
                    lemmizer.createLemmasAndIndex(pageCRUDService.getByPathAndSitePath(pathFromRoot, rootUrl));
                }
                List<String> abstrUrls = new ArrayList<>();
                List<ForkJoinTask<TaskResult>> tasks = new ArrayList<>();
                Elements links = doc.select("a[href]");
                links.stream().forEach(link -> {
                    String absUrl = normalizeUrl(link.absUrl("href"));

                    if (absUrl.startsWith(rootUrl) && !absUrl.contains("#")
                            && !absUrl.contains(".xml") && !absUrl.contains(".css")) {
                        SiteMapTask task =
                                new SiteMapTask(absUrl, level + 1, pageCRUDService, siteCRUDService, lemmizer, siteId, siteMapManager);
                        tasks.add(task.fork());
                    } else {
                        abstrUrls.add(absUrl);
                    }
                });
                for (ForkJoinTask<TaskResult> task : tasks) {
                    task.join();
                }
            } catch (Exception e) {
                success = false;
                errorMessage = e.getMessage();
                System.err.println("Ошибка при обработке URL: " + url + " " + e.getMessage());

            }

            return new TaskResult(success, errorMessage);
        } else return new TaskResult(false, "Indexing stopped");
    }

    private String normalizeUrl(String url) {
        if (!url.endsWith("/")) {
            return url.concat("/");
        }
        return url;
    }

    private String normalizePath(String path) {
        if (!path.endsWith("/")) {
            return path.concat("/");
        }
        return path;
    }

}