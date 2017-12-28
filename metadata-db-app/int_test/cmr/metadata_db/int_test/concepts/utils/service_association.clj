(ns cmr.metadata-db.int-test.concepts.utils.service-association
  "Defines implementations for all of the multi-methods for service associations in the metadata-db
  integration tests."
  (:require
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [clojure.string :as string]))

(def service-association-edn
  "Valid EDN for variable association metadata"
  (pr-str {:service-concept-id "S120000008-PROV1"
           :associated-concept-id "C120000000-PROV1"
           :revision-id 1}))

(defmethod concepts/get-sample-metadata :service-association
  [_]
  service-association-edn)

(defn- create-service-association-concept
  "Creates a service association concept"
  [assoc-concept service uniq-num attributes]
  (let [{:keys [concept-id revision-id]} assoc-concept
        service-concept-id (:concept-id service)
        user-id (str "user" uniq-num)
        native-id (string/join "/" [service-concept-id concept-id revision-id])
        extra-fields (merge {:associated-concept-id concept-id
                             :associated-revision-id revision-id
                             :service-concept-id service-concept-id}
                            (:extra-fields attributes))
        attributes (merge {:user-id user-id
                           :format "application/edn"
                           :native-id native-id
                           :extra-fields extra-fields}
                          (dissoc attributes :extra-fields))]
    ;; no provider-id should be specified for service associations
    (dissoc (concepts/create-any-concept nil :service-association uniq-num attributes)
            :provider-id)))

(defmethod concepts/create-concept :service-association
  [concept-type & args]
  (let [[associated-concept concept uniq-num attributes] (concepts/parse-create-concept-args
                                                          :service-association args)]
    (create-service-association-concept associated-concept concept uniq-num attributes)))

(defmethod concepts/parse-create-concept-args :service-association
  [concept-type args]
  (concepts/parse-create-associations-args args))

(defmethod concepts/parse-create-and-save-args :service-association
  [concept-type args]
  (concepts/parse-create-and-save-associations-args args))
