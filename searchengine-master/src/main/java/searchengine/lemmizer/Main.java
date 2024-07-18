package searchengine.lemmizer;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        Lemmizer lemmizer = new Lemmizer();
        LuceneMorphology luceneMorphologyEN = new EnglishLuceneMorphology();
        System.out.println(luceneMorphologyEN.getMorphInfo("or"));
        //System.out.println(
          //      lemmizer
            //            .getLemmasList("Повторное появление леопарда в Осетии позволяет предположить, что леопард постоянно обитает в некоторых районах Северного Кавказа."));
    }

}
