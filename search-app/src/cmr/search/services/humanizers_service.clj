(ns cmr.search.services.humanizers-service
  "Provides functions for reporting on humanizers"
  (require [cmr.common.concepts :as concepts]
           [cmr.common.util :as u]
           [cmr.common-app.humanizer :as humanizer]
           [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
           [cmr.search.data.metadata-retrieval.revision-format-map :as rfm]
           [cmr.umm-spec.legacy :as umm-legacy]
           [clojure.data.csv :as csv])
  (:import java.io.StringWriter))

(def CSV_HEADER
  ["provider", "concept_id", "short_name" "version", "original_value", "humanized_value"])

(defn- rfm->umm-collection
  "Takes a revision format map and parses it into a UMM lib record."
  [context revision-format-map]
  (let [concept-id (:concept-id revision-format-map)
        umm (umm-legacy/parse-concept
             context
             (rfm/revision-format-map->concept :native revision-format-map))]
    (assoc umm
           :concept-id concept-id
           :provider-id (:provider-id (concepts/parse-concept-id concept-id)))))

(defn- get-all-collections
  "Retrieves all collections from the Metadata cache"
  [context]
  ;; TODO should this throw an exception if the cache isn't populated
  ;; TODO should this use pmap? (Test performance in workload and then look at times if too slow.)
  (map #(rfm->umm-collection context %) (metadata-cache/all-cached-revision-format-maps context)))

(comment
 (do
   (def context {:system (get-in user/system [:apps :search])})
   (metadata-cache/refresh-cache context))

 (->> (get-all-collections context)
      (map humanizer/umm-collection->umm-collection+humanizers)
      (mapcat humanized-collection->reported-fields)))

;; TODO unit tests are needed for this function.
;; Create a collection with multiple values for each of the humanized fields.
;; There should be at least three values for each field.
;; - Two values that will get humanized and is reportable
;; - one value that is humanized but not reportable.
;; Dynamically setup some humanizers in the test that will make the above happen.

(defn humanized-collection->reported-rows
  "Takes a humanized collection and returns rows to populate the CSV report."
  [collection]
  (let [{:keys [provider-id concept-id product]} collection
        {:keys [short-name version-id]} product]
    (for [paths (vals humanizer/humanizer-field->umm-paths)
          path paths
          parents (u/get-in-all collection (drop-last path))
          :let [parents (if (sequential? parents)
                          parents
                          [parents])]
          parent parents
          :let [field (last path)
                humanized-value (get parent (humanizer/humanizer-key field))]
          :when (and (some? humanized-value) (:reportable humanized-value))
          :let [humanized-string-value (:value humanized-value)
                original-value (get parent field)]]
      [provider-id concept-id short-name version-id original-value humanized-string-value])))

(defn humanizers-report-csv
  "Returns a report on humanizers in use in collections as a CSV."
  [context]
  ;; TODO add logging of times taken for each step. See Metadata Cache functions for examples of how we do that.
  (let [collections (get-all-collections context)
        humanized-rows (pmap (fn [coll]
                               (->> coll
                                    humanizer/umm-collection->umm-collection+humanizers
                                    humanized-collection->reported-rows))
                             collections)
        rows (cons CSV_HEADER
                   (apply concat humanized-rows))
        string-writer (StringWriter.)]
    (csv/write-csv string-writer rows)
    (str string-writer)))
