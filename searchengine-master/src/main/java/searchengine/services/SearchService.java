package searchengine.services;

import org.springframework.stereotype.Service;

@Service
public class SearchService {
   /* public SearchResponse search(String query, String site, int offset, int limit) {
        // Здесь должна быть логика поиска по индексу

        SearchResponse response = new SearchResponse();
        // Пример заполнения
        response.setCount(100); // общее количество результатов
        // заполняем данные результатами поиска

        return response;
    }*/

    public boolean isIndexNotReady(String site) {
        // Проверка, готов ли индекс для данного сайта или всех сайтов
        return false;
    }
}
