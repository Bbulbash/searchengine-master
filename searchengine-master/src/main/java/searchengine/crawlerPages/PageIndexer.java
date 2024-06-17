package searchengine.crawlerPages;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.objects.PageDto;
import searchengine.dto.objects.SiteDto;
import searchengine.lemmizer.Lemmizer;
import searchengine.model.SiteModel;
import searchengine.model.Status;
import searchengine.services.PageCRUDService;
import searchengine.services.SiteCRUDService;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class PageIndexer {
    @Autowired
    private SiteMapManager siteMapManager;
    @Autowired
    private PageCRUDService pageCRUDService;

    @Autowired
    private SiteCRUDService siteCRUDService;
    @Autowired
    private SitesList sitesList;
    @Autowired
    private Lemmizer lemmizer;
    private static final String USER_AGENT = "SEARCH_BOT";
    private String errorMessage = null;
    private boolean success = true;
    private List<Site> listOfSite;

//    public PageIndexer(SiteMapManager siteMapManager, PageCRUDService pageCRUDService, SiteCRUDService siteCRUDService) {
//        this.siteMapManager = siteMapManager;
//        this.pageCRUDService = pageCRUDService;
//        this.siteCRUDService = siteCRUDService;
//    }

    public void indexPage(String url) {
        //Метод проверяющий наличие сайта в репозитории, если его там нет, проверяющий наличие сайта в конфиге, если там есть - создать новый сайт
        if (isIndexingAllow(url)) {
            log.info("Is indexing allow " + isIndexingAllow(url));

            String hostName = getHostName(getUrl(url));
            log.info("Is site exist before delete page " + siteCRUDService.existsByUrl(hostName));
            deleteIfPageExist(hostName);
            log.info("Host name " + hostName);
            try {
                errorMessage = null;
                updateSiteStatus(hostName, errorMessage, Status.INDEXING);
                siteMapManager.setIndexingActive(true);
                log.info("Before initialization pageDto");
                PageDto pageDto = initializationPageDto(url);
                pageCRUDService.create(pageDto);
                PageDto newPage = pageCRUDService.getByPathAndSitePath(pageDto.getPath(), pageDto.getSite());// Новая ошибка тут
                lemmizer.createLemmasAndIndex(newPage);// Множественное создание страниц отсюда
                log.info("After saving page dto object");
                siteMapManager.setIndexingActive(false);
                //updateSiteStatus(hostName, errorMessage, Status.INDEXED);
            } catch (Exception e) {
                success = false;
                errorMessage = e.getMessage();
                //updateSiteStatus(hostName, errorMessage, Status.FAILED);
                System.out.println("-----------1--------");
                System.out.println("Ошбика при обработке URL: " + hostName + " " + e.getMessage());
                System.out.println("-----------------------");
                System.out.println(e.getStackTrace());
            }
        }
    }

    private void deleteIfPageExist(String urlS) {
        Boolean isPageExist = false;
        Long pageId = null;
        URL url = getUrl(urlS);
        String host = getHostName(url);
        log.info("HOST " + host);
        String path = url.getPath();
        log.info("PATH " + path);
        boolean isHostExist = siteCRUDService.existsByUrl(host);
        if (isHostExist) {
            isPageExist = pageCRUDService
                    .getAll().stream().anyMatch(it -> it.getPath().equals(path) && it.getSite().equals(host));
            log.info("Host exist. Is page exist = " + isPageExist);
        }
        if (isPageExist) {
            PageDto pageDto = pageCRUDService
                    .getAll().stream()
                    .filter(it -> it.getPath().equals(path) && it.getSite().equals(host)).findFirst().get();
            pageId = pageDto.getId();
            log.info("Page exist. Page id " + pageId);
            pageCRUDService.delete(pageId);
        }

    }

    public Boolean isIndexingAllow(String url) {
        log.info("Inside is indexing allow. URL - " + url);
        Boolean isIndexingActive = siteMapManager.isIndexingActive;
        log.info("Is indexing active - " + isIndexingActive);
        String host = getHostName(getUrl(url));
        log.info("Host name " + host);
        Boolean isSiteInRepo = siteCRUDService.existsByUrl(host);
        log.info("Is site in repository - " + isSiteInRepo);

        log.info("Is site in site list " + isSiteInSiteList(host));
        if (!isSiteInRepo && isSiteInSiteList(host)) {
            log.info("Creating site ");
            createSite(host);
            isSiteInRepo = true;
        }
        return !isIndexingActive && isSiteInRepo;
    }

    private boolean isSiteInSiteList(String bigUrl) {
        String url = getHostName(getUrl(bigUrl));
        return sitesList.getSites().stream().anyMatch(it -> it.getUrl().equals(url));
    }

    private String getHostName(URL url) {
        return url.getProtocol() + "://" + url.getHost() + "/";
    }

    private String getPagePath(String url) {
        log.info("URL " + getUrl(url).getPath());
        return getUrl(url).getPath();
    }

    private URL getUrl(String url) {
        URL urlAsURL = null;
        try {
            urlAsURL = new URL(url);
        } catch (Exception e) {
            System.err.println("Ошибка при обработке URL: " + e.getMessage());
        }
        return urlAsURL;
    }

    private Connection.Response getResponse(String url) throws IOException {
        Connection.Response response = null;
        try {
            response = Jsoup.connect(url).execute();
        } catch (Exception e) {
            System.err.println("Ошбика получение response: " + url + " " + e.getMessage());
        }
        return response;
    }

    private Document getDocument(String url) throws IOException {
        Document doc = null;
        try {
            doc = Jsoup.connect(url).userAgent(USER_AGENT).referrer("http://www.google.com").get();
        } catch (IOException ex) {
            System.err.println("Ошибка при получении Document " + ex.getMessage());
        }
        return doc;
    }

    private PageDto initializationPageDto(String url) throws IOException {
        URL urlAsUrl = getUrl(url);
        PageDto pageDto = new PageDto();
        pageDto.setSite(getHostName(urlAsUrl));//Корневой url
        pageDto.setCode(getResponse(url).statusCode());
        pageDto.setContent(getDocument(url).body().text());
        pageDto.setPath(getPagePath(url));

        return pageDto;
    }

private void updateSiteStatus(String url, String errorMessage, Status status) {
    SiteDto dto = siteCRUDService.getByUrl(url);
    dto.setStatus(status.name());
    dto.setLastError(errorMessage);
    dto.setStatusTime(LocalDateTime.now().toString());
    siteCRUDService.update(dto);
}


    private void createSite(String url) {
        listOfSite = sitesList.getSites();
        Site site = null;
        Optional<Site> optionalSite = listOfSite.stream().filter(it -> it.getUrl().equals(url)).findFirst();
        if (optionalSite.isPresent()) {
            site = optionalSite.get();
        }
        Long siteId = getNewSiteId();
        SiteDto siteDto = new SiteDto();
        siteDto.setId(siteId);
        log.info("New site id is " + siteId);
        siteDto.setName(site.getName());
        siteDto.setUrl(site.getUrl());
        siteDto.setStatus(Status.INDEXING.name());
        log.info("Url of new site from page indexer " + site.getUrl());
        siteCRUDService.create(siteDto);
    }

    private Long getNewSiteId() {
        if (siteCRUDService.count() == 0) {
            return 1L;
        }
        return siteCRUDService.count() + 1L;
    }

}
