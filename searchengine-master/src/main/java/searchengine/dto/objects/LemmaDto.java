package searchengine.dto.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
public class LemmaDto {
    private Long id;
    private String siteUrl;
    private String lemma;
    private int frequency;
}
