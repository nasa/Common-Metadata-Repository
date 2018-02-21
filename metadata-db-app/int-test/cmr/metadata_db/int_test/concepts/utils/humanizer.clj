(ns cmr.metadata-db.int-test.concepts.utils.humanizer
  "Defines implementations for all of the multi-methods for humanizers in the metadata-db
  integration tests."
  (:require
   [cheshire.core :as json]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]))

(def humanizer-json
  (json/generate-string
    [{"type" "trim_whitespace", "field" "platform", "order" -100},
     {"type" "priority", "field" "platform", "source_value" "Aqua", "order" 10, "priority" 10}]))

(defmethod concepts/get-sample-metadata :humanizer
  [_]
  humanizer-json)

(defn- create-humanizer-concept
  "Creates a humanizer concept"
  [_ uniq-num attributes]
  (let [native-id "humanizer"
        attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/json"
                           :native-id native-id}
                          attributes)]
    ;; no provider-id should be specified for humanizers
    (dissoc (concepts/create-any-concept nil :humanizer uniq-num attributes) :provider-id)))

(defmethod concepts/create-concept :humanizer
  [concept-type & args]
  (let [[provider-id uniq-num attributes] (concepts/parse-create-concept-args :humanizer args)]
    (create-humanizer-concept provider-id uniq-num attributes)))
