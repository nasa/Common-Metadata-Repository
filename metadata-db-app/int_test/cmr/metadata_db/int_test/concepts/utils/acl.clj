(ns cmr.metadata-db.int-test.concepts.utils.acl
  "Defines implementations for all of the multi-methods for acls in the metadata-db
  integration tests."
  (:require
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]))

(def acl-edn
  (pr-str {:name "Some ACL"
           :etc "TBD"}))

(defmethod concepts/get-sample-metadata :acl
  [_]
  acl-edn)

(defn create-acl-concept
  "Creates an ACL concept"
  [provider-id uniq-num attributes]
  (let [attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/edn"
                           :extra-fields {:acl-identity (str "test-identity:" provider-id ":" uniq-num)}}
                          attributes)]
    (concepts/create-any-concept provider-id :acl uniq-num attributes)))

(defmethod concepts/create-concept :acl
  [concept-type & args]
  (let [[provider-id uniq-num attributes] (concepts/parse-create-concept-args :acl args)]
    (create-acl-concept provider-id uniq-num attributes)))
