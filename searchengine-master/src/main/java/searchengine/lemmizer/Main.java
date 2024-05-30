package searchengine.lemmizer;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        Lemmizer lemmizer = new Lemmizer();
        System.out.println(
                lemmizer
                        .getLemmasList("Бронирование залов галереи по телефонам +7 (985) 998 97 95 / +7 (499) 253 86 07 In English Бронирование залов галереи по телефонам +7 (985) 998 97 95 / +7 (499) 253 86 07 Креативное пространство и галерея Н.Б. Никогосяна Галерея О художнике Aфиша Аренда залов Новости Контакты Галерея О художнике Aфиша Аренда залов Новости Контакты Бронирование залов галереи по телефонам +7 (985) 998 97 95 /" +
                                " +7 (499) 253 86 07 In English Оформление заявки Оставьте свой номер и мы вам перезвоним, чтобы проконсультировать вас по возникшим вопросам * * 4-й этаж Галереи Нико5-й этаж Галереи НикоЯ не знаю Защита от автоматического заполнения Или звоните нам по телефону +7 (985) 998 97 95 +7 (499) 253 86 07 Request Leave your number and we will call you back to advise you on any questions * * The Fourth Floor of the Niko GalleryThe Third floor of the Niko GalleryI dont know Защита от автоматического заполнения Or call us at +7 (985) 998 97 95 +7 (499) 253 86 07 Креативное пространство и галерея Н.Б. Никогосяна Галерея Нико — это музей работ классика советского и постсоветского искусства академика Н.Б. Никогос"));
    }

}
