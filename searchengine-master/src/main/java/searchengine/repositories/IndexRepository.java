package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexKey;
import searchengine.model.IndexModel;
import searchengine.model.PageModel;

import java.util.List;
import java.util.Optional;
@Repository
public interface IndexRepository extends JpaRepository<IndexModel, IndexKey> {
    //Optional<IndexModel> findByPageAndLemmaId(PageModel page, int lemmaId);

    Optional<IndexModel> findByKey(IndexKey key);
    Optional<IndexModel> findByPageId(Long pageId);
    @Query("SELECT COUNT(i) > 0 FROM IndexModel i WHERE i.id.pageId = :pageId AND i.id.lemmaId = :lemmaId")
    Boolean existsByKey(@Param("pageId") Long pageId, @Param("lemmaId") Long lemmaId);
    //List<IndexModel>
    @Modifying
    @Transactional
    @Query("DELETE FROM IndexModel i WHERE i.id.pageId = :pageId AND i.id.lemmaId = :lemmaId")
    void deleteByIndexKey(@Param("pageId") Long pageId, @Param("lemmaId") Long lemmaId);
    @Query("SELECT i FROM IndexModel i WHERE i.key.pageId = :pageId")
    List<IndexModel> findAllByPageId(@Param("pageId") Long pageId);
}
