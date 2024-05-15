package searchengine.crawlerPages;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class TaskResult {
    private final Boolean success;
    private final String errorMessage;

}
