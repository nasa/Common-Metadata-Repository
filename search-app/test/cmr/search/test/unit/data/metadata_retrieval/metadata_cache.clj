(ns cmr.search.test.unit.data.metadata-retrieval.metadata-cache
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.data.metadata-retrieval.collection-metadata-cache :as cmn-coll-metadata-cache]
   [cmr.common-app.data.metadata-retrieval.revision-format-map :as crfm]
   [cmr.common.util :refer [are3]]
   [cmr.redis-utils.redis-hash-cache :as redis-hash-cache]
   [cmr.redis-utils.test.test-util :as test-util]
   [cmr.search.data.metadata-retrieval.metadata-cache :as mc]
   [cmr.search.data.metadata-retrieval.metadata-transformer :as metadata-transformer]
   [cmr.search.test.unit.data.metadata-retrieval.revision-format-map :as trfm]
   [cmr.search.test.unit.data.metadata-retrieval.test-metadata :as tm]
   [cmr.common.hash-cache :as hash-cache]))

(use-fixtures :each test-util/embedded-redis-server-fixture)

(def get-cached-metadata-in-format #'mc/get-cached-metadata-in-format)

(defn test-rfm
  "Creates a revision format map with the specified formats."
  [concept-id revision-id formats]
  (-> (crfm/concept->revision-format-map nil 
                                         tm/dif10-concept 
                                         trfm/all-metadata-formats 
                                         metadata-transformer/transform-to-multiple-formats)
      (select-keys (concat formats [:native-format]))
      (assoc :concept-id concept-id
             :revision-id revision-id)))

(def empty-response
  {:revision-format-maps []
   :target-format-not-cached []
   :concept-not-cached []
   :older-revision-requested []
   :newer-revision-requested []})

(deftest get-cached-metadata-in-format-test
  (let [cache-key cmn-coll-metadata-cache/cache-key
        cache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [cache-key]})
        _ (hash-cache/reset cache cache-key)
        rfm1 (test-rfm "C1" 1 [:dif])
        rfm2 (test-rfm "C2" 2 [:echo10])
        rfm3 (test-rfm "C3" 3 [:echo10])
        context {:system {:caches {cache-key cache}}}]
    (hash-cache/set-value cache cache-key "C1" rfm1)
    (hash-cache/set-value cache cache-key "C2" rfm2)
    (hash-cache/set-value cache cache-key "C3" rfm3)
    (are3 [expected tuples format]
      (is (= (merge empty-response expected)
             (get-cached-metadata-in-format context tuples format)))

      "Concepts not cached"
      {:concept-not-cached [["C4" 1] ["C5" 2]]}
      [["C4" 1] ["C5" 2]] :echo10

      "Older revisions requested"
      {:older-revision-requested [["C2" 1] ["C3" 2]]}
      [["C2" 1] ["C3" 2]] :echo10

      "Newer revisions requested"
      {:newer-revision-requested [["C2" 3] ["C3" 4]]}
      [["C2" 3] ["C3" 4]] :echo10

      "Target format not cached"
      {:target-format-not-cached [rfm2 rfm3]} ;; real revision format maps are in this key
      [["C2" 2] ["C3" 3]] :iso19115

      "Cache hit"
      {:revision-format-maps [rfm2 rfm3]} ;; real revision format maps are in this key
      [["C2" 2] ["C3" 3]] :echo10

      "Combination hit"
      {:revision-format-maps [rfm2 rfm3]
       :target-format-not-cached [rfm1]
       :concept-not-cached [["C5" 1]]
       :older-revision-requested [["C2" 1]]
       :newer-revision-requested [["C1" 2]]} ;; real revision format maps are in this key
      [["C1" 1] ["C2" 2] ["C3" 3]
       ["C1" 2] ["C2" 1] ["C5" 1]]
      :echo10)))
