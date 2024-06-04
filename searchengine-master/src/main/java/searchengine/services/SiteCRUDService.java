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
import java.util.*;
import java.util.stream.Collectors;
import javax.persistence.PersistenceContext;
import javax.persistence.EntityManager;


@Slf4j
@Service
@RequiredArgsConstructor
public class SiteCRUDService implements CRUDService<SiteDto> {

    private final SiteRepository repository;
    @Override
    @Transactional
    public SiteDto getById(Long id) {
        return mapToDto(repository.findById(id.intValue()).get());
    }
    @Transactional
    public SiteDto getByUrl(String url){
        return mapToDto(repository.findByUrl(url));
    }

    @Override
    @Transactional
    public Collection<SiteDto> getAll() {
        List<SiteModel> list = repository.findAll();
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(it -> mapToDto(it)).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = {RuntimeException.class, EntityNotFoundException.class, Exception.class})
    @Override
    public void update(SiteDto item) {
        log.info("Inside updating site in site CRUD " + repository.findAll().stream().filter(it -> it.getId() == 52).toList().size());
        //SiteModel existingSiteM = repository.findById(item.getId().intValue()).get();
         //       .orElseThrow(() -> new EntityNotFoundException("From site CRUD service. Site with id " + item.getId() + " not found"));
        //Optional<SiteModel> existingSiteM = repository.findAll().stream().filter(it -> it.getId().equals(it.getId())).findFirst();
        //SiteModel model = setNewValueToEntity(item, existingSiteM.get());
        //log.info("Existing site model " + existingSiteM.getName());
        Optional<SiteModel> optionalSiteModel = repository.findById(Math.toIntExact(item.getId()));
        if(optionalSiteModel.isPresent()){
            SiteModel existingSiteM = optionalSiteModel.get();
            try {
                log.info("Existing site model " + existingSiteM.getName());
                existingSiteM.setLastError(item.getLastError());
                existingSiteM.setStatusTime(LocalDateTime.parse(item.getStatusTime()));
                existingSiteM.setStatus(Status.valueOf(item.getStatus()));
                repository.saveAndFlush(existingSiteM);
            }catch (Exception ex){
                log.warn("Exception in update site " + ex.getMessage());
            }
        }else{
            log.info("Site model with id " + item.getId() + " not found");
            throw new EntityNotFoundException("Site model with id " + item.getId() + " not found");
        }

    }

    @Transactional
    @Override
    public void create(SiteDto siteDto) {
        SiteModel siteM = mapToModel(siteDto);
        siteM.setStatus(Status.INDEXING);
        log.info("From site CRUD servise. Site status = " + siteM.getStatus());
        siteM.setStatusTime(LocalDateTime.now());
        siteM.setUrl(siteDto.getUrl());
        repository.saveAndFlush(siteM);
        log.info("Site " + siteM.getUrl() + " was saved");
        log.info("Repository size after creating site " + repository.findAll().size());

    }

    @Transactional
    @Override
    public void delete(Long id) {
        log.info("Delete site " + id.toString());
        if (repository.existsById(id.intValue())) {
            repository.deleteById(id.intValue());
        } else {
            throw new EntityNotFoundException("Site not found");
        }
    }
    @Transactional
    public SiteModel findByUrl(String url){
        return repository.findByUrl(url);
    }
    @Transactional
    public List<SiteModel> findAll(){
        return repository.findAll();
    }
    @Transactional
    public Boolean existsByUrl(String url){
        return repository.existsByUrl(url);
    }
    @Transactional
    public long count(){
        return repository.count();
    }
    @Transactional
    public List<SiteModel> findAllByStatus(String statusName){
        return repository.findAllByStatus(statusName);
    }

    public static SiteDto mapToDto(SiteModel siteModel) {
        SiteDto siteDto = new SiteDto();
        siteDto.setId(siteModel.getId());
        siteDto.setStatus(siteModel.getStatus().name());
        siteDto.setStatusTime(siteModel.getStatusTime().toString());
        siteDto.setLastError(siteModel.getLastError());
        siteDto.setName(siteModel.getName());
        siteDto.setUrl(siteModel.getUrl());
        return siteDto;
    }
    public static SiteModel mapToModel(SiteDto siteDto) {
        SiteModel siteM = new SiteModel();
        siteM.setId(siteDto.getId());
        siteM.setUrl(siteDto.getUrl());
        siteM.setName(siteDto.getName());
        siteM.setStatus(Status.valueOf(siteDto.getStatus()));
        siteM.setStatusTime(LocalDateTime.now());
        siteM.setLastError(siteDto.getLastError());
        return siteM;
    }

    public static SiteDto mapToDto(searchengine.config.Site site) {
        SiteDto siteDto = new SiteDto();
        siteDto.setName(site.getName());
        siteDto.setUrl(site.getUrl());
        return siteDto;
    }
    private static SiteModel setNewValueToEntity(SiteDto dto, SiteModel model){
        model.setStatus(Status.valueOf(dto.getStatus()));
        model.setStatusTime(LocalDateTime.parse(dto.getStatusTime()));
        model.setLastError(dto.getLastError());
        return model;
    }


}
