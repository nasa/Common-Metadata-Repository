(defproject cmr-graph "0.1.0-SNAPSHOT"
  :description "A service and API for querying CMR metadata relationships"
  :url "https://github.com/cmr-exchange/cmr-graph"
  :license {
    :name "Apache License, Version 2.0"
    :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [
    [clojurewerkz/neocons "3.2.0"]
    [org.clojure/clojure "1.8.0"]]
  :plugins [
    [lein-shell "0.5.0"]]
  :aliases {
    "start-infra"
      ["shell"
       "docker-compose" "-f" "resources/docker/docker-compose.yml" "up"]
    "stop-infra" [
      "shell"
      "docker-compose" "-f" "resources/docker/docker-compose.yml" "down"]
    "neo4j-bash" [
      "shell"
      "docker" "exec" "-it" "cmr-graph-neo4j" "bash"]
    "elastic-bash" [
      "shell"
      "docker" "exec" "-it" "cmr-graph-elastic" "bash"]
    "kibana-bash" [
      "shell"
      "docker" "exec" "-it" "cmr-graph-kibana" "bash"]})
