package searchengine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.*;

import java.io.Serializable;
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Embeddable
public class IndexKey implements Serializable {
    @Column(name = "page_id", nullable = false)
    private Long pageId;
    @Column(name = "lemma_id", nullable = false)
    private int lemmaId;
}
