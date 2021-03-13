# Backend

The API for the chickens service.

## Development

1) Setup the development database:

    docker run --name some-postgres -e POSTGRES_PASSWORD=mysecretpassword -p 5432:5432 -d postgres
    clojure -A:migrate up

2) Start the server:

    clojure -M:dev

## Testing

    clojure -M:test

## Deployment

    clojure -X:depstar uberjar
    java -Dconfig=config/dev/config.edn -jar chickens.jar

## Migrations

To the newest migration

    clojure -A:migrate up

Down one

    clojure -A:migrate down
