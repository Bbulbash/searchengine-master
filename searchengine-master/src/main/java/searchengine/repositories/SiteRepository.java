package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteModel;
import searchengine.model.Status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteRepository extends JpaRepository<SiteModel, UUID> {
    SiteModel findByUrl(String url);
    List<SiteModel> findAllByStatus(Status status);
    Boolean existsByUrl(String url);
    boolean existsById(UUID uuid);
    Optional<SiteModel> findById(UUID uuid);
    List<SiteModel> findAll();


}
