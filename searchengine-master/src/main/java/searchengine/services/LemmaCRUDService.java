package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.objects.LemmaDto;
import searchengine.model.LemmaModel;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LemmaCRUDService implements CRUDService<LemmaDto> {
    private final LemmaRepository lemmaRepository;

    private final SiteCRUDService siteCRUDService;

    @Override
    @Transactional
    public LemmaDto getById(Long id) {
        return mapToDto(lemmaRepository.findById(id.intValue()).get());
    }

    @Transactional
    public LemmaDto getByLemmaAndSiteId(String lemma, Long siteId) {
        log.info("Before get by lemma and site. Is empty " + lemmaRepository.findAll().isEmpty());

        Optional<LemmaModel> modelO = lemmaRepository
                .findAll().stream().filter(it -> it.getLemma().equals(lemma) && it.getSite().getId().equals(siteId)).findFirst();
        if (modelO.isPresent()) {
            return mapToDto(modelO.get());
        }
        return null;
    }

    @Override
    @Transactional
    public Collection<LemmaDto> getAll() {
        List<LemmaModel> list = lemmaRepository.findAll();
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(it -> mapToDto(it)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void create(LemmaDto item) {
        LemmaModel lemmaM = mapToModel(item);
        log.warn("From page CRUD Service. Page model get site url == " + lemmaM.getSite().getUrl());
        log.warn("From page CRUD Service. Site repo size " + siteCRUDService.findAll().size());
        SiteModel siteM = siteCRUDService.findByUrl(lemmaM.getSite().getUrl());
        if (siteM != null) {
            siteM.setStatusTime(LocalDateTime.now());
            siteM.setStatus(Status.INDEXING);
            siteCRUDService.update(siteCRUDService.mapToDto(siteM));
        } else {
            log.error("Cannot find lemma with URL: " + lemmaM.getSite().getUrl());
            throw new EntityNotFoundException("Site model not found for URL: " + lemmaM.getSite().getUrl());
        }
        log.info("Before saving lemma into method create");
        lemmaRepository.saveAndFlush(lemmaM);
    }

    @Override
    @Transactional
    public void update(LemmaDto item) {
        //LemmaModel lemmaM = mapToModel(item);
        //lemmaRepository.saveAndFlush(lemmaM);
        LemmaModel existingLemma = lemmaRepository.findById(Math.toIntExact(item.getId()))
                .orElseThrow(() -> new EntityNotFoundException("Lemma not found"));
        SiteModel siteM = siteCRUDService.findByUrl(item.getSiteUrl());

        if (siteM == null) {
            log.error("SiteModel not found for URL: " + item.getSiteUrl());
            log.info("Site repo size " + siteCRUDService.findAll().size());
            throw new EntityNotFoundException("SiteModel not found for URL: " + item.getSiteUrl());
        }
        existingLemma.setLemma(item.getLemma());
        existingLemma.setFrequency(item.getFrequency());
        existingLemma.setSite(siteM);

        // Сохраните обновленную сущность
        lemmaRepository.saveAndFlush(existingLemma);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Delete lemma " + id.toString());
        //  log.info("Lemma site " + lemmaRepository.findAll()
        //        .stream().filter(it -> it.getId().equals(id))
        //      .collect(Collectors.toList()).stream().findFirst().get().getSite().getName());
        if (lemmaRepository.existsById(id.intValue())) {
            lemmaRepository.deleteById(id.intValue());
        } else {
            throw new jakarta.persistence.EntityNotFoundException("Lemma not found");
        }
    }

    public LemmaDto mapToDto(LemmaModel model) {
        LemmaDto dto = new LemmaDto();
        dto.setId(model.getId());
        dto.setSiteUrl(model.getSite().getUrl());
        dto.setFrequency(model.getFrequency());
        dto.setLemma(model.getLemma());
        return dto;
    }

    @Transactional
    public LemmaModel mapToModel(LemmaDto lemmaDto) {
        LemmaModel model = new LemmaModel();
        SiteModel siteM = siteCRUDService.findByUrl(lemmaDto.getSiteUrl());

        if (siteM == null) {
            log.error("SiteModel not found for URL: " + lemmaDto.getSiteUrl());
            log.info("Site repo size " + siteCRUDService.findAll().size());
            throw new EntityNotFoundException("SiteModel not found for URL: " + lemmaDto.getSiteUrl());
        }
        model.setId(lemmaDto.getId());
        model.setSite(siteM);
        model.setFrequency(lemmaDto.getFrequency());
        model.setLemma(lemmaDto.getLemma());
        return model;
    }

    @Transactional
    public Boolean isServiceEmpty() {
        return lemmaRepository.count() == 0;
    }
   /* @Transactional
    public List<LemmaModel> getLemmasByPage(Long pageId){
        if (lemmaRepository.existsByPageId(pageId)){
            return getLemmasByPage(pageId);
        }else{
            return new ArrayList<>();
        }

    }*/

}
