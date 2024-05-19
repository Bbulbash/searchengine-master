package searchengine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

@Entity
@Table(name = "Lemma")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
   // @Column(name = "site_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", referencedColumnName = "id", nullable = false)
    private SiteModel site;
    @Column(name = "lemma", nullable = false, length = 255)
    private String lemma;
    @Column(name = "frequency", nullable = false)
    private int frequency;
}
