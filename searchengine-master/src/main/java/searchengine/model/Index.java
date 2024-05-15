package searchengine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "Index_table")
@Getter
@Setter
@AllArgsConstructor
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;
    //@Column(name = "page_id", nullable = false)
    @OneToOne(fetch = FetchType.LAZY)
    private PageModel page;
    @Column(name = "lemma_id", nullable = false)
    private int lemmaId;
    @Column(name = "rank_value", nullable = false)
    private float rankValue;
}
