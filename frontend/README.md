# Frontend

All visible stuff.

## Development

Start the server:

    npx shadow-cljs watch frontend

The app will be available at http://localhost:8280/

## Stylesheets

Run `clojure -A:garden -m chicken-master.css compile` to generate the css files once.
Run `clojure -A:garden -m chicken-master.css watch` to generate the css files on any change.

## Testing

Setup Karma:

    npm install karma karma-cljs-test --save-dev
    npm install karma-chrome-launcher --save-dev
    sudo npm install -g karma-cli

Compile the code:

    npx shadow-cljs compile ci

Start the tester:

    CHROME_BIN=/usr/bin/chromium karma start

If the tests are only to be run once, use `CHROME_BIN=/usr/bin/chromium karma start --single-run`
