(ns cmr.search.test.data.metadata-retrieval.metadata-cache
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.search.data.metadata-retrieval.metadata-cache :as mc]
   [cmr.search.test.data.metadata-retrieval.revision-format-map :as rfm]))

(def get-cached-metadata-in-format #'mc/get-cached-metadata-in-format)

(defn context-with-cache-map
  [cache-map]
  (let [cache (mc/create-cache)]
    (reset! (:cache-atom cache) cache-map)
    {:system {:caches {mc/cache-key cache}}}))

(def empty-response
  {:revision-format-maps []
   :target-format-not-cached []
   :concept-not-cached []
   :older-revision-requested []
   :newer-revision-requested []})

(deftest get-cached-metadata-in-format-test
  (let [rfm1 (rfm/test-rfm "C1" 1 [:dif])
        rfm2 (rfm/test-rfm "C2" 2 [:echo10])
        rfm3 (rfm/test-rfm "C3" 3 [:echo10])
        cache-map {"C1" rfm1 "C2" rfm2 "C3" rfm3}
        context (context-with-cache-map cache-map)]
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
