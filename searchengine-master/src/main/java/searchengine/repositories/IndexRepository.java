package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexKey;
import searchengine.model.IndexModel;
import searchengine.model.PageModel;

import java.util.Optional;

@Repository
public interface IndexRepository extends JpaRepository<IndexModel, IndexKey> {
    //Optional<IndexModel> findByPageAndLemmaId(PageModel page, int lemmaId);

    Optional<IndexModel> findByKey(IndexKey key);
    Optional<IndexModel> findByPageId(Long pageId);
    Boolean existByKey(IndexKey key);

    void deleteByKey(IndexKey key);
}
