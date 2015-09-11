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

(defn create-tag
  "Creates a tag."
  [token tag]
  (let [{:keys [status body]} (tt/create-tag (s/context) tag {:is-raw? true :token token})]
    (if (map? body)
      (assoc body :status status)
      {:status status
       :body body})))

(defn update-tag
  "Updates a tag."
  [token concept-id tag]
  (let [{:keys [status body]} (tt/update-tag (s/context) concept-id tag {:is-raw? true :token token})]
    (if (map? body)
      (assoc body :status status)
      {:status status
       :body body})))

(defn assert-tag-saved
  "Checks that a tag was persisted correctly in metadata db. The tag should already have originator
  id set correctly. The user-id indicates which user updated this revision."
  [tag user-id concept-id revision-id]
  (let [concept (mdb/get-concept concept-id revision-id)]
    (is (= {:concept-type :tag
            :native-id (str (:namespace tag) (char 29) (:value tag))
            ;; TODO Get James or change it yourself that provider id shouldn't be returned if we don't send it in
            :provider-id "CMR"
            :format mt/edn
            :metadata (pr-str tag)
            :user-id user-id
            :deleted false
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date)))))
