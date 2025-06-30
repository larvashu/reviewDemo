Testy demo dla G2A.

Zawartość:

## Testy cucumber (dev-like):

Za pomocą jednej komendy uruchamiane są serwisy:
- PostgresDB
- RabbitMQ
- Worker biznesowy (Przeliczanie VAT dla zamówień)
- Testy cucumber (src/test/resources/feature)

`mvn clean test -Pcucumber`

Po wykonaniu testów, wszystkie serwisy są automatycznie zamykane.

Wyświetlenie raportu:

`start target/cucumber-reports/cucumber.html`

## Testy na czystym jUnit 
preProd-like, testy bardziej 'czarnoskrzynkowe', z naciskiem na szerszą parametryzacje

Przed przeprowadzeniem testu, należy ręcznie uruchomić środowisko, aby odwzorować warunki przedprodukcyjne,
oraz mieć szybki dostęp do logów workera:

    1. docker-compose up
    2. Uruchom ręcznie test/java/worker/OrderWorkerMain
    
`mvn clean test -Pjunit`

WAŻNE: Po zakończonych testach należy ręcznie wyłączyć środowisko (docker-compose down)

Użyte liby:
- Junit
- Cucumber
- jooq ("for science", używam pierwszy raz)
- testcontainers (używam pierwszy raz)

### Potencjalne TODO: reporty allure oraz równoległe uruchamianie testów