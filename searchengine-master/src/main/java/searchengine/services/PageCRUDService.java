package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.objects.IndexDto;
import searchengine.dto.objects.LemmaDto;
import searchengine.dto.objects.PageDto;
import searchengine.dto.objects.SiteDto;
import searchengine.model.*;
import searchengine.repositories.PageRepository;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Getter
@RequiredArgsConstructor
public class PageCRUDService implements CRUDService<PageDto> {

    private final PageRepository pageRepository;

    private final SiteCRUDService siteCRUDService;
    @Autowired
    private IndexCRUDService indexCRUDService;
    @Autowired
    private LemmaCRUDService lemmaCRUDService;
    @Autowired
    private SitesList siteList;

    @Transactional
    @Override
    public PageDto getById(Long id) {
        try {
            return pageRepository.findById(id.intValue())
                    .map(this::mapToDto)
                    .orElseThrow(() -> new EntityNotFoundException("Page with id " + id + " not found."));
        } catch (EntityNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("An unexpected error occurred while finding the page by id: " + id, ex);
        }
    }

    @Transactional
    @Override
    public Collection<PageDto> getAll() {
        List<PageModel> list = pageRepository.findAll();
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public int getPagesCount() {
        return getAll().size();
    }

    @Transactional
    public PageDto getByPathAndSitePath(String pagePath, String sitePath) {
        try {
            return mapToDto(pageRepository.findByPathAndSiteUrl(pagePath, sitePath).stream().findFirst().get());
        } catch (EntityNotFoundException ex) {
            log.warn("Page with path {} not found: {}", pagePath, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("An unexpected error occurred while finding the page by path: {}", pagePath, ex.fillInStackTrace());
            throw new RuntimeException("An unexpected error occurred while finding the page by path: " + pagePath, ex);
        }
    }

    @Transactional
    @Override
    public void create(PageDto item) throws Exception {
        PageModel pageM = mapToModel(item);
        SiteModel siteM = siteCRUDService.findByUrl(pageM.getSite().getUrl());

        if (!List.of(siteM).isEmpty()) {
            log.warn("optionalModel.isPresent()");
        } else {
            Site site = siteList.getSites().stream().filter(it -> it.getUrl().equals(pageM.getSite().getUrl())).findFirst().get();
            SiteDto dto = siteCRUDService.generateSiteDto(site);
            siteCRUDService.create(dto);
        }

        if (siteM != null) {
            siteM.setStatusTime(LocalDateTime.now());
            siteM.setStatus(Status.INDEXING);
            SiteDto siteDto = SiteCRUDService.mapToDto(siteM);
            siteCRUDService.update(siteDto);
        } else {
            throw new EntityNotFoundException("Site model not found for URL: " + pageM.getSite().getUrl());
        }
        pageM.setContent(parseHtml(item.getContent()));
        pageRepository.save(pageM);
    }

    private String parseHtml(String html){
        Document document = Jsoup.parse(html);
        return document.body().text();
    }

    @Transactional
    @Override
    public void update(PageDto item) {
        PageModel pageModel = mapToModel(item);
        pageRepository.save(pageModel);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Override
    public void delete(Long id) {
        if (pageRepository.existsById(id.intValue())) {

            List<IndexDto> indexes = indexCRUDService.getAll().stream().filter(it -> it.getPageId().equals(id)).toList();

            indexes.forEach(index -> {
                LemmaDto lemma = lemmaCRUDService.getById(Long.valueOf(index.getLemmaId()));
                lemma.setFrequency(lemma.getFrequency() - 1);
                lemmaCRUDService.update(lemma);

            });
            pageRepository.deleteById(id.intValue());

        } else {
            throw new EntityNotFoundException("Page not found with id: " + id);
        }
    }

    public PageDto mapToDto(PageModel page) {
        PageDto pageDto = new PageDto();
        pageDto.setId(page.getId());
        pageDto.setCode(page.getCode());
        pageDto.setSite(page.getSite().getUrl());
        pageDto.setContent(page.getContent());
        pageDto.setPath(page.getPath());

        return pageDto;
    }

    @Transactional
    public PageModel mapToModel(PageDto pageDto) {
        PageModel pageM = new PageModel();
        SiteModel siteM = siteCRUDService.findByUrl(pageDto.getSite().replaceAll("/$", ""));
        if (siteM == null) {
            log.error("SiteModel not found for URL: " + pageDto.getSite());
            throw new EntityNotFoundException("SiteModel not found for URL: " + pageDto.getSite());
        }

        pageM.setSite(siteM);
        pageM.setPath(pageDto.getPath());
        pageM.setCode(pageDto.getCode());
        pageM.setContent(pageDto.getContent().toString());

        return pageM;
    }

    @Transactional
    public PageModel mapToModelWithId(PageDto pageDto) {
        PageModel pageM = new PageModel();
        SiteModel siteM = siteCRUDService.findByUrl(pageDto.getSite());
        if (siteM == null) {
            log.error("SiteModel not found for URL: " + pageDto.getSite());
            throw new EntityNotFoundException("SiteModel not found for URL: " + pageDto.getSite());
        }
        pageM.setId(pageDto.getId());
        pageM.setSite(siteM);
        pageM.setPath(pageDto.getPath());
        pageM.setCode(pageDto.getCode());
        pageM.setContent(pageDto.getContent());

        return pageM;
    }

    @Transactional
    public Boolean isPageExists(String path, String uuid) {
        return pageRepository.existsByPathAndSiteId(path, UUID.fromString(uuid));
    }

    @Transactional
    public Set<PageDto> findPagesByIds(Set<Long> pageIds) {
        return pageRepository.findAllByIdIn(pageIds).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }
}
