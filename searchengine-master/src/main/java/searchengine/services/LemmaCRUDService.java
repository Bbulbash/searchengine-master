package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.objects.IndexDto;
import searchengine.dto.objects.LemmaDto;
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
    private final IndexCRUDService indexCRUDService;
    private final PageCRUDService pageCRUDService;

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

    public int getLemmasCount() {
        return Integer.parseInt(String.valueOf(getAll().stream().count()));
    }

    @Override
    @Transactional
    @Retryable(
            value = {DeadlockLoserDataAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000))
    public void create(LemmaDto item) {
        LemmaModel lemmaM = mapToModel(item);
        SiteModel siteM = siteCRUDService.findByUrl(lemmaM.getSite().getUrl());

        if (siteM == null) {
            throw new EntityNotFoundException("Site model not found for URL: " + lemmaM.getSite().getUrl());
        }

        lemmaM.setFrequency(1);
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
                existingLemma.setLemma(lemmaDto.getLemma());
                existingLemma.setFrequency(lemmaDto.getFrequency());
                existingLemma.setSite(siteM);
                lemmasToUpdate.add(existingLemma);
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
        if (lemmaRepository.existsById(id.intValue())) {
            lemmaRepository.deleteById(id.intValue());
        } else {
            throw new jakarta.persistence.EntityNotFoundException("Lemma not found");
        }
    }

    private LemmaDto mapToDto(LemmaModel model) {
        LemmaDto dto = new LemmaDto();
        dto.setId(model.getId());
        dto.setSiteUrl(model.getSite().getUrl());
        dto.setFrequency(model.getFrequency());
        dto.setLemma(model.getLemma());
        return dto;
    }

    private LemmaModel mapToModel(LemmaDto lemmaDto) {
        LemmaModel model = new LemmaModel();
        SiteModel siteM = siteCRUDService.findByUrl(lemmaDto.getSiteUrl());

        if (siteM == null) {
            throw new EntityNotFoundException("SiteModel not found for URL: " + lemmaDto.getSiteUrl());
        }
        model.setSite(siteM);
        model.setFrequency(lemmaDto.getFrequency());
        model.setLemma(lemmaDto.getLemma().toString());
        return model;
    }

    public Map<Long, Set<LemmaDto>> findLemmasByPageIds(Set<Long> pageIds) {
        String url = pageCRUDService.getById(pageIds.stream().findFirst().orElseThrow()).getSite();

        UUID uuid = siteCRUDService.findByUrl(url).getId();

        Map<Long, Set<IndexDto>> indexMap = indexCRUDService.findIndexesByPageIds(pageIds);

        List<LemmaModel> lemmas = lemmaRepository.findLemmasBySiteId(uuid);

        Map<Long, LemmaDto> lemmaDtoMap = lemmas.stream()
                .map(this::mapToDto)
                .collect(Collectors.toMap(LemmaDto::getId, lemmaDto -> lemmaDto));

        Map<Long, Set<LemmaDto>> lemmasByPageId = new HashMap<>();

        for (Map.Entry<Long, Set<IndexDto>> entry : indexMap.entrySet()) {
            Long pageId = entry.getKey();
            Set<IndexDto> indices = entry.getValue();

            for (IndexDto index : indices) {
                Long lemmaId = Long.valueOf(index.getLemmaId());
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
