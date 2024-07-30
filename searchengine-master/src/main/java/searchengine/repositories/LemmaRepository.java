package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.dto.objects.LemmaDto;
import searchengine.model.LemmaModel;
import searchengine.model.SiteModel;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaModel, Integer> {
    @Query("SELECT l FROM LemmaModel l WHERE l.id IN :lemmasId")
    List<LemmaModel> findLemmasByIds(@Param("lemmasId") List<Integer> lemmasId);
    List<LemmaModel> findLemmasBySiteId(UUID siteId);


}
