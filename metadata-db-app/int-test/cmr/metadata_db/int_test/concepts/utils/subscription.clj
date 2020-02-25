(ns cmr.metadata-db.int-test.concepts.utils.subscription
  "Defines implementations for all of the multi-methods for subscriptions in the metadata-db
  integration tests."
  (:require
   [cheshire.core :as json]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]))

(def subscription-metadata
  "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78")

(defmethod concepts/get-sample-metadata :subscription
  [_]
  subscription-metadata)

(defn- create-subscription-concept
  "Creates a subscription concept"
  [_ uniq-num attributes]
  (let [attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/json"
                           :email-address "user1@yahoo.com"
                           :collection-concept-id "C123-PROV1"
                           :description "subscription description"}
                          attributes)]
    ;; no provider-id should be specified for subscription 
    (dissoc (concepts/create-any-concept nil :subscription uniq-num attributes) :provider-id)))

(defmethod concepts/create-concept :subscription
  [concept-type & args]
  (let [[provider-id uniq-num attributes] (concepts/parse-create-concept-args :subscription args)]
    (create-subscription-concept provider-id uniq-num attributes)))
