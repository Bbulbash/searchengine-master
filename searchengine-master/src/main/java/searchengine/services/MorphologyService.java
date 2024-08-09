package searchengine.services;

import lombok.AllArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class MorphologyService {

    private String NOT_A_WORD_PATTERN =  "(?:\\.*\\s+\\-\\s+\\.*)|[^\\-а-яА-Яa-zA-Z\\d\\ё\\Ё]+";
    private LuceneMorphology  russianLuceneMorph;
    private LuceneMorphology englishLuceneMorph;



    public List<String> getNormalFormOfAWord(String word) throws IOException {
        russianLuceneMorph = new RussianLuceneMorphology();
        englishLuceneMorph = new EnglishLuceneMorphology();
        word = word.replaceAll("ё", "е");
        if (russianLuceneMorph.checkString(word) && !isRussianGarbage(russianLuceneMorph.getMorphInfo(word))) {
            return russianLuceneMorph.getNormalForms(word);
        } else if (englishLuceneMorph.checkString(word) && !isEnglishGarbage(englishLuceneMorph.getMorphInfo(word))) {
            return englishLuceneMorph.getNormalForms(word);
        } else if (word.chars().allMatch(Character::isDigit)){
            return Collections.singletonList(word);
        }
        return new ArrayList<>();
    }

    public String[] splitStringToLowercaseWords(String input) {
        return Arrays.stream(input.toLowerCase(Locale.ROOT)
                        .replaceAll(NOT_A_WORD_PATTERN, " ")
                        .trim()
                        .split(" "))
                .filter(s -> !s.isBlank()).toArray(String[]::new);
    }

    public String[] splitStringToWords(String sentence) {
        return Arrays.stream(sentence.replaceAll(NOT_A_WORD_PATTERN, " ")
                        .trim()
                        .split(" "))
                .filter(s -> !s.isBlank()).toArray(String[]::new);
    }

    boolean isRussianGarbage(List<String> morphInfos) {
        for(String variant : morphInfos) {
            if (variant.contains(" СОЮЗ") || variant.contains(" МЕЖД") ||
                    variant.contains(" ПРЕДЛ") || variant.contains(" ЧАСТ")) {
                return true;
            }
        }
        return false;
    }

    boolean isEnglishGarbage (List<String> morphInfos) {
        for(String variant : morphInfos) {
            if (variant.contains(" CONJ") || variant.contains(" INT") ||
                    variant.contains(" PREP") || variant.contains(" PART") ||  variant.contains(" ARTICLE")) {
                return true;
            }
        }
        return false;
    }
}