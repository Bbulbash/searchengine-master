package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.objects.PageDto;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
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
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private SiteCRUDService siteCRUDService;

    @Override
    public PageDto getById(Long id) {
        return null;
//        log.info("Get page by id " + id.toString());
//        PageModel pageM = pageRepository.getById(Math.toIntExact(id))
//                .orElseThrow(()-> new EntityNotFoundException("Page with id " + id + " not found."));
//        return mapToDto(pageM);
    }

    @Override
    public Collection<PageDto> getAll() {
        List<PageModel> list = pageRepository.findAll();
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(it -> mapToDto(it)).collect(Collectors.toList());
    }

    @Override
    public void create(PageDto item) {
        PageModel pageM = mapToModel(item);
        log.warn("From page CRUD Service. Page model get site url == " + pageM.getSite().getUrl());
        log.warn("From page CRUD Service. Site repo size " + siteRepository.findAll().size() + ". Repo hash " + siteRepository.hashCode());
        SiteModel siteM = siteRepository.findByUrl(pageM.getSite().getUrl());
        if (siteM != null) {
            siteM.setStatusTime(LocalDateTime.now());
            siteCRUDService.update(siteCRUDService.mapToDto(siteM));
        } else {
            log.error("Cannot find SiteModel with URL: " + pageM.getSite().getUrl());
            throw new EntityNotFoundException("Site model not found for URL: " + pageM.getSite().getUrl());
        }
        pageRepository.save(pageM);
    }

    @Override
    public void update(PageDto item) {

    }

    @Override
    public void delete(Long id) {

    }

    public PageDto mapToDto(PageModel page) {
        PageDto pageDto = new PageDto();
        pageDto.setCode(page.getCode());
        pageDto.setSite(page.getSite().getUrl());
        pageDto.setContent(page.getContent());
        pageDto.setPath(page.getPath());

        return pageDto;
    }
    public PageModel mapToModel(PageDto pageDto) {
        PageModel pageM = new PageModel();
        SiteModel siteM = siteRepository.findByUrl(pageDto.getSite());

        if (siteM == null) {
            log.error("SiteModel not found for URL: " + pageDto.getSite());
            log.info("Site repo size " + siteRepository.findAll().size());
            throw new EntityNotFoundException("SiteModel not found for URL: " + pageDto.getSite());
        }

        pageM.setSite(siteM);
        pageM.setPath(pageDto.getPath());
        pageM.setCode(pageDto.getCode());
        pageM.setContent(pageDto.getContent());

        return pageM;
    }
}
