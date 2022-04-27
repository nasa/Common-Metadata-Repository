(ns cmr.metadata-db.int-test.concepts.utils.subscription
  "Defines implementations for all of the multi-methods for subscriptions in the metadata-db
  integration tests."
  (:require
   [cheshire.core :as json]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]))

(def subscription-json
  (json/generate-string
   {"Name" "someSubscription"
    "SubscriberId" "someSubscriberId"
    "CollectionConceptId" "C1234-PROV1"
    "Query" "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"}))

(defmethod concepts/get-sample-metadata :subscription
  [_]
  subscription-json)

(defn- create-subscription-concept
  "Creates a subscription concept"
  [provider-id uniq-num attributes]
  (let [native-id (str "sub-native" uniq-num)
        extra-fields (merge {:subscription-name (str "subname" uniq-num)
                             :subscriber-id (str "subid" uniq-num)
                             :collection-concept-id "C12345-PROV1"
                             :subscription-type "granule"
                             :normalized-query (str "instrument=" uniq-num "B")}
                            (:extra-fields attributes))
        attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/json"
                           :native-id native-id
                           :extra-fields extra-fields}
                          (dissoc attributes :extra-fields))]
    (concepts/create-any-concept provider-id :subscription uniq-num attributes)))

(defmethod concepts/create-concept :subscription
  [concept-type & args]
  (let [[provider-id uniq-num attributes] (concepts/parse-create-concept-args :subscription args)]
    (create-subscription-concept provider-id uniq-num attributes)))
