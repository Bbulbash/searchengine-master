package searchengine.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
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
public class SiteCRUDService  {

    private final SiteRepository siteRepository;

    @Transactional
    public SiteDto getById(UUID uuid) {
        return mapToDto(siteRepository.findById(uuid).get());
    }
    @Transactional
    public SiteDto getByUrl(String url){
        return mapToDto(siteRepository.findByUrl(url));
    }
    @Transactional
    public int getPagesCount(String url){
        SiteModel model = siteRepository.findByUrl(url);
        return model.getPages().size();
    }

    @Transactional
    public Collection<SiteDto> getAll() {
        List<SiteModel> list = siteRepository.findAll();
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(SiteCRUDService::mapToDto).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = {RuntimeException.class, EntityNotFoundException.class, Exception.class})
    public void update(SiteDto item) throws Exception {
        String itemUrl = item.getUrl();
        Optional<SiteModel> optionalSiteModel = Optional.ofNullable(siteRepository.findByUrl(itemUrl));
        if (!optionalSiteModel.isPresent()) {
            log.info("Site model with id " + item.getId() + " not found");
            throw new EntityNotFoundException("Site model with id " + item.getId() + " not found");
        } else {
            SiteModel existingSiteM = optionalSiteModel.get();
            try {
                log.info("Existing site model " + existingSiteM.getName());
                existingSiteM.setLastError(item.getLastError());
                existingSiteM.setStatusTime(LocalDateTime.parse(item.getStatusTime()));
                existingSiteM.setStatus(Status.valueOf(item.getStatus()));
                siteRepository.save(existingSiteM);
            }catch (Exception ex){
                throw new Exception("Trabls with site update " + ex.getMessage());
            }
        }
    }

    @Transactional
    public SiteDto create(SiteDto siteDto) {
        SiteModel siteM = mapToModel(siteDto);
        siteM.setStatus(Status.INDEXING);
        siteM.setStatusTime(LocalDateTime.now());
        siteM.setUrl(siteDto.getUrl());
        return mapToDto(siteRepository.save(siteM));
    }
    public int getLemmasCountBySiteId(UUID uuid){
        return getSiteModelById(uuid).getLemmaModels().size();
    }
    @Transactional
    private SiteModel getSiteModelById(UUID uuid){
        return siteRepository.findById(uuid).get();
    }

    @Transactional
    public void delete(UUID uuid) {
        if (siteRepository.existsById(uuid)) {
            log.warn("Delete site by id");
            siteRepository.deleteById(uuid);
        } else {
            throw new EntityNotFoundException("Site not found");
        }
    }
    @Transactional
    public void deleteAll() throws Exception {
        try{
            siteRepository.deleteAll();
        }catch (Exception ex){
            throw new Exception("Problems with deleting all site");
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
    public int getSitesCount(){return findAll().size();}
    @Transactional
    public Boolean existsByUrl(String url){
        return siteRepository.existsByUrl(url);
    }
    @Transactional
    public long count(){
        return siteRepository.count();
    }
    @Transactional
    public List<SiteModel> findAllByStatus(Status status){
        return siteRepository.findAllByStatus(status);
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
        siteM.setUrl(siteDto.getUrl());
        siteM.setName(siteDto.getName());
        siteM.setStatus(Status.valueOf(siteDto.getStatus()));
        siteM.setStatusTime(LocalDateTime.now());
        siteM.setLastError(siteDto.getLastError());
        return siteM;
    }

    public SiteDto generateSiteDto(Site site){
        SiteDto dto = new SiteDto();
        dto.setUrl(site.getUrl());
        dto.setName(site.getName());
        return dto;
    }
}
