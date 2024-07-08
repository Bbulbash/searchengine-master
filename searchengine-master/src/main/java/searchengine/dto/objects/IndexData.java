package searchengine.dto.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class IndexData {
    private int lemmaCount;
    private Long pageId;
    private LemmaDto lemmaDto;
}
