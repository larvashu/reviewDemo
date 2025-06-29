Testy demo dla G2A.

Zawartość:

**Testy cucumber (dev-like):**

Za pomocą jednej komendy uruchamiane są serwisy:
- PostgresDB
- RabbitMQ
- Worker biznesowy (Przeliczanie VAT dla zamówień)

Oraz testy cucumber (src/test/resources/feature)

mvn clean test -Pcucumber

Po wykonaniu testów, wszystkie serwisy są automatycznie zamykane.


2. Testy na czystym jUnit (preProd-like)

Przed przeprowadzeniem testu, należy ręcznie uruchomić środowisko, aby odwzorować warunki przedprodukcyjne,
oraz mieć szybki dostęp do logów workera:

    1. docker-compose up
    2. Uruchom ręcznie test/java/worker/OrderWorkerMain
    
Na tak przygotowanym środowisku uruchom:
mvn clean test -Pjunit

WAŻNE: Po zakończonych testach należy ręcznie wyłączyć środowisko (docker-compose down)
