package searchengine.crawlerPages;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
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
import searchengine.model.Status;
import searchengine.services.PageCRUDService;
import searchengine.services.SiteCRUDService;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class PageIndexer {
    private final SiteMapManager siteMapManager;
    private final PageCRUDService pageCRUDService;
    private final SiteCRUDService siteCRUDService;
    @Autowired
    private SitesList sitesList;
    private final Lemmizer lemmizer;
    private static final String USER_AGENT = "SEARCH_BOT";
    private String errorMessage = null;
    private boolean success = true;
    private List<Site> listOfSite;

    public PageIndexer(SiteMapManager siteMapManager, PageCRUDService pageCRUDService, SiteCRUDService siteCRUDService, Lemmizer lemmizer) {
        this.siteMapManager = siteMapManager;
        this.pageCRUDService = pageCRUDService;
        this.siteCRUDService = siteCRUDService;
        this.lemmizer = lemmizer;
    }

    public void indexPage(String url) throws Exception {
        if (isIndexingAllow(url)) {
            String hostName = getHostName(getUrl(url));
            log.info("Is site exist before delete page " + siteCRUDService.existsByUrl(hostName));

            deleteIfSiteExist(hostName);

            createSite(hostName);
            try {

                siteMapManager.isIndexingActive.set(true);
                log.info("Before initialization pageDto");
                PageDto pageDto = initializationPageDto(url);
                pageCRUDService.create(pageDto);
                PageDto newPage = pageCRUDService.getByPathAndSitePath(pageDto.getPath(), pageDto.getSite());// Новая ошибка тут
                lemmizer.createLemmasAndIndex(newPage);
                log.info("After saving page dto object");
                siteMapManager.isIndexingActive.set(false);
            } catch (Exception e) {
                success = false;
                errorMessage = e.getMessage();
                updateSiteStatus(hostName, errorMessage, Status.FAILED);
                log.error("Ошбика при обработке URL: " + hostName + " " + e.getMessage());
                log.error(Arrays.toString(e.getStackTrace()));
            }
        }
    }

    private void deleteIfSiteExist(String url) {
        boolean isHostExist = siteCRUDService.existsByUrl(url);
        if (isHostExist) {
            SiteDto dto = siteCRUDService.findByUrlSiteDto(url);
            siteCRUDService.delete(UUID.fromString(dto.getId()));
        }
    }

    public Boolean isIndexingAllow(String url) {
        AtomicBoolean isIndexingActive = siteMapManager.isIndexingActive;
        String host = getHostName(getUrl(url));
        Boolean isSiteInRepo = siteCRUDService.existsByUrl(host);
        if (!isSiteInRepo && isSiteInSiteList(host)) {
            log.info("Creating site ");
            createSite(host);
            isSiteInRepo = true;
        }
        return !isIndexingActive.get() && isSiteInRepo;
    }

    private boolean isSiteInSiteList(String bigUrl) {
        String url = getHostName(getUrl(bigUrl));
        return sitesList.getSites().stream().anyMatch(it -> it.getUrl().equals(url));
    }

    private String getHostName(URL url) {
        return url.getProtocol() + "://" + url.getHost() + "/";
    }

    private String getPagePath(String url) {
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

    private void updateSiteStatus(String url, String errorMessage, Status status) throws Exception {
        SiteDto dto = siteCRUDService.getByUrl(url);
        dto.setUrl(url);
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
        SiteDto siteDto = new SiteDto();

        siteDto.setName(site.getName());
        siteDto.setUrl(site.getUrl());
        siteDto.setStatus(Status.INDEXING.name());
        log.info("Url of new site from page indexer " + site.getUrl());
        siteCRUDService.create(siteDto);
    }

}
