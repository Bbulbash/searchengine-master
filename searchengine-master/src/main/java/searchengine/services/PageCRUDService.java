package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.objects.IndexDto;
import searchengine.dto.objects.LemmaDto;
import searchengine.dto.objects.PageDto;
import searchengine.dto.objects.SiteDto;
import searchengine.model.*;
import searchengine.repositories.PageRepository;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
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

    @Transactional
    @Override
    public PageDto getById(Long id) {
        try {
            return pageRepository.findById(id.intValue())
                    .map(this::mapToDto)
                    .orElseThrow(() -> new EntityNotFoundException("Page with id " + id + " not found."));
        } catch (EntityNotFoundException ex) {
            log.warn("Page with id {} not found: {}", id, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("An unexpected error occurred while finding the page by id: {}", id, ex);
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

    @Transactional
    public PageDto getByPathAndSitePath(String pagePath, String sitePath) {
        try {
            log.info("Find page by path and site url size " + pageRepository.findAll().size());
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
    public void create(PageDto item) {
        PageModel pageM = mapToModel(item);

        log.warn("From page CRUD Service. Page model get site url == " + pageM.getSite().getUrl());
        SiteModel siteM = siteCRUDService.findByUrl(pageM.getSite().getUrl());
        Optional<PageModel> optionalModel = pageRepository.findByPathAndSiteUrl(pageM.getPath(), siteM.getUrl()).stream().findFirst();

        if (optionalModel.isPresent()) {
            //delete(optionalModel.get().getId());
            log.warn("optionalModel.isPresent()");
        } else {
            log.warn("Don`t present");
        }

        if (siteM != null) {
            siteM.setStatusTime(LocalDateTime.now());
            siteM.setStatus(Status.INDEXING);
            SiteDto siteDto = SiteCRUDService.mapToDto(siteM);
            siteCRUDService.update(siteDto);
        } else {
            log.error("Cannot find SiteModel with URL: " + pageM.getSite().getUrl());
            throw new EntityNotFoundException("Site model not found for URL: " + pageM.getSite().getUrl());
        }
        log.warn("New page id " + pageM.getId());
        pageRepository.saveAndFlush(pageM);
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
        log.info("Delete page " + id);
        if (pageRepository.existsById(id.intValue())) {

            List<IndexDto> indexes = indexCRUDService.getAll().stream().filter(it -> it.getPageId().equals(id)).toList();
            log.info("Found {} indexes for page id {}", indexes.size(), id);

            indexes.forEach(index -> {
                LemmaDto lemma = lemmaCRUDService.getById(Long.valueOf(index.getLemmaId()));
                lemma.setFrequency(lemma.getFrequency() - 1);
                lemmaCRUDService.update(lemma);

                IndexKey key = new IndexKey(index.getPageId(), index.getLemmaId());
                indexCRUDService.delete(key);
            });
            List<IndexDto> indexForLog = indexCRUDService.getAll().stream().filter(it -> it.getPageId().equals(id)).toList();
            log.warn("indexForLog size " + indexForLog.size());
            log.warn("lemma repo is empty " + lemmaCRUDService.isServiceEmpty());
            log.warn("Second check is page exist "+ pageRepository.existsById(id.intValue()));

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
        SiteModel siteM = siteCRUDService.findByUrl(pageDto.getSite());
        if (siteM == null) {
            log.error("SiteModel not found for URL: " + pageDto.getSite());
            log.info("Site repo size " + siteCRUDService.findAll().size());
            throw new EntityNotFoundException("SiteModel not found for URL: " + pageDto.getSite());
        }
        //pageM.setId(pageId);
        pageM.setSite(siteM);
        pageM.setPath(pageDto.getPath());
        pageM.setCode(pageDto.getCode());
        pageM.setContent(pageDto.getContent());

        return pageM;
    }

    @Transactional
    public PageModel mapToModelWithId(PageDto pageDto) {
        PageModel pageM = new PageModel();
        SiteModel siteM = siteCRUDService.findByUrl(pageDto.getSite());
        if (siteM == null) {
            log.error("SiteModel not found for URL: " + pageDto.getSite());
            log.info("Site repo size " + siteCRUDService.findAll().size());
            throw new EntityNotFoundException("SiteModel not found for URL: " + pageDto.getSite());
        }
        pageM.setId(pageDto.getId());
        pageM.setSite(siteM);
        pageM.setPath(pageDto.getPath());
        pageM.setCode(pageDto.getCode());
        pageM.setContent(pageDto.getContent());

        return pageM;
    }

    //@Transactional
    public void deleteBySiteId(Long id) {
        List<PageModel> models = pageRepository.findAllBySiteId(id);
        for (PageModel page : models) {
            delete(page.getId());
        }

    }
}
