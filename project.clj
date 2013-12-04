(defproject cmr-es-spatial-plugin "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.elasticsearch/elasticsearch "0.90.7"]

                 ;; Versions set to match elastic search numbers. Look in elasticsearch pom.xml
                 [log4j/log4j "1.2.17"]]

  :aot [cmr.es-spatial-plugin.StringMatchScript
        cmr.es-spatial-plugin.StringMatchScriptFactory
        cmr.es-spatial-plugin.SpatialSearchPlugin]

  :profiles
  {:integration
   {:jvm-opts ["-Des.config=integration_test/elasticsearch.yml"
               "-Des.path.conf=integration_test"

               ;; uncomment to debug log4j
               "-Dlog4j.debug=true"

               ;; important to allow logging
               "-Des.foreground=true"]}}

  :aliases {"es" ["with-profile" "integration"
                  "do" "clean,"
                  "compile,"
                  "run" "-m" "org.elasticsearch.bootstrap.ElasticSearch"
                  ]})
