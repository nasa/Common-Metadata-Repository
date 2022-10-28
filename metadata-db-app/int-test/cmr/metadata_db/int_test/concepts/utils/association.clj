(ns cmr.metadata-db.int-test.concepts.utils.association
  "Defines implementations for all of the multi-methods for associations in the metadata-db
  integration tests."
  (:require
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [clojure.string :as string]))

(def association-edn
  "Valid EDN for generic association metadata"
  (pr-str {:source-concept-identifier "DQS120000008-PROV1"
           :source-revision-id 1
           :associated-concept-id "C120000000-PROV1"
           :associated-revision-id 1
           :data {:XYZ "ZYX"}}))

(defmethod concepts/get-sample-metadata :generic-association
  [_]
  association-edn)

(defn- create-association-concept
  "Creates a association concept"
  [assoc-concept first-concept uniq-num attributes]
  (let [{:keys [concept-id revision-id]} assoc-concept
        first-concept-id (:concept-id first-concept)
        first-revision-id (:revision-id first-concept)
        user-id (str "user" uniq-num)
        native-id (string/join "/" [first-concept-id concept-id revision-id])
        extra-fields (merge {:associated-concept-id concept-id
                             :associated-revision-id revision-id
                             :source-concept-identifier first-concept-id
                             :source-concept-id first-revision-id}
                            (:extra-fields attributes))
        attributes (merge {:user-id user-id
                           :format "application/edn"
                           :native-id native-id
                           :extra-fields extra-fields}
                          (dissoc attributes :extra-fields))]
    (concepts/create-any-concept "CMR" :generic-association uniq-num attributes)))

(defmethod concepts/create-concept :generic-association
  [_concept-type & args]
  (let [[associated-concept concept uniq-num attributes] (concepts/parse-create-concept-args
                                                          :generic-association args)]
    (create-association-concept associated-concept concept uniq-num attributes)))

(defmethod concepts/parse-create-concept-args :generic-association
  [_concept-type args]
  (concepts/parse-create-associations-args args))

(defmethod concepts/parse-create-and-save-args :generic-association
  [_concept-type args]
  (concepts/parse-create-and-save-associations-args args))
