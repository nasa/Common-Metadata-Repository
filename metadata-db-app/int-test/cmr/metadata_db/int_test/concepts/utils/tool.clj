(ns cmr.metadata-db.int-test.concepts.utils.tool
  "Defines implementations for all of the multi-methods for tools in the metadata-db
  integration tests."
  (:require
   [cheshire.core :as json]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]))

(def tool-json
  (json/generate-string
   {:Name "someTool"
    :Other "TBD"}))

(defmethod concepts/get-sample-metadata :tool
  [_]
  tool-json)

(defn- create-tool-concept
  "Creates a tool concept"
  [provider-id uniq-num attributes]
  (let [native-id (str "tl-native" uniq-num)
        extra-fields (merge {:tool-name (str "tl" uniq-num)}
                            (:extra-fields attributes))
        attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/json"
                           :native-id native-id
                           :extra-fields extra-fields}
                          (dissoc attributes :extra-fields))]
    (concepts/create-any-concept provider-id :tool uniq-num attributes)))

(defmethod concepts/create-concept :tool
  [concept-type & args]
  (let [[provider-id uniq-num attributes] (concepts/parse-create-concept-args :tool args)]
    (create-tool-concept provider-id uniq-num attributes)))
