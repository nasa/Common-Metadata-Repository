(ns cmr.system-int-test.data2.opendata
  "Contains functions for parsing opendata results."
  (:require [clojure.data.xml :as x]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.line-string :as l]
            [clojure.string :as str]
            [cmr.spatial.relations :as r]
            [clojure.test]
            [cheshire.core :as json]
            [cmr.common.util :as util]
            [cmr.search.results-handlers.opendata-results-handler :as odrh]
            [cmr.umm.related-url-helper :as ru])
  (:import cmr.umm.collection.UmmCollection
           cmr.spatial.mbr.Mbr))

(defn parse-opendata-result
  "Returns the opendata result from a json string"
  [concept-type json-str]
  (let [json-struct (json/decode json-str true)]
    json-struct))

(defn collection->expected-opendata
  [collection]
  (let [{:keys [short-name keywords project-sn summary entry-title
                access-value concept-id related-urls]} collection
        update-time (get-in collection [:data-provider-timestamps :update-time])
        insert-time (get-in collection [:data-provider-timestamps :insert-time])]
    (util/remove-nil-keys {:identifier concept-id
                           :description summary
                           :accessLevel (odrh/short-name->access-level short-name)
                           :accessURL (ru/related-urls->opendata-access-url related-urls)
                           :programCode odrh/PROGRAM_CODE
                           :bureauCode odrh/BUREAU_CODE
                           :publisher odrh/PUBLISHER
                           :language odrh/LANGUAGE_CODE
                           :title entry-title
                           :format (ru/related-urls->opendata-format related-urls)
                           :modified (str update-time)
                           :issued (str insert-time)})))

(defn collections->expected-opendata
  [collections]
  {:status 200
   :results (set (map collection->expected-opendata collections))})

(defn assert-collection-opendata-results-match
  "Returns true if the opendata results are for the expected items"
  [collections actual-result]
  (clojure.test/is (= (collections->expected-opendata collections)
                      (update-in actual-result [:results] set))))