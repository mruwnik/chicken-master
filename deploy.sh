cd backend
clojure -X:depstar uberjar

cd ../frontend
npx shadow-cljs release frontend
clojure -A:garden -m chicken-master.css

cd ..
scp backend/chicken-master.jar chickens:/srv/chickens/chicken-master.jar
ssh chickens 'mkdir -p /srv/chickens/frontend/js'
ssh chickens 'mkdir -p /srv/chickens/frontend/css'
rsync -r frontend/resources/public/index.html chickens:/srv/chickens/frontend/index.html
rsync -r frontend/resources/public/css/screen.css chickens:/srv/chickens/frontend/css/screen.css
rsync -r frontend/resources/public/js/app.js chickens:/srv/chickens/frontend/js/app.js
