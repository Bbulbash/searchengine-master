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
public class IndexCRUDService{
    private final IndexRepository indexRepository;
    private final SiteCRUDService siteCRUDService;
    @Autowired
    private PageCRUDService pageCRUDService;
    @Autowired
    private LemmaCRUDService lemmaCRUDService;
    @Transactional
    public IndexDto getById(IndexKey key) {
        IndexModel model = indexRepository.findByKey(key)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("From index CRUD service. Index not found"));
        return mapToDto(model);//mapToDto(indexRepository.getReferenceById(Math.toIntExact(id)));
    }

    @Transactional
    public Collection<IndexDto> getAll() {
        List<IndexModel> list = indexRepository.findAll();
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(it -> mapToDto(it)).collect(Collectors.toList());
    }

   // @Override
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

    //@Override
    @Transactional
    public void update(IndexDto item) {
        indexRepository.findById(new IndexKey(item.getPageId(), item.getLemmaId()))
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("From index CRUD service. Index not found"));
        IndexModel indexModel = mapToModel(item);
        indexRepository.save(indexModel);
    }

    @Transactional
    public void delete(IndexKey key) {
        log.info("Delete index " + key);
        if (indexRepository.existsByKey(key.getPageId(), Long.parseLong(String.valueOf(key.getLemmaId())))){
          //  lemmaCRUDService.delete((long) key.getLemmaId());
            indexRepository.deleteByIndexKey(key.getPageId(), Long.parseLong(String.valueOf(key.getLemmaId())));
        }
        else throw new jakarta.persistence.EntityNotFoundException("Index not found");
    }

    //@Transactional
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
    public Optional<IndexModel> findByPageId(Long pageId) {
        return Optional.ofNullable(indexRepository.findByPageId(pageId).orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("From index CRUD service. Index not found")));
    }
    @Transactional
    public void deleteByPageId(Long pageId){
        List<IndexModel> models = indexRepository.findAllByPageId(pageId);
        for(IndexModel model : models){
            log.info("Index model page before delete " + model.getKey().getPageId());
            log.info("Lemma id " + model.getKey().getLemmaId());
            indexRepository.delete(model);
        }
    }
}
