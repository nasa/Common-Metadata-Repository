(ns cmr.metadata-db.int-test.concepts.utils.tag-association
  "Defines implementations for all of the multi-methods for tag associations in the metadata-db
  integration tests."
  (:require
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [clojure.string :as string]))

(def tag-association-edn
  "Valid EDN for tag association metadata"
  (pr-str {:tag-key "org.nasa.something.ozone"
           :associated-concept-id "C120000000-PROV1"
           :revision-id 1
           :value "Some Value"}))

(defmethod concepts/get-sample-metadata :tag-association
  [_]
  tag-association-edn)

(defn- create-tag-association-concept
  "Creates a tag association concept"
  [assoc-concept tag uniq-num attributes]
  (let [{:keys [concept-id revision-id]} assoc-concept
        tag-id (:native-id tag)
        user-id (str "user" uniq-num)
        native-id (string/join "/" [tag-id concept-id revision-id])
        extra-fields (merge {:associated-concept-id concept-id
                             :associated-revision-id revision-id
                             :tag-key tag-id}
                            (:extra-fields attributes))
        attributes (merge {:user-id user-id
                           :format "application/edn"
                           :native-id native-id
                           :extra-fields extra-fields}
                          (dissoc attributes :extra-fields))]
    ;; no provider-id should be specified for tag associations
    (dissoc (concepts/create-any-concept nil :tag-association uniq-num attributes) :provider-id)))

(defmethod concepts/create-concept :tag-association
  [concept-type & args]
  (let [[associated-concept concept uniq-num attributes] (concepts/parse-create-concept-args
                                                          :tag-association args)]
    (create-tag-association-concept associated-concept concept uniq-num attributes)))

(defmethod concepts/parse-create-concept-args :tag-association
  [concept-type args]
  (concepts/parse-create-associations-args args))

(defmethod concepts/parse-create-and-save-args :tag-association
  [concept-type args]
  (concepts/parse-create-and-save-associations-args args))
