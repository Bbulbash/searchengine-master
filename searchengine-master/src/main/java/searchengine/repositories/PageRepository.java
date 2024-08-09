package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.PageModel;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface PageRepository extends JpaRepository<PageModel, Integer> {
    List<PageModel> findByPathAndSiteUrl(String url, String sitePath);
    List<PageModel> findAllBySiteId(UUID uuid);
    Boolean existsByPathAndSiteId(String path, UUID siteId);
    Set<PageModel> findAllByIdIn(Set<Long> ids);
}
