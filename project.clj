(defproject cmr-graph "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :plugins [[lein-shell "0.5.0"]]
  :aliases {
    "start-es" ["do"
      "shell" "resources/scripts/start-elasticsearch.sh"]
    "start-kibana" ["do"
      "shell" "resources/scripts/start-kibana.sh"]
    "start-neo4j" ["do"
      "shell" "resources/scripts/start-neo4j.sh"]
    "start-network" ["do"
      "shell" "resources/scripts/start-network.sh"]
    "start-all" ["do"
      ["start-neo4j"]
      ["start-es"]
      ["start-kibana"]
      ;["start-network"]
      ]
    "stop-all" ["do"
      "shell" "resources/scripts/stop-all.sh"]})
