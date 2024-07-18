package searchengine.crawlerPages;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.NodeList;
import searchengine.dto.objects.PageDto;
import searchengine.lemmizer.Lemmizer;
import searchengine.services.PageCRUDService;
import searchengine.services.SiteCRUDService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ForkJoinTask;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
//@Service
public class SiteMapTask extends RecursiveTask<TaskResult> {
    private final SiteMapManager siteMapManager;
    private static final Object lock = new Object();
    private static final String USER_AGENT = "SEARCH_BOT";
    private final String url;
    private final int level;
    protected static Set<String> visited; //= new ConcurrentHashMap<>();
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
        synchronized (lock) {
            siteVisitedMap.putIfAbsent(siteId, ConcurrentHashMap.newKeySet());
        }
        this.visited = siteVisitedMap.get(siteId);
        this.siteMapManager = siteMapManager;

    }

    @Override
    protected TaskResult compute() {
        synchronized (visited) {
            if (!visited.add(url)) {
                if (url.contains("/posts/arrays-in-java/")){
                    log.warn("");
                }
                return new TaskResult(true, "URL already visited");
            }
        }
        Boolean success = true;
        String errorMessage = null;
        if (siteMapManager.isIndexingActive() == true) {

            try {
                if (url.contains("/posts/arrays-in-java/")) {
                    log.warn("");
                }
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
                String rootUrl = urlAsURL.getProtocol() + "://" + urlAsURL.getHost() + "/";
                log.info("Root url " + rootUrl);
                PageDto pageDto = new PageDto();
                pageDto.setSite(rootUrl);//Корневой url
                pageDto.setCode(response.statusCode());
                pageDto.setContent(doc.body().text());
                pageDto.setPath(pathFromRoot);
                log.info("Before saving page dto object " + pageDto.getPath());
                if (!pageCRUDService.isPageExists(pathFromRoot, siteId)) {
                    pageCRUDService.create(pageDto);
                    lemmizer.createLemmasAndIndex(pageCRUDService.getByPathAndSitePath(pathFromRoot, rootUrl));
                }
                List<String> abstrUrls = new ArrayList<>();
                List<ForkJoinTask<TaskResult>> tasks = new ArrayList<>();
                Elements links = doc.select("a[href]");
                links.stream().forEach(link -> {
                    String absUrl = normalizeUrl(link.absUrl("href"));
                    if (absUrl.contains("/posts/arrays-in-java/")) {
                        log.warn("");
                    }
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
                log.info("some log 1");
                System.err.println("Ошибка при обработке URL: " + url + " " + e.getMessage());
                log.info("some log 2");
            }

            return new TaskResult(success, errorMessage);
        }else  return new TaskResult(false, "Indexing stopped");
    }

    private boolean isValidUrl(String url, String absUrl) {
        return absUrl.startsWith(url) && !absUrl.equals(url) && !absUrl.contains("#") && !absUrl.equals(url + "/");
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