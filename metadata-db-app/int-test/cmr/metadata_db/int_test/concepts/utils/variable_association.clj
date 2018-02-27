(ns cmr.metadata-db.int-test.concepts.utils.variable-association
  "Defines implementations for all of the multi-methods for variable associations in the metadata-db
  integration tests."
  (:require
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [clojure.string :as string]))

(def variable-association-edn
  "Valid EDN for variable association metadata"
  (pr-str {:variable-concept-id "V120000008-PROV1"
           :associated-concept-id "C120000000-PROV1"
           :revision-id 1
           :value "Some Value"}))

(defmethod concepts/get-sample-metadata :variable-association
  [_]
  variable-association-edn)

(defn- create-variable-association-concept
  "Creates a variable association concept"
  [assoc-concept variable uniq-num attributes]
  (let [{:keys [concept-id revision-id]} assoc-concept
        variable-concept-id (:concept-id variable)
        user-id (str "user" uniq-num)
        native-id (string/join "/" [variable-concept-id concept-id revision-id])
        extra-fields (merge {:associated-concept-id concept-id
                             :associated-revision-id revision-id
                             :variable-concept-id variable-concept-id}
                            (:extra-fields attributes))
        attributes (merge {:user-id user-id
                           :format "application/edn"
                           :native-id native-id
                           :extra-fields extra-fields}
                          (dissoc attributes :extra-fields))]
   ;; no provider-id should be specified for variable associations
   (dissoc (concepts/create-any-concept nil :variable-association uniq-num attributes)
           :provider-id)))

(defmethod concepts/create-concept :variable-association
  [concept-type & args]
  (let [[associated-concept concept uniq-num attributes] (concepts/parse-create-concept-args
                                                          :variable-association args)]
    (create-variable-association-concept associated-concept concept uniq-num attributes)))

(defmethod concepts/parse-create-concept-args :variable-association
  [concept-type args]
  (concepts/parse-create-associations-args args))

(defmethod concepts/parse-create-and-save-args :variable-association
  [concept-type args]
  (concepts/parse-create-and-save-associations-args args))
