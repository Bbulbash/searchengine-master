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
import java.util.UUID;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaModel, Integer> {

    LemmaModel findByLemmaAndSiteId(String lemma, UUID siteId);
    //List<LemmaModel> findAllByLemmaAndSiteId(List<String> lemma, UUID siteId);
    @Modifying
    @Query("UPDATE LemmaModel l SET l.lemma = :lemma, l.frequency = :frequency, l.site = :site WHERE l.id = :id")
    void updateLemma(@Param("lemma") String lemma,
                     @Param("frequency") int frequency,
                     @Param("site") SiteModel site,
                     @Param("id") int id);
}
