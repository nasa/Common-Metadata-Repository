(ns cmr.system-int-test.bootstrap.bulk-index.core
  "CMR bulk indexing integration common functions."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [is]]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.service-util :as service-util]
   [cmr.system-int-test.utils.subscription-util :as sub-util]
   [cmr.system-int-test.utils.tool-util :as tool-util]
   [cmr.system-int-test.utils.variable-util :as var-util]
   [cmr.umm.echo10.echo10-core :as echo10]))

(defn disable-automatic-indexing
  "This is intended for use in system integration tests that don't want to have
  ingested data indexed."
  []
  (dev-sys-util/eval-in-dev-sys
   `(cmr.metadata-db.config/set-publish-messages! false)))

(defn reenable-automatic-indexing
  "This is intended for use in system integration tests that want to return
  automatic indexing to its defuault state."
  []
  (dev-sys-util/eval-in-dev-sys
   `(cmr.metadata-db.config/set-publish-messages! true)))

(defn save-collection
  "Saves a collection concept"
  ([provider-id n]
   (save-collection provider-id n {}))
  ([provider-id n attributes]
   (let [unique-str (str "coll" n)
         umm (dc/collection {:short-name unique-str :entry-title unique-str})
         xml (echo10/umm->echo10-xml umm)
         coll (mdb/save-concept (merge
                                 {:concept-type :collection
                                  :format "application/echo10+xml"
                                  :metadata xml
                                  :extra-fields {:short-name unique-str
                                                 :entry-title unique-str
                                                 :entry-id unique-str
                                                 :version-id "v1"}
                                  :revision-date "2000-01-01T10:00:00Z"
                                  :provider-id provider-id
                                  :native-id unique-str
                                  :short-name unique-str}
                                 attributes))]
     ;; Make sure the concept was saved successfully
     (is (= 201 (:status coll)))
     (merge umm (select-keys coll [:concept-id :revision-id])))))

(defn save-granule
  "Saves a granule concept"
  ([provider-id n collection]
   (save-granule provider-id n collection {}))
  ([provider-id n collection attributes]
   (let [unique-str (str "gran" n)
         umm (dg/granule collection {:granule-ur unique-str})
         xml (echo10/umm->echo10-xml umm)
         gran (mdb/save-concept (merge
                                 {:concept-type :granule
                                  :provider-id provider-id
                                  :native-id unique-str
                                  :format "application/echo10+xml"
                                  :metadata xml
                                  :revision-date "2000-01-01T10:00:00Z"
                                  :extra-fields {:parent-collection-id (:concept-id collection)
                                                 :parent-entry-title (:entry-title collection)
                                                 :granule-ur unique-str}}
                                 attributes))]
     ;; Make sure the concept was saved successfully
     (is (= 201 (:status gran)))
     (merge umm (select-keys gran [:concept-id :revision-id])))))

(defn save-tag
  "Saves a tag concept"
  ([n]
   (save-tag n {}))
  ([n attributes]
   (let [unique-str (str "tag" n)
         tag (mdb/save-concept (merge
                                {:concept-type :tag
                                 :native-id unique-str
                                 :user-id "user1"
                                 :format "application/edn"
                                 :metadata (str "{:tag-key \"" unique-str "\" :description \"A good tag\" :originator-id \"user1\"}")
                                 :revision-date "2000-01-01T10:00:00Z"}
                                attributes))]
     ;; Make sure the concept was saved successfully
     (is (= 201 (:status tag)))
     (merge tag (select-keys tag [:concept-id :revision-id])))))

(defn save-acl
  "Saves an acl"
  [n attributes target]
  (let [unique-str (str "acl" n)
        acl (mdb/save-concept (merge
                               {:concept-type :acl
                                :provider-id "CMR"
                                :native-id unique-str
                                :format "application/edn"
                                :metadata (pr-str {:group-permissions [{:user-type "guest"
                                                                        :permissions ["read" "update"]}]
                                                   :system-identity {:target target}})
                                :revision-date "2000-01-01T10:00:00Z"}
                               attributes))]
    ;; Make sure the acl was saved successfully
    (is (= 201 (:status acl)))
    acl))

(defn save-group
  "Saves a group"
  ([n]
   (save-group n {}))
  ([n attributes]
   (let [unique-str (str "group" n)
         group (mdb/save-concept (merge
                                  {:concept-type :access-group
                                   :provider-id "CMR"
                                   :native-id unique-str
                                   :format "application/edn"
                                   :metadata "{:name \"Administrators\"
                                               :description \"The group of users that manages the CMR.\"
                                               :members [\"user1\" \"user2\"]}"
                                   :revision-date "2000-01-01T10:00:00Z"}
                                  attributes))]
     ;; Make sure the group was saved successfully
     (is (= 201 (:status group)))
     group)))

(defn save-variable
  "Saves a variable concept"
  ([n]
   (save-variable n {}))
  ([n attributes]
   (let [var-concept (var-util/make-variable-concept {} attributes n)
         parsed (json/parse-string (:metadata var-concept) true)
         variable (mdb/save-concept (merge
                                     var-concept
                                     {:extra-fields {:variable-name (:Name parsed)
                                                     :measurement (:LongName parsed)}}
                                     attributes))]
     ;; Make sure the concept was saved successfully
     (is (= 201 (:status variable)))
     (select-keys variable [:concept-id :revision-id]))))

(defn save-service
  "Saves a service concept"
  ([n]
   (save-service n {}))
  ([n attributes]
   (let [service-concept (service-util/make-service-concept {} attributes n)
         parsed (json/parse-string (:metadata service-concept) true)
         service (mdb/save-concept (merge
                                    service-concept
                                    {:extra-fields {:service-name (:Name parsed)}}
                                    attributes))]
     ;; Make sure the concept was saved successfully
     (is (= 201 (:status service)))
     (select-keys service [:concept-id :revision-id]))))

(defn save-tool
  "Saves a tool concept"
  ([n]
   (save-tool n {}))
  ([n attributes]
   (let [tool-concept (tool-util/make-tool-concept {} attributes n)
         parsed (json/parse-string (:metadata tool-concept) true)
         tool (mdb/save-concept (merge
                                 tool-concept
                                 {:extra-fields {:tool-name (:Name parsed)}}
                                 attributes))]
     ;; Make sure the concept was saved successfully
     (is (= 201 (:status tool)))
     (select-keys tool [:concept-id :revision-id]))))

(defn save-subscription
  "Saves a subscription concept"
  ([n]
   (save-subscription n {}))
  ([n attributes]
   (let [sub-concept (sub-util/make-subscription-concept {} attributes n)
         parsed (json/parse-string (:metadata sub-concept) true)
         subscription (mdb/save-concept (merge
                                         sub-concept
                                         {:extra-fields {:subscription-name (:Name parsed)
                                                         :subscription-type (:Type parsed)
                                                         :collection-concept-id (:CollectionConceptId parsed)
                                                         :subscriber-id (:SubscriberId parsed)
                                                         :normalized-query (:Query parsed)
                                                         :email-address "dummy-email@gmail.com"}}
                                         attributes))]
     ;; Make sure the concept was saved successfully
     (is (= 201 (:status subscription)))
     (select-keys subscription [:concept-id :revision-id]))))

(defn delete-concept
  "Creates a tombstone for the concept in metadata-db."
  [concept]
  (let [tombstone (update-in concept [:revision-id] inc)]
    (is (= 201 (:status (mdb/tombstone-concept tombstone))))))
