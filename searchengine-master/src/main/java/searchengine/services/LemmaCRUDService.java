package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.objects.IndexDto;
import searchengine.dto.objects.LemmaDto;
import searchengine.dto.objects.PageDto;
import searchengine.model.IndexModel;
import searchengine.model.LemmaModel;
import searchengine.model.SiteModel;
import searchengine.repositories.LemmaRepository;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LemmaCRUDService implements CRUDService<LemmaDto> {
    private final LemmaRepository lemmaRepository;

    private final SiteCRUDService siteCRUDService;
    @Autowired
    private IndexCRUDService indexCRUDService;
    @Autowired
    private PageCRUDService pageCRUDService;

    @Override
    @Transactional
    @Retryable(
            value = {DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000))
    public LemmaDto getById(Long id) {
        Optional<LemmaModel> lemmaOpt = lemmaRepository.findById(id.intValue());
        if (lemmaOpt.isPresent()) {
            return mapToDto(lemmaOpt.get());
        } else {
            throw new EntityNotFoundException("Lemma with id " + id + " not found");
        }
    }

    @Transactional
    @Retryable(
            value = {DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000))
    public LemmaDto getByLemmaAndSiteId(String lemma, UUID uuid) {
        log.info("Before get by lemma and site. Is empty " + lemmaRepository.findAll().isEmpty());

        Optional<LemmaModel> modelO = lemmaRepository
                .findAll().stream().filter(it -> it.getLemma().equals(lemma) && it.getSite().getId().equals(uuid)).findFirst();
        if (modelO.isPresent()) {
            return mapToDto(modelO.get());
        }
        return null;
    }

    @Override
    @Transactional
    @Retryable(
            value = {DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000))
    public Collection<LemmaDto> getAll() {
        List<LemmaModel> list = lemmaRepository.findAll();
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(it -> mapToDto(it)).collect(Collectors.toList());
    }
    public int getLemmasCount(){return Integer.parseInt(String.valueOf(getAll().stream().count()));}

    @Override
    @Transactional
    @Retryable(
            value = {DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000))
    public void create(LemmaDto item) {
        LemmaModel lemmaM = mapToModel(item);
        log.warn("From page CRUD Service. Page model get site url == " + lemmaM.getSite().getUrl());
        log.warn("From page CRUD Service. Site repo size " + siteCRUDService.findAll().size());
        SiteModel siteM = siteCRUDService.findByUrl(lemmaM.getSite().getUrl());

        if (siteM == null) {
            log.error("Cannot find lemma with URL: " + lemmaM.getSite().getUrl());
            throw new EntityNotFoundException("Site model not found for URL: " + lemmaM.getSite().getUrl());
        }

        lemmaM.setFrequency(1);
        log.info("Before saving lemma into method create");
        lemmaRepository.save(lemmaM);
    }

    @Transactional
    @Retryable(
            value = {DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000))
    public HashSet<LemmaDto> createAll(HashSet<LemmaDto> lemmaDtoHashSet) {
        HashSet<LemmaModel> lemmaModels = new HashSet<>();
        for (LemmaDto dto : lemmaDtoHashSet) {
            dto.setFrequency(1);
            lemmaModels.add(mapToModel(dto));
        }
        return lemmaRepository.saveAll(lemmaModels).stream()
                .map(this::mapToDto).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    @Transactional
    @Retryable(
            value = {DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000))
    public void update(LemmaDto item) {
        if (item.getId() == null) {
            throw new IllegalArgumentException("ID of the lemma cannot be null");
        }
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

        lemmaRepository.save(existingLemma);
    }

    @Transactional
    @Retryable(
            value = {DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000))
    public HashSet<LemmaDto> updateAll(HashSet<LemmaDto> lemmaDtos) {
        List<Integer> ids = lemmaDtos.stream().map(dto -> Math.toIntExact(dto.getId())).collect(Collectors.toList());
        List<LemmaModel> existingLemmas = lemmaRepository.findAllById(ids);
        Map<Integer, LemmaDto> lemmaDtoMap = lemmaDtos.stream()
                .collect(Collectors.toMap(dto -> Math.toIntExact(dto.getId()), dto -> dto));
        List<LemmaModel> lemmasToUpdate = new ArrayList<>();

        for (LemmaModel existingLemma : existingLemmas) {
            LemmaDto lemmaDto = lemmaDtoMap.get(Math.toIntExact(existingLemma.getId()));
            if (lemmaDto != null) {
                SiteModel siteM = siteCRUDService.findByUrl(lemmaDto.getSiteUrl());
                if (siteM == null) {
                    throw new EntityNotFoundException("SiteModel not found for URL: " + lemmaDto.getSiteUrl());
                }
                boolean isUpdated = false;
                if (!existingLemma.getLemma().equals(lemmaDto.getLemma())) {
                    existingLemma.setLemma(lemmaDto.getLemma());
                    isUpdated = true;
                }
                if (existingLemma.getFrequency() != lemmaDto.getFrequency()) {
                    existingLemma.setFrequency(lemmaDto.getFrequency());
                    isUpdated = true;
                }
                if (!existingLemma.getSite().equals(siteM)) {
                    existingLemma.setSite(siteM);
                    isUpdated = true;
                }
                if (isUpdated) {
                    lemmasToUpdate.add(existingLemma);
                }
            }
        }
        return lemmaRepository.saveAll(lemmasToUpdate).stream()
                .map(this::mapToDto).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    @Transactional
    @Retryable(
            value = {DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000))
    public void delete(Long id) {
        log.info("Delete lemma " + id.toString());
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

    public LemmaDto mapToDtoWithOutId(LemmaModel model) {
        LemmaDto dto = new LemmaDto();
        dto.setSiteUrl(model.getSite().getUrl());
        dto.setFrequency(model.getFrequency());
        dto.setLemma(model.getLemma());
        return dto;
    }

    //@Transactional
    public LemmaModel mapToModel(LemmaDto lemmaDto) {
        LemmaModel model = new LemmaModel();
        SiteModel siteM = siteCRUDService.findByUrl(lemmaDto.getSiteUrl());

        if (siteM == null) {
            log.error("SiteModel not found for URL: " + lemmaDto.getSiteUrl());
            log.info("Site repo size " + siteCRUDService.findAll().size());
            throw new EntityNotFoundException("SiteModel not found for URL: " + lemmaDto.getSiteUrl());
        }
        //model.setId(lemmaDto.getId());
        model.setSite(siteM);
        model.setFrequency(lemmaDto.getFrequency());
        model.setLemma(lemmaDto.getLemma().toString());
        return model;
    }

    @Transactional
    @Retryable(
            value = {DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000))
    public Boolean isServiceEmpty() {
        return lemmaRepository.count() == 0;
    }
    @Transactional
    public Set<LemmaDto> getLemmasByPage(Long pageId){
        List<Integer> lemmasId = new ArrayList<>();
        Set<IndexDto> indexes = indexCRUDService.findByPageId(pageId);
        indexes.stream().forEach(it -> lemmasId.add(it.getLemmaId()));

        List<LemmaModel> lemmaModels = lemmaRepository.findLemmasByIds(lemmasId);
        Set<LemmaDto> lemmaDtos = new HashSet<>();
        lemmaModels.stream().forEach(it -> lemmaDtos.add(mapToDto(it)));
        return lemmaDtos;
    }
//    @Transactional// Метод должен вернуть <PageId, Set<LemmaDto>>
//    public Map<Long, Set<LemmaDto>> findLemmasByPageIds(Set<Long> pageIds) {
//        String url = pageCRUDService.getById(pageIds.stream().findFirst().get()).getSite();
//        UUID uuid = siteCRUDService.findByUrl(url).getId();
//        Map<Long, Set<IndexDto>> indexMap = indexCRUDService.findIndexesByPageIds(pageIds);
//        List<LemmaModel> lemmas = lemmaRepository.findLemmasBySiteId(uuid);
//        Map<Long, Set<LemmaDto>> lemmasByPageId = new HashMap<>();
//
//        for (LemmaModel lemma : lemmas) {
//            LemmaDto lemmaDto = mapToDto(lemma);
//
//            // Найти все страницы, на которых встречается эта лемма
//            for (Map.Entry<Long, Set<IndexDto>> entry : indexMap.entrySet()) {
//                Long pageId = entry.getKey();
//                Set<IndexDto> indices = entry.getValue();
//
//                for (IndexDto index : indices) {
//                    log.info("index.getLemmaId() " + index.getLemmaId() + ". lemma.getId() " + lemma.getId());
//                    if (index.getLemmaId() == lemma.getId()) {
//                        lemmasByPageId
//                                .computeIfAbsent(pageId, k -> new HashSet<>())
//                                .add(lemmaDto);
//                        break; // Выход из внутреннего цикла, так как лемма уже добавлена
//                    }
//                }
//            }
//        }
//
//        return lemmasByPageId;
//    }
public Map<Long, Set<LemmaDto>> findLemmasByPageIds(Set<Long> pageIds) {
    String url = pageCRUDService.getById(pageIds.stream().findFirst().orElseThrow()).getSite();

    UUID uuid = siteCRUDService.findByUrl(url).getId();

    Map<Long, Set<IndexDto>> indexMap = indexCRUDService.findIndexesByPageIds(pageIds);

    List<LemmaModel> lemmas = lemmaRepository.findLemmasBySiteId(uuid);

    // Мапа для быстрого доступа к леммам по их id
    Map<Long, LemmaDto> lemmaDtoMap = lemmas.stream()
            .map(this::mapToDto)
            .collect(Collectors.toMap(LemmaDto::getId, lemmaDto -> lemmaDto));

    // Мапа для группировки лемм по pageId
    Map<Long, Set<LemmaDto>> lemmasByPageId = new HashMap<>();

    // Заполняем мапу lemmasByPageId
    for (Map.Entry<Long, Set<IndexDto>> entry : indexMap.entrySet()) {
        Long pageId = entry.getKey();
        Set<IndexDto> indices = entry.getValue();

        for (IndexDto index : indices) {
            //int lemmaId = index.getLemmaId();
            Long lemmaId = Long.valueOf(index.getLemmaId());
            // Проверяем наличие леммы в мапе lemmaDtoMap и добавляем в lemmasByPageId
            if (lemmaDtoMap.containsKey(lemmaId)) {
                lemmasByPageId
                        .computeIfAbsent(pageId, k -> new HashSet<>())
                        .add(lemmaDtoMap.get(lemmaId));
            }
        }
    }

    return lemmasByPageId;
}
}
