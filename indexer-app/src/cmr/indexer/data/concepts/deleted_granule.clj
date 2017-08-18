(ns cmr.indexer.data.concepts.deleted-granule
  "Contains functions to parse and convert deleted-granule index document"
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clj-time.core :as t]
   [clojure.string :as s]
   [cmr.common.cache :as cache]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.elastic-utils.index-util :as index-util]
   [cmr.indexer.data.concepts.attribute :as attrib]
   [cmr.indexer.data.concepts.orbit-calculated-spatial-domain :as ocsd]
   [cmr.indexer.data.concepts.spatial :as spatial]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.umm-spec.umm-spec-core :as umm-spec]
   [cmr.umm.echo10.spatial :as umm-spatial]
   [cmr.umm.related-url-helper :as ru]
   [cmr.umm.start-end-date :as sed])
  (:import
   (cmr.spatial.mbr Mbr)))

(defn deleted-granule->elastic-doc
  "Returns elastic json that can be used to insert the given granule concept in elasticsearch."
  [deleted-granule original-granule-concept]
  (let [{:keys [concept-id provider-id extra-fields]} original-granule-concept
        {:keys [granule-ur parent-collection-id]} extra-fields]
    {:concept-id concept-id
     :delete-time (t/now)
     :provider-id provider-id
     :granule-ur granule-ur
     :parent-collection-id parent-collection-id}))

(defmethod es/parsed-concept->elastic-doc :deleted-granule
  [context deleted-granule original-granule-concept]
  (deleted-granule->elastic-doc deleted-granule original-granule-concept))
