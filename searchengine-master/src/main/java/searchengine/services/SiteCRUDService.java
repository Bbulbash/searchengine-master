package searchengine.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.objects.SiteDto;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.model.Status;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteCRUDService implements CRUDService<SiteDto> {
    @Autowired
    private final SiteRepository repository;

    @Override
    public SiteDto getById(Long id) {
        return mapToDto(repository.findById(id.intValue()).get());
    }

    @Override
    public Collection<SiteDto> getAll() {
        List<SiteModel> list = repository.findAll();
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(it -> mapToDto(it)).collect(Collectors.toList());
    }

    @Override
    public void update(SiteDto item) {
        SiteModel siteM = mapToModel(item);
        repository.saveAndFlush(siteM);
    }

    @Transactional
    @Override
    public void create(SiteDto siteDto) {
        SiteModel siteM = mapToModel(siteDto);
        siteM.setStatus(Status.INDEXING);
        log.info("From site CRUD servise. Site status = " + siteM.getStatus());
        siteM.setStatusTime(LocalDateTime.now());
        siteM.setUrl(siteDto.getUrl());
        log.info("From site CRUD service. Status time = " + siteM.getStatusTime());
        log.info("From site CRUD service. Url = " + siteM.getUrl());
        log.info("From site CRUD service. Name = " + siteM.getName());
        log.info("Repository size before creating site " + repository.findAll().size());
        repository.saveAndFlush(siteM);
        log.info("Site " + siteM.getUrl() + " was saved");
        log.info("Repository size after creating site " + repository.findAll().size());

    }

    @Override
    public void delete(Long id) {
        log.info("Delete site " + id.toString());
        if (repository.existsById(id.intValue())) {
            repository.deleteById(id.intValue());
        } else {
            throw new EntityNotFoundException("Site not found");
        }
    }

    public static SiteDto mapToDto(SiteModel siteModel) {
        SiteDto siteDto = new SiteDto();
        siteDto.setName(siteModel.getName());
        siteDto.setUrl(siteModel.getUrl());
        return siteDto;
    }

    /* public static SiteModel mapToModel(SiteDto siteDto) {
         SiteModel siteM = new SiteModel();
         siteM.setUrl(siteDto.getUrl());
         siteM.setName(siteDto.getName());
         return siteM;
     }*/
    public static SiteModel mapToModel(SiteDto siteDto) {
        SiteModel siteM = new SiteModel();
        siteM.setId(siteM.getId());
        siteM.setUrl(siteM.getUrl());
        siteM.setName(siteDto.getName());
        //siteM.setStatus(Status.valueOf(status));
        //siteM.setStatusTime(LocalDateTime.now());
        siteM.setLastError(siteDto.getLastError());
        return siteM;
    }

    public static SiteDto mapToDto(searchengine.config.Site site) {
        SiteDto siteDto = new SiteDto();
        siteDto.setName(site.getName());
        siteDto.setUrl(site.getUrl());
        return siteDto;
    }
    /*public static SiteDto mapToDto(SiteModel siteM){
        SiteDto siteDto = new SiteDto();
        siteDto.setUrl();
    }*/

}
