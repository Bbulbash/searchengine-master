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

//@IdClass(IndexKey.class)
public class IndexModel {

    @EmbeddedId
    private IndexKey key;
/*    @Id
    private Long pageId;
    @Id
    private Long lemmaId;*/
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)//Ошибка из-за onetoone
    @JoinColumn(name = "page_id", nullable = false)
    @MapsId("pageId")

    private PageModel page;
    @Column(name = "rank_value", nullable = false)
    private float rankValue;
}
