(ns cmr.metadata-db.int-test.concepts.utils.access-group
  "Defines implementations for all of the multi-methods for access-groups in the metadata-db
  integration tests."
  (:require
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]))

(def access-group-edn
  "Valid EDN for group metadata"
  (pr-str {:name "LPDAAC_ECS Administrators"
            :provider-id "LPDAAC_ECS"
            :description "Contains users with permission to manage LPDAAC_ECS holdings."
            :members ["jsmith" "prevere" "ndrew"]}))

(defmethod concepts/get-sample-metadata :access-group
  [_]
  access-group-edn)

(defn create-access-group-concept
  "Creates a group concept"
  [provider-id uniq-num attributes]
  (let [attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/edn"}
                          attributes)]
    (concepts/create-any-concept provider-id :access-group uniq-num attributes)))

(defmethod concepts/create-concept :access-group
  [concept-type & args]
  (let [[provider-id uniq-num attributes] (concepts/parse-create-concept-args :access-group args)]
    (create-access-group-concept provider-id uniq-num attributes)))
