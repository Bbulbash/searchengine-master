package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.objects.IndexDto;
import searchengine.model.IndexKey;
import searchengine.model.IndexModel;
import searchengine.model.PageModel;
import searchengine.repositories.IndexRepository;

import javax.persistence.EntityNotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexCRUDService implements CRUDService<IndexDto> {
    private final IndexRepository indexRepository;
    private final SiteCRUDService siteCRUDService;
    @Autowired
    private PageCRUDService pageCRUDService;

    @Override
    @Transactional
    public IndexDto getById(Long id) {
        return null;//mapToDto(indexRepository.getReferenceById(Math.toIntExact(id)));
    }
    /* @Transactional
    public IndexDto getByKey(IndexKey key){
        return indexRepository.
    }*/

    @Override
    @Transactional
    public IndexDto getById(Long id) {
        return null;
    }

    @Override
    @Transactional
    public Collection<IndexDto> getAll() {
        List<IndexModel> list = indexRepository.findAll();
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(it -> mapToDto(it)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void create(IndexDto item) {
        IndexModel indexM = mapToModel(item);
        indexRepository.save(indexM);
    }

    @Override
    @Transactional
    public void update(IndexDto item) {
        indexRepository.findById(new IndexKey(item.getPageId(), item.getLemmaId()))
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("From index CRUD service. Index not found"));
        IndexModel indexModel = mapToModel(item);
        indexRepository.saveAndFlush(indexModel);
    }

    @Override
    public void delete(BaseId id) {

    }

    @Override
    @Transactional
    public void delete(IndexKey key) {
        if (indexRepository.existByKey(key)) indexRepository.delete(key);
        else throw new jakarta.persistence.EntityNotFoundException("Index not found");
    }

    //@Transactional
    private IndexModel mapToModel(IndexDto dto) {
        IndexModel model = new IndexModel();
        IndexKey key = new IndexKey(dto.getPageId(), dto.getLemmaId());
        log.info("Index page id from index " + dto.getPageId());
        PageModel pageM = pageCRUDService.mapToModel(pageCRUDService.getById(dto.getPageId()));
        if (pageM == null) {
            log.error("PageModel not found for ID: " + dto.getPageId());
            log.info("Page repo size " + siteCRUDService.findAll().size());
            throw new EntityNotFoundException("PageModel not found for ID: " + dto.getPageId());
        }
        model.setId(key);
        model.setPage(pageM);
        model.setRankValue(dto.getRankValue());
        return model;
    }

    private IndexDto mapToDto(IndexModel model) {
        IndexDto dto = new IndexDto();
        dto.setLemmaId(model.getId().getLemmaId());
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
}
