(ns cmr.system-int-test.utils.tag-util
  "This contains utilities for testing tagging"
  (:require [cmr.transmit.tag :as tt]
            [clojure.test :refer [is]]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.common.mime-types :as mt]))

(defn make-tag
  "Makes a valid unique tag"
  [n]
  {:namespace "org.nasa.something"
   :category "QA"
   :value (str "value" n)
   :description "A very good tag"})

(defn- process-response
  [{:keys [status body]}]
  (if (map? body)
    (assoc body :status status)
    {:status status
     :body body}))

(defn create-tag
  "Creates a tag."
  ([token tag]
   (create-tag token tag nil))
  ([token tag options]
   (let [options (merge {:is-raw? true :token token} options)]
     (process-response (tt/create-tag (s/context) tag options)))))

(defn get-tag
  "Retrieves a tag by concept id"
  [concept-id]
  (tt/get-tag (s/context) concept-id {:is-raw? true}))

(defn update-tag
  "Updates a tag."
  ([token concept-id tag]
   (update-tag token concept-id tag nil))
  ([token concept-id tag options]
   (let [options (merge {:is-raw? true :token token} options)]
     (process-response (tt/update-tag (s/context) concept-id tag options)))))

(defn delete-tag
  "Deletes a tag"
  ([token concept-id]
   (delete-tag token concept-id nil))
  ([token concept-id options]
   (let [options (merge {:is-raw? true :token token} options)]
     (process-response (tt/delete-tag (s/context) concept-id options)))))

(defn associate
  "Associates a tag with collections found with a JSON query"
  ([token concept-id condition]
   (associate token concept-id condition nil))
  ([token concept-id condition options]
   (let [options (merge {:is-raw? true :token token} options)]
     (process-response (tt/associate-tag (s/context) concept-id {:condition condition} options)))))

(defn disassociate
  "Disassociates a tag with collections found with a JSON query"
  ([token concept-id condition]
   (disassociate token concept-id condition nil))
  ([token concept-id condition options]
   (let [options (merge {:is-raw? true :token token} options)]
     (process-response (tt/disassociate-tag (s/context) concept-id {:condition condition} options)))))

(defn assert-tag-saved
  "Checks that a tag was persisted correctly in metadata db. The tag should already have originator
  id set correctly. The user-id indicates which user updated this revision."
  [tag user-id concept-id revision-id]
  (let [concept (mdb/get-concept concept-id revision-id)
        ;; make sure a tag has associated collection ids for comparison in metadata db
        tag (update-in tag [:associated-collection-ids] #(or % #{}))]
    (is (= {:concept-type :tag
            :native-id (str (:namespace tag) (char 29) (:value tag))
            :provider-id "CMR"
            :format mt/edn
            :metadata (pr-str tag)
            :user-id user-id
            :deleted false
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date)))))

(defn assert-tag-deleted
  "Checks that a tag tombstone was persisted correctly in metadata db."
  [tag user-id concept-id revision-id]
  (let [concept (mdb/get-concept concept-id revision-id)]
    (is (= {:concept-type :tag
            :native-id (str (:namespace tag) (char 29) (:value tag))
            :provider-id "CMR"
            :metadata ""
            :format mt/edn
            :user-id user-id
            :deleted true
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date)))))

