package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.objects.IndexDto;
import searchengine.model.IndexKey;
import searchengine.model.IndexModel;
import searchengine.model.LemmaModel;
import searchengine.model.PageModel;
import searchengine.repositories.IndexRepository;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor

public class IndexCRUDService {
    private final IndexRepository indexRepository;
    private final PageCRUDService pageCRUDService;


    @Transactional
    public Collection<IndexDto> getAll() {
        List<IndexModel> list = indexRepository.findAll();
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(it -> mapToDto(it)).collect(Collectors.toList());
    }
    @Transactional
    public void create(IndexDto item) {
        IndexModel indexM = mapToModel(item);
        indexRepository.save(indexM);
    }
    @Transactional
    public void createAll(HashSet<IndexDto> indexDtoSet){
        HashSet<IndexModel> models = new HashSet<>();
        for(IndexDto index : indexDtoSet){
            models.add(mapToModel(index));
        }
        indexRepository.saveAllAndFlush(models);
    }
    @Transactional
    public void update(IndexDto item) {
        indexRepository.findById(new IndexKey(item.getPageId(), item.getLemmaId()))
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("From index CRUD service. Index not found"));
        IndexModel indexModel = mapToModel(item);
        indexRepository.save(indexModel);
    }


    @Transactional
    public void delete(IndexKey key) {
        if (indexRepository.existsByKey(key.getPageId(), Long.parseLong(String.valueOf(key.getLemmaId())))){
            indexRepository.deleteByIndexKey(key.getPageId(), Long.parseLong(String.valueOf(key.getLemmaId())));
        }
        else throw new jakarta.persistence.EntityNotFoundException("Index not found");
    }
    private IndexModel mapToModel(IndexDto dto) {
        IndexModel model = new IndexModel();
        IndexKey key = new IndexKey(dto.getPageId(), dto.getLemmaId());
        log.info("Index page id from index " + dto.getPageId());
        PageModel pageM;
        try {
            pageM = pageCRUDService.mapToModelWithId(pageCRUDService.getById(dto.getPageId()));
        } catch (EntityNotFoundException ex) {
            log.error("PageModel not found for ID: " + dto.getPageId());
            throw new EntityNotFoundException("PageModel not found for ID: " + dto.getPageId());
        }
        model.setKey(key);
        model.setPage(pageM);
        model.setRankValue(dto.getRankValue());
        return model;
    }

    private IndexDto mapToDto(IndexModel model) {
        IndexDto dto = new IndexDto();
        dto.setLemmaId(model.getKey().getLemmaId());
        dto.setPageId(model.getPage().getId());
        dto.setRankValue(model.getRankValue());
        return dto;
    }

    @Transactional
    public Boolean isServiceEmpty() {
        return indexRepository.count() == 0;
    }

    @Transactional
    public Set<IndexDto> findByPageId(Long pageId){
        List<IndexModel> models = indexRepository.findByPageId(pageId);
        Set<IndexDto> dto = new HashSet<>();
        for(IndexModel model : models){
            dto.add(mapToDto(model));
        }
        return dto;
    }
    @Transactional
    public Map<Long, Set<IndexDto>> findIndexesByPageIds(Set<Long> pageIds) {
        List<IndexModel> allIndexes = indexRepository.findAllByPageIds(pageIds);
        Map<Long, Set<IndexDto>> indexesByPageId = new HashMap<>();

        for (IndexModel index : allIndexes) {
            Long pageId = index.getKey().getPageId();
            IndexDto indexDto = mapToDto(index);
            indexesByPageId
                    .computeIfAbsent(pageId, k -> new HashSet<>())
                    .add(indexDto);
        }

        return indexesByPageId;
    }
    @Transactional
    public Set<IndexDto> findAllByLemmaId(Set<Long> lemmaIds){
        List<IndexModel> models =  indexRepository.findAllByLemmaIds(lemmaIds);
        Set<IndexDto> indexDtos = new HashSet<>();
        for (IndexModel model : models){
            indexDtos.add(mapToDto(model));
        }
        return indexDtos;

    }

}
