### Дипломный проект курса "Java-разработчик"

**Задачи проекта:**

- Реализовать многопоточный обход дерева страниц сайта.
- Реализовать функционал индексации сайтов.
- Реализовать систему поиска.

---

### Как запустить проект

Следуйте этим шагам, чтобы запустить проект на вашем локальном компьютере:

1. **Склонируйте репозиторий:**

    ```sh
    git clone https://github.com/Bbulbash/searchengine-master.git
    cd search-engine
    ```

2. **Обновите конфигурацию базы данных:**

   Откройте файл `src/main/resources/application.properties` и замените значения параметров `spring.datasource.url`, `spring.datasource.username`, и `spring.datasource.password` на настройки вашей базы данных.

    ```properties
    spring.datasource.url=jdbc:postgresql://localhost:5432/yourdatabase
    spring.datasource.username=yourusername
    spring.datasource.password=yourpassword
    spring.jpa.hibernate.ddl-auto=update
    ```

3. **Соберите проект с помощью Maven:**

    ```sh
    mvn clean install
    ```

4. **Запустите приложение:**

    ```sh
    mvn spring-boot:run
    ```