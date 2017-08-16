(ns cmr.system-int-test.bootstrap.bulk-index.core
  "CMR bulk indexing integration common functions."
  (:require
   [clojure.test :refer [is]]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
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
  ([n]
   (save-collection n {}))
  ([n attributes]
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
                                  :provider-id "PROV1"
                                  :native-id unique-str
                                  :short-name unique-str}
                                 attributes))]
     ;; Make sure the concept was saved successfully
     (is (= 201 (:status coll)))
     (merge umm (select-keys coll [:concept-id :revision-id])))))

(defn save-granule
  "Saves a granule concept"
  ([n collection]
   (save-granule n collection {}))
  ([n collection attributes]
   (let [unique-str (str "gran" n)
         umm (dg/granule collection {:granule-ur unique-str})
         xml (echo10/umm->echo10-xml umm)
         gran (mdb/save-concept (merge
                                 {:concept-type :granule
                                  :provider-id "PROV1"
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

(defn delete-concept
  "Creates a tombstone for the concept in metadata-db."
  [concept]
  (let [tombstone (update-in concept [:revision-id] inc)]
    (is (= 201 (:status (mdb/tombstone-concept tombstone))))))
