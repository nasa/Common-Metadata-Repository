(defproject cmr-es-spatial-plugin "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.elasticsearch/elasticsearch "0.90.7"]]
  :aot [cmr.es-spatial-plugin.FooBarSearchScript
        cmr.es-spatial-plugin.FooBarSearchScriptFactory
        cmr.es-spatial-plugin.SpatialSearchPlugin])
