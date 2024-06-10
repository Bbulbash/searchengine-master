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
public class IndexModel {

    @EmbeddedId
    private IndexKey id;
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)//Ошибка из-за onetoone
    @JoinColumn(name = "page_id", nullable = false)
    @MapsId("pageId")
    private PageModel page;
    @Column(name = "rank_value", nullable = false)
    private float rankValue;
}
