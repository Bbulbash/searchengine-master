package searchengine.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "Site")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SiteModel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "ENUM('FAILED', 'INDEXED', 'INDEXING')")
    private Status status;
    @Column(name = "status_time", nullable = false)
    private LocalDateTime statusTime;
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    @Column(name = "url", nullable = false, length = 255)
    private String url;
    @Column( name = "name", nullable = false, length = 255)
    private String name;
    @OneToMany(mappedBy = "site", fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)
    private List<LemmaModel> lemmaModels;
    @OneToMany(mappedBy = "site", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    private Set<PageModel> pages;
}
