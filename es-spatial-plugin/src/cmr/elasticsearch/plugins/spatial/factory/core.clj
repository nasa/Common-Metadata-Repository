(ns cmr.elasticsearch.plugins.spatial.factory.core
  (:require
   [clojure.string :as str]
   [cmr.spatial.derived :as d]
   [cmr.spatial.point :as point]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.relations :as relations]
   [cmr.spatial.serialize :as srl])
  (:import
   (cmr.elasticsearch.plugins SpatialScriptLeafFactory)
   (java.util Map)
   (org.apache.lucene.index LeafReaderContext)
   (org.elasticsearch.common.settings Settings)
   (org.elasticsearch.common.xcontent.support XContentMapValues)
   (org.elasticsearch.search.lookup SearchLookup))
  (:gen-class
   :name cmr.elasticsearch.plugins.SpatialScriptFactory
   :implements [org.elasticsearch.script.FilterScript$Factory
                org.elasticsearch.script.FilterScript$LeafFactory]
   :state data))

(import 'cmr.elasticsearch.plugins.SpatialScriptFactory)

(defn -newFactory
  [^SpatialScriptFactory this ^Map params ^SearchLookup lookup]
  (new SpatialScriptLeafFactory params lookup))
