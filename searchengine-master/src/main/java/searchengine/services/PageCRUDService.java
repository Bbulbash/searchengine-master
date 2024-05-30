package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.objects.PageDto;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import javax.persistence.EntityNotFoundException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageCRUDService implements CRUDService<PageDto> {

    private final PageRepository pageRepository;

    private final SiteCRUDService siteCRUDService;

    @Transactional
    @Override
    public PageDto getById(Long id) {
        return mapToDto(pageRepository.findById(id.intValue()).get());
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
    @Override
    public void create(PageDto item) {
        PageModel pageM = mapToModel(item);
        log.warn("From page CRUD Service. Page model get site url == " + pageM.getSite().getUrl());
        log.warn("From page CRUD Service. Site repo size " + siteCRUDService.findAll().size());
        SiteModel siteM = siteCRUDService.findByUrl(pageM.getSite().getUrl());
        if (siteM != null) {
            siteM.setStatusTime(LocalDateTime.now());
            siteM.setStatus(Status.INDEXING);
            siteCRUDService.update(siteCRUDService.mapToDto(siteM));
        } else {
            log.error("Cannot find SiteModel with URL: " + pageM.getSite().getUrl());
            throw new EntityNotFoundException("Site model not found for URL: " + pageM.getSite().getUrl());
        }
        pageRepository.saveAndFlush(pageM);

    }

    @Transactional
    @Override
    public void update(PageDto item) {
        PageModel existingPageM= pageRepository.findById(item.getId().intValue())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("From page CRUD service. Page not found"));
        PageModel pageModel = mapToModel(item);
        pageRepository.saveAndFlush(pageModel);
    }

    @Transactional
    @Override
    public void delete(Long id) {
        log.info("Delete site " + id.toString());
        if (pageRepository.existsById(id.intValue())) {
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

        if (siteM == null) {
            log.error("SiteModel not found for URL: " + pageDto.getSite());
            log.info("Site repo size " + siteCRUDService.findAll().size());
            throw new EntityNotFoundException("SiteModel not found for URL: " + pageDto.getSite());
        }

        pageM.setSite(siteM);
        pageM.setPath(pageDto.getPath());
        pageM.setCode(pageDto.getCode());
        pageM.setContent(pageDto.getContent());

        return pageM;
    }
}
