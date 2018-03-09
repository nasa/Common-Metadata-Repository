(defproject cmr-graph "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :plugins [[lein-shell "0.5.0"]]

  :aliases {
    "start-infra"
      ["shell"
       "docker-compose" "-f" "resources/docker/docker-compose.yml" "up"]
    "stop-infra" [
      "shell"
      "docker-compose" "-f" "resources/docker/docker-compose.yml" "down"]})
