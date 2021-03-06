
## Development

1) Setup the development database:

    docker run --name some-postgres -e POSTGRES_PASSWORD=mysecretpassword -p 5432:5432 -d postgres
    psql 'postgresql://localhost/postgres?user=postgres&password=mysecretpassword' < resources/schema.sql

2) Start the server:

    clojure -M:dev

## Testing

    clojure -M:test

## Deployment

    clojure -X:depstar uberjar
    java -jar chickens.jar -Dconfig=config/dev/config.edn
