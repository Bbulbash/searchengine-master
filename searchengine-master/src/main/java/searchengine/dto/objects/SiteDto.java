package searchengine.dto.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
public class SiteDto {
    private Long id;
    private String status;
    private String statusTime;
    private String lastError;
    private String url;
    private String name;

}