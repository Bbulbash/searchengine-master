package searchengine.crawlerPages;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import searchengine.dto.objects.PageDto;
import searchengine.lemmizer.Lemmizer;
import searchengine.model.SiteModel;
import searchengine.repositories.SiteRepository;
import searchengine.services.PageCRUDService;
import searchengine.services.SiteCRUDService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ForkJoinTask;
import java.util.HashSet;
import java.util.Set;
import java.net.URL;

@Slf4j
//@Service
public class SiteMapTask extends RecursiveTask<TaskResult> {
    private static final String USER_AGENT = "SEARCH_BOT";
    private final String url;
    private final int level;
    private static final Set<String> visited = new HashSet<>();
    private PageCRUDService pageCRUDService;
    private SiteCRUDService siteCRUDService;
    private Lemmizer lemmizer;
    public SiteMapTask(String url, int level, PageCRUDService pageCRUDService,
                       SiteCRUDService siteCRUDService, Lemmizer lemmizer) {
        this.url = url;
        this.level = level;
        this.pageCRUDService = pageCRUDService;
        this.siteCRUDService = siteCRUDService;
        this.lemmizer = lemmizer;

    }

    @Override
    protected TaskResult compute() {
        synchronized (visited) {
            if (!visited.add(url)) {
                return null;
            }
        }
        Boolean success = true;
        String errorMessage = null;
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
            String pathFromRoot = urlAsURL.getPath();
            log.info("Path from url " + pathFromRoot);
            String rootUrl = urlAsURL.getProtocol() + "://" + urlAsURL.getHost() + "/";
            log.info("Root url " + rootUrl);
            Boolean isServiceEmpty = pageCRUDService.getAll().isEmpty();
            log.info(" Is page crud service is empty " + isServiceEmpty);
            // Long idPage = (isServiceEmpty) ? 1L : Long.parseLong(String.valueOf(pageCRUDService.getAll().size())) + 1L;
            PageDto pageDto = new PageDto();
           // pageDto.setId(idPage);
            pageDto.setSite(rootUrl);//Корневой url
            pageDto.setCode(response.statusCode());
            pageDto.setContent(doc.body().text());
            pageDto.setPath(pathFromRoot);
           // log.info("From site repository " + siteCRUDService.findAll().size());
            log.info("Before saving page dto object " + pageDto.getPath());

            pageCRUDService.create(pageDto);
            //Здесь дописать индексацию новой страницы
            lemmizer.createLemmasAndIndex(pageCRUDService.getByPathAndSitePath(pathFromRoot, rootUrl));
            log.info("After saving page dto object");
            log.info("Doc body text " + doc.body());
            List<ForkJoinTask<TaskResult>> tasks = new ArrayList<>();
            Elements links = doc.select("a[href]");
            links.stream().forEach(link -> {
                String absUrl = link.absUrl("href");
                if (isValidUrl(url, absUrl)) {
                    SiteMapTask task = new SiteMapTask(absUrl, level + 1, pageCRUDService, siteCRUDService, lemmizer);
                    tasks.add(task.fork());
                }
            });
            for(ForkJoinTask<TaskResult> task : tasks){
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
    }

    private boolean isValidUrl(String url, String absUrl) {
        return absUrl.startsWith(url) && !absUrl.equals(url) && !absUrl.contains("#") && !absUrl.equals(url + "/");
    }

}
