(ns cmr.elasticsearch.plugins.spatial.factory.core
  (:import
   (cmr.elasticsearch.plugins SpatialScriptLeafFactory)
   (java.util Map)
   (org.elasticsearch.search.lookup SearchLookup))
  (:gen-class
   :name cmr.elasticsearch.plugins.SpatialScriptFactory
   :implements [org.elasticsearch.script.FilterScript$Factory
                org.elasticsearch.script.FilterScript$LeafFactory]
   :state data))

(import 'cmr.elasticsearch.plugins.SpatialScriptFactory)

(defn -isResultDeterministic
  "Implies the results are cacheable if true.
   See [[https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting-engine.html]]"
  [^SpatialScriptFactory this]
  false)

(defn -newFactory
  [^SpatialScriptFactory this ^Map params ^SearchLookup lookup]
  (new SpatialScriptLeafFactory params lookup))
