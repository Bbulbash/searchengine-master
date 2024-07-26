package searchengine.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.dto.statistics.SearchResult;
import searchengine.model.PageModel;

import java.util.List;
import java.util.UUID;

@Repository
public interface PageRepository extends JpaRepository<PageModel, Integer> {
    List<PageModel> findByPathAndSiteUrl(String url, String sitePath);
    List<PageModel> findAllBySiteId(UUID uuid);
    void deleteById(Long id);
    Boolean existsByPathAndSiteId(String path, UUID siteId);

}
