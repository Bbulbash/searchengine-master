package searchengine.dto.statistics;

public class WordMapper {

        private String word;
        private String normalForm;

        public WordMapper(String word, String normalForm) {
            this.word = word;
            this.normalForm = normalForm;
        }

        public String getWord() {
            return word;
        }

        public String getNormalForm() {
            return normalForm;
        }

}
