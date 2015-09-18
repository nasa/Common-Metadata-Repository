(ns cmr.system-int-test.utils.tag-util
  "This contains utilities for testing tagging"
  (:require [cmr.transmit.tag :as tt]
            [clojure.test :refer [is]]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.common.mime-types :as mt]))

(defn make-tag
  "Makes a valid tag"
  ([]
   (make-tag nil))
  ([attributes]
   (merge {:namespace "org.nasa.something"
           :category "QA"
           :value "value"
           :description "A very good tag"}
          attributes)))

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
  (process-response (tt/get-tag (s/context) concept-id {:is-raw? true})))

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

(defn associate-by-query
  "Associates a tag with collections found with a JSON query"
  ([token concept-id condition]
   (associate-by-query token concept-id condition nil))
  ([token concept-id condition options]
   (let [options (merge {:is-raw? true :token token} options)]
     (process-response (tt/associate-tag-by-query (s/context) concept-id {:condition condition} options)))))

(defn disassociate-by-query
  "Disassociates a tag with collections found with a JSON query"
  ([token concept-id condition]
   (disassociate-by-query token concept-id condition nil))
  ([token concept-id condition options]
   (let [options (merge {:is-raw? true :token token} options)]
     (process-response (tt/disassociate-tag-by-query (s/context) concept-id {:condition condition} options)))))

(defn save-tag
  "A helper function for creating or updating tags for search tests. If the tag does not have a
  :concept-id it saves it. If the tag has a :concept-id it updates the tag. Returns the saved tag
  along with :concept-id, :revision-id, :errors, and :status"
  ([token tag]
   (let [tag-to-save (select-keys tag [:namespace :category :value :description])
         response (if-let [concept-id (:concept-id tag)]
                    (update-tag token concept-id tag-to-save)
                    (create-tag token tag-to-save))
         tag (into tag (select-keys response [:status :errors :concept-id :revision-id]))]

     (if (= (:revision-id tag) 1)
       ;; Get the originator id for the tag
       (assoc tag :originator-id (tokens/get-user-id (s/context) token))
       tag)))
  ([token tag associated-collections]
   (let [saved-tag (save-tag token tag)
         ;; Associate the tag with the collections using a query by concept id
         condition {:or (map #(hash-map :concept_id (:concept-id %)) associated-collections)}
         response (associate-by-query token (:concept-id saved-tag) condition)]
     (assert (= 200 (:status response)) (pr-str condition))
     (assoc saved-tag :revision-id (:revision-id response)))))

(defn search
  "Searches for tags using the given parameters"
  [params]
  (process-response (tt/search-for-tags (s/context) params {:is-raw? true})))

(defn assert-tag-saved
  "Checks that a tag was persisted correctly in metadata db. The tag should already have originator
  id set correctly. The user-id indicates which user updated this revision."
  [tag user-id concept-id revision-id]
  (let [concept (mdb/get-concept concept-id revision-id)
        ;; make sure a tag has associated collection ids for comparison in metadata db
        tag (update-in tag [:associated-concept-ids] #(or % #{}))]
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

(defn sort-expected-tags
  "Sorts the tags using the expected default sort key."
  [tags]
  (sort-by identity
           (fn [t1 t2]
             (let [tns (:namespace t1)
                   tns2 (:namespace t2)
                   v1 (:value t1)
                   v2 (:value t2)]
               (cond
                 (not= tns tns2) (compare tns tns2)
                 :else (compare v1 v2))))
           tags))

(def tag-keys-in-expected-response
  [:concept-id :revision-id :namespace :value :description :category :originator-id])

(defn assert-tag-search
  "Verifies the tag search results"
  ([tags response]
   (assert-tag-search nil tags response))
  ([expected-hits tags response]
   (let [expected-items (->> tags
                             sort-expected-tags
                             (map #(select-keys % tag-keys-in-expected-response)))
         expected-response {:status 200
                            :hits (or expected-hits (:hits response))
                            :items expected-items}]
     (is (:took response))
     (is (= expected-response (dissoc response :took))))))

