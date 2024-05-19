package searchengine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Index_table")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    //@Column(name = "page_id", nullable = false)
    @OneToOne(fetch = FetchType.LAZY)
    private PageModel page;
    @Column(name = "lemma_id", nullable = false)
    private int lemmaId;
    @Column(name = "rank_value", nullable = false)
    private float rankValue;
}
