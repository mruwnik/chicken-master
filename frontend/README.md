
## Development

Start the server:

    clojure -A:shadow-cljs watch frontend

or

    npx shadow-cljs watch frontend

The app will be available at http://localhost:8280/

## Stylesheets

Run `clojure -A:garden -m chicken-master.css` to generate the css files.

## Testing

Setup Karma:

    npm install karma karma-cljs-test --save-dev
    npm install karma-chrome-launcher --save-dev
    sudo npm install -g karma-cli

Compile the code:

    npx shadow-cljs compile ci

Start the tester:

    karma start

If the tests are only to be run once, use `karma start --single-run`
