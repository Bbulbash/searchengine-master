package searchengine.model;
import jakarta.persistence.*;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "Page", indexes = {
        @Index(name = "idx_path", columnList = "path")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PageModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)//cascade = {CascadeType.PERSIST, CascadeType.MERGE}
    @JoinColumn(name = "site_id", nullable = false)
    private SiteModel site;
    @Column(name = "path", nullable = false, length = 255)
    private String path;
    @Column(name = "code", nullable = false)
    private int code;
    //@Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String content;
    @OneToMany(mappedBy = "page", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<IndexModel> indexes;
}

