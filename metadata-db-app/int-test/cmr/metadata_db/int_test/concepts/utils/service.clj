(ns cmr.metadata-db.int-test.concepts.utils.service
  "Defines implementations for all of the multi-methods for services in the metadata-db
  integration tests."
  (:require
   [cheshire.core :as json]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]))

(def service-json
  (json/generate-string
   {"Name" "someService"
    "Other" "TBD"}))

(defmethod concepts/get-sample-metadata :service
  [_]
  service-json)

(defn- create-service-concept
  "Creates a service concept"
  [provider-id uniq-num attributes]
  (let [native-id (str "svc-native" uniq-num)
        extra-fields (merge {:service-name (str "svc" uniq-num)}
                            (:extra-fields attributes))
        attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/json"
                           :native-id native-id
                           :extra-fields extra-fields}
                          (dissoc attributes :extra-fields))]
    (concepts/create-any-concept provider-id :service uniq-num attributes)))

(defmethod concepts/create-concept :service
  [concept-type & args]
  (let [[provider-id uniq-num attributes] (concepts/parse-create-concept-args :service args)]
    (create-service-concept provider-id uniq-num attributes)))
