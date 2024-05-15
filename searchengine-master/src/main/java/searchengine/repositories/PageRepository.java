package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.PageModel;
@Repository
public interface PageRepository extends JpaRepository<PageModel, Integer> {
    PageModel findByPath(String url);
}
