package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.objects.IndexDto;
import searchengine.model.IndexModel;
import searchengine.model.PageModel;
import searchengine.repositories.IndexRepository;
import javax.persistence.EntityNotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexCRUDService implements CRUDService<IndexDto> {
    private final IndexRepository indexRepository;
    private final PageCRUDService pageCRUDService;
    private final SiteCRUDService siteCRUDService;

    @Override
    @Transactional
    public IndexDto getById(Long id) {
        return mapToDto(indexRepository.getById(Math.toIntExact(id)));
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
        log.info("From index crud service. Index model " + indexM.getId());
        indexRepository.save(indexM);
    }

    @Override
    @Transactional
    public void update(IndexDto item) {
        indexRepository.findById(item.getId().intValue())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("From index CRUD service. Index not found"));
        IndexModel indexModel = mapToModel(item);
        indexRepository.saveAndFlush(indexModel);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (indexRepository.existsById(Math.toIntExact(id))) {
            indexRepository.deleteById(id.intValue());
        } else {
            throw new jakarta.persistence.EntityNotFoundException("Index not found");
        }
    }

    @Transactional
    private IndexModel mapToModel(IndexDto dto) {
        IndexModel model = new IndexModel();
        PageModel pageM = pageCRUDService.mapToModel(pageCRUDService.getById(dto.getPageId()));
        if (pageM == null) {
            log.error("PageModel not found for ID: " + dto.getPageId());
            log.info("Page repo size " + siteCRUDService.findAll().size());
            throw new EntityNotFoundException("PageModel not found for ID: " + dto.getPageId());
        }
        model.setId(dto.getId());
        model.setPage(pageM);
        model.setRankValue(dto.getRankValue());
        model.setLemmaId(dto.getLemmaId());
        return model;
    }

    private IndexDto mapToDto(IndexModel model) {
        IndexDto dto = new IndexDto();
        dto.setId(model.getId());
        dto.setLemmaId(model.getLemmaId());
        dto.setPageId(model.getPage().getId());
        dto.setRankValue(model.getRankValue());
        return dto;
    }
    @Transactional
    public Boolean isServiceEmpty(){
        return indexRepository.count() == 0;
    }
}
