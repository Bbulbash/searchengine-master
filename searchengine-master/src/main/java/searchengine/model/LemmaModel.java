package searchengine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Lemma")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LemmaModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // @Column(name = "site_id", nullable = false)
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL, optional = true)//cascade = {CascadeType.PERSIST, CascadeType.MERGE}
    @JoinColumn(name = "site_id", referencedColumnName = "id", nullable = true)
    private SiteModel site;
    @Column(name = "lemma", nullable = false, length = 255)
    private String lemma;
    @Column(name = "frequency", nullable = false)
    private int frequency;
}
