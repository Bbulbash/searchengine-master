package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteModel;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<SiteModel, Integer> {
    SiteModel findByUrl(String url);
    List<SiteModel> findAllByStatus(String status);
    Boolean existsByUrl(String url);
    List<SiteModel> findAll();


}
