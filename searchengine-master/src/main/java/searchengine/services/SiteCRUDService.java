package searchengine.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.dto.objects.PageDto;
import searchengine.dto.objects.SiteDto;
import searchengine.model.SiteModel;
import searchengine.model.Status;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class SiteCRUDService  {//implements CRUDService<SiteDto>

    private final SiteRepository siteRepository;
    @Autowired
    private PageCRUDService pageCRUDService;
    //@Override
    @Transactional
    public SiteDto getById(UUID uuid) {
        return mapToDto(siteRepository.findById(uuid).get());
    }
    @Transactional
    public SiteDto getByUrl(String url){
        return mapToDto(siteRepository.findByUrl(url));
    }

   // @Override
    @Transactional
    public Collection<SiteDto> getAll() {
        List<SiteModel> list = siteRepository.findAll();
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(SiteCRUDService::mapToDto).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = {RuntimeException.class, EntityNotFoundException.class, Exception.class})
   // @Override
    public void update(SiteDto item) {
        //Optional<SiteModel> optionalSiteModel = siteRepository.findById(Math.toIntExact(item.getId()));
       // Integer siteRepoSize = siteRepository.findAll().size();
        String itemUrl = item.getUrl();
        Optional<SiteModel> optionalSiteModel = Optional.ofNullable(siteRepository.findByUrl(itemUrl));
        if (!optionalSiteModel.isPresent()) {
            log.warn("Site repo size " + siteRepository.findAll().size());
            log.info("Site model with id " + item.getId() + " not found");
            throw new EntityNotFoundException("Site model with id " + item.getId() + " not found");
        } else {
            SiteModel existingSiteM = optionalSiteModel.get();
            try {
                log.info("Existing site model " + existingSiteM.getName());
                existingSiteM.setLastError(item.getLastError());
                existingSiteM.setStatusTime(LocalDateTime.parse(item.getStatusTime()));
                existingSiteM.setStatus(Status.valueOf(item.getStatus()));
                siteRepository.saveAndFlush(existingSiteM);
            }catch (Exception ex){
                log.warn("Exception in update site " + ex.getMessage());
            }
        }
    }

    @Transactional
    //@Override
    public void create(SiteDto siteDto) {
        SiteModel siteM = mapToModel(siteDto);
        siteM.setStatus(Status.INDEXING);
       // log.info("From site CRUD service. Site status = " + siteM.getStatus());
        siteM.setStatusTime(LocalDateTime.now());
        siteM.setUrl(siteDto.getUrl());
        //siteRepository.saveAndFlush(siteM);
        siteRepository.save(siteM);
        log.info("Site " + siteM.getUrl() + " was saved");
        log.info("Repository size after creating site " + siteRepository.findAll().size());

    }

    @Transactional
   // @Override
    public void delete(UUID uuid) {
        log.info("Delete site " + uuid.toString());
        if (siteRepository.existsById(uuid)) {
            //pageCRUDService.deleteBySiteUUID(uuid);
            log.warn("Delete site by id");
            siteRepository.deleteById(uuid);
        } else {
            throw new EntityNotFoundException("Site not found");
        }
    }
    @Transactional
    public SiteModel findByUrl(String url){
        return siteRepository.findByUrl(url);
    }
    @Transactional
    public SiteDto findByUrlSiteDto(String url){
        return mapToDto(siteRepository.findByUrl(url));
    }
    @Transactional
    public List<SiteModel> findAll(){
        return siteRepository.findAll();
    }
    @Transactional
    public Boolean existsByUrl(String url){
        return siteRepository.existsByUrl(url);
    }
    @Transactional
    public long count(){
        return siteRepository.count();
    }
    @Transactional
    public List<SiteModel> findAllByStatus(String statusName){
        return siteRepository.findAllByStatus(statusName);
    }

    public static SiteDto mapToDto(SiteModel siteModel) {
        SiteDto siteDto = new SiteDto();
        siteDto.setId(siteModel.getId().toString());
        siteDto.setStatus(siteModel.getStatus().name());
        siteDto.setStatusTime(siteModel.getStatusTime().toString());
        siteDto.setLastError(siteModel.getLastError());
        siteDto.setName(siteModel.getName());
        siteDto.setUrl(siteModel.getUrl());
        return siteDto;
    }
    public static SiteModel mapToModel(SiteDto siteDto) {
        SiteModel siteM = new SiteModel();
        //siteM.setId(UUID.fromString(siteDto.getId()));
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
    public SiteDto generateSiteDto(Site site){
        SiteDto dto = new SiteDto();
        dto.setUrl(site.getUrl());
        dto.setName(site.getName());
        return dto;
    }
}
