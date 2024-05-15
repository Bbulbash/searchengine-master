package searchengine.dto.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
public class PageDto {
    private Long id;
    private String site;//Корневой адрес сайта
    private String path;
    private int code;
    private String content;
}