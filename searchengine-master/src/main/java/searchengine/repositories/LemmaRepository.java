package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaModel;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaModel, Integer> {
   // LemmaModel findByLemma(String lemma);
    LemmaModel findByLemmaAndSiteId(String lemma, Long siteId);
    //List<LemmaModel> findAllByPageId(Long pageId);
  //  Boolean existsByPageId(Long pageId);
}
