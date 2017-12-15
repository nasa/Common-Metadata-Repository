(ns cmr.metadata-db.int-test.concepts.utils.tag
  "Defines implementations for all of the multi-methods for tags in the metadata-db
  integration tests."
  (:require
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]))

(def tag-edn
  "Valid EDN for tag metadata"
  (pr-str {:tag-key "org.nasa.something.ozone"
           :description "A very good tag"
           :originator-id "jnorton"}))

(defmethod concepts/get-sample-metadata :tag
  [_]
  tag-edn)

(defn- create-tag-concept
  "Creates a tag concept"
  [_ uniq-num attributes]
  (let [native-id (str "tag-key" uniq-num)
        attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/edn"
                           :native-id native-id}
                          attributes)]
    ;; no provider-id should be specified for tags
    (dissoc (concepts/create-any-concept nil :tag uniq-num attributes) :provider-id)))

(defmethod concepts/create-concept :tag
  [concept-type & args]
  (let [[provider-id uniq-num attributes] (concepts/parse-create-concept-args :tag args)]
    (create-tag-concept provider-id uniq-num attributes)))
