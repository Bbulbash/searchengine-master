package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.PageModel;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<PageModel, Integer> {
    List<PageModel> findByPathAndSiteUrl(String url, String sitePath);
    List<PageModel> findAllBySiteId(Long id);
    void deleteById(Long id);
}
