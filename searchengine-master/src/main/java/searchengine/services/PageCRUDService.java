package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.objects.IndexDto;
import searchengine.dto.objects.PageDto;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import javax.persistence.EntityNotFoundException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

    @Transactional
    @Override
    public PageDto getById(Long id) {
        try {
            return pageRepository.findById(id.intValue())
                    .map(this::mapToDto)
                    .orElseThrow(() -> new EntityNotFoundException("Page with id " + id + " not found."));
        } catch (EntityNotFoundException ex) {
            log.warn("Page with id {} not found: {}", id, ex.getMessage());
            throw ex;  // или ваше собственное исключение
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
        return list.stream().map(it -> mapToDto(it)).collect(Collectors.toList());
    }

    @Transactional
    public PageDto getByPathAndSitePath(String pagePath, String sitePath) {
        try {
            return mapToDto(pageRepository.findByPathAndSiteUrl(pagePath, sitePath));
        } catch (EntityNotFoundException ex) {
            log.warn("Page with path {} not found: {}", pagePath, ex.getMessage());
            throw ex;  // или ваше собственное исключение
        } catch (Exception ex) {
            log.error("An unexpected error occurred while finding the page by path: {}", pagePath, ex);
            throw new RuntimeException("An unexpected error occurred while finding the page by path: " + pagePath, ex);
        }
    }

    @Transactional
    @Override
    public void create(PageDto item) {
        //Long pageId = pageRepository.count() == 0 ? 1l : pageRepository.count() + 1L;
        PageModel pageM = mapToModel(item);
        //pageM.setId(pageId);
        log.warn("From page CRUD Service. Page model get site url == " + pageM.getSite().getUrl());
        log.warn("From page CRUD Service. Site repo size " + siteCRUDService.findAll().size());
        SiteModel siteM = siteCRUDService.findByUrl(pageM.getSite().getUrl());
        PageModel model = pageRepository.findByPathAndSiteUrl(pageM.getPath(), siteM.getUrl());
        if (model.getId() != null) {
            delete(model.getId());
        }

        if (siteM != null) {
            siteM.setStatusTime(LocalDateTime.now());
            siteM.setStatus(Status.INDEXING);
            siteCRUDService.update(siteCRUDService.mapToDto(siteM));
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
        PageModel existingPageM = pageRepository.findById(item.getId().intValue())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("From page CRUD service. Page not found"));
        PageModel pageModel = mapToModel(item);
        pageRepository.saveAndFlush(pageModel);
    }

    @Transactional
    @Override
    public void delete(Long id) {
        log.info("Delete site " + id.toString());
        if (pageRepository.existsById(id.intValue())) {
            //Дописать удаление связанных индексов
            log.info("Index service size " + indexCRUDService.getAll().size());
            List<IndexDto> indexes = indexCRUDService.getAll().stream().filter(it -> it.getPageId() == id).toList();
            log.info("Is indexes present " + indexes.size());
            for (IndexDto dto : indexes.stream().toList()) {
                //IndexKey key =
                Long indexId = Long.parseLong(dto.getPageId().toString().concat(String.valueOf(dto.getLemmaId())));
                indexCRUDService.delete(indexId);
            }
            pageRepository.deleteById(id.intValue());
        } else {
            throw new jakarta.persistence.EntityNotFoundException("Page not found");
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
        Long pageId = (pageRepository.findAll().size() == 0) ? 1L : pageRepository.findAll().size() + 1L;
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
}
