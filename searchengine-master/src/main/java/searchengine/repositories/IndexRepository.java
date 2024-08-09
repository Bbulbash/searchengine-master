package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexKey;
import searchengine.model.IndexModel;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface IndexRepository extends JpaRepository<IndexModel, IndexKey> {
    List<IndexModel> findByPageId(Long pageId);
    @Query("SELECT COUNT(i) > 0 FROM IndexModel i WHERE i.id.pageId = :pageId AND i.id.lemmaId = :lemmaId")
    Boolean existsByKey(@Param("pageId") Long pageId, @Param("lemmaId") Long lemmaId);
    //List<IndexModel>
    @Modifying
    @Transactional
    @Query("DELETE FROM IndexModel i WHERE i.id.pageId = :pageId AND i.id.lemmaId = :lemmaId")
    void deleteByIndexKey(@Param("pageId") Long pageId, @Param("lemmaId") Long lemmaId);
    @Query("SELECT i FROM IndexModel i WHERE i.id.pageId IN :pageIds")
    List<IndexModel> findAllByPageIds(@Param("pageIds") Set<Long> pageIds);
    @Query("SELECT i FROM IndexModel i WHERE i.id.lemmaId IN :lemmaIds")
    List<IndexModel> findAllByLemmaIds(@Param("lemmaIds")Set<Long> lemmaIds);
}
