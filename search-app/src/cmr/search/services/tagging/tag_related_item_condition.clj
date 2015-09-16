(ns cmr.search.services.tagging.tag-related-item-condition
  "Contains a function for creating a tag related item query condition along with the function to
  process the related tag items."
  (:require [cmr.search.models.query :as qm]
            [cmr.common.util :as util]
            [clojure.edn :as edn]))

(defn- convert-results-to-concept-id-condition
  "Extracts the associated concept ids from the elastic results and creates a string condition
  matching on the concept ids."
  [elastic-results]
  (let [associated-concept-ids (->> (get-in elastic-results [:hits :hits])
                                    ;; Extract associated concept id sets that are GZIP'd
                                    (map #(get-in % [:fields :associated-concept-ids-gzip-b64 0]))
                                    ;; Decompress them
                                    (map util/gzip-base64->string)
                                    ;; Read them as clojure sets
                                    (map edn/read-string)
                                    ;; Combine all the sets together
                                    (reduce into #{}))]
    (if (seq associated-concept-ids)
      (qm/string-conditions :concept-id associated-concept-ids true)
      qm/match-none)))

(defn tag-related-item-query-condition
  "Creates a related item query condition that will find tags matching the given condition. The
  related item query condition will be replaced with an equivalent condition matching concept ids."
  [condition]
  (qm/map->RelatedItemQueryCondition
    {:concept-type :tag
     :condition condition
     :result-fields [:associated-concept-ids-gzip-b64]
     :results-to-condition-fn convert-results-to-concept-id-condition}))