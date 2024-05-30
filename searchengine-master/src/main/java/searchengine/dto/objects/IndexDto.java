package searchengine.dto.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
public class IndexDto {
    private Long id;
    private Long pageId;
    private int lemmaId;
    private float rankValue;
}
