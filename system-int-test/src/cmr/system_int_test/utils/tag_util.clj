(ns cmr.system-int-test.utils.tag-util
  "This contains utilities for testing tagging"
  (:require [cmr.transmit.tag :as tt]
            [clojure.test :refer [is]]
            [clojure.string :as str]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.common.mime-types :as mt]
            [cmr.common.util :as util]))

(defn grant-all-tag-fixture
  "A test fixture that grants all users the ability to create and modify tags"
  [f]
  (e/grant-all-tag (s/context))
  (f))

(defn make-tag
  "Makes a valid tag"
  ([]
   (make-tag nil))
  ([attributes]
   (merge {:tag-key "tag-key"
           :description "A very good tag"}
          attributes)))

(defn string-of-length
  "Creates a string of the specified length"
  [n]
  (str/join (repeat n "x")))

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
   (let [options (merge {:raw? true :token token} options)]
     (process-response (tt/create-tag (s/context) tag options)))))

(defn get-tag
  "Retrieves a tag by tag-key"
  [tag-key]
  (process-response (tt/get-tag (s/context) tag-key {:raw? true})))

(defn update-tag
  "Updates a tag."
  ([token tag]
   (update-tag token (:tag-key tag) tag nil))
  ([token tag-key tag]
   (update-tag token tag-key tag nil))
  ([token tag-key tag options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (tt/update-tag (s/context) tag-key tag options)))))


(defn delete-tag
  "Deletes a tag"
  ([token tag-key]
   (delete-tag token tag-key nil))
  ([token tag-key options]
   (let [options (merge {:raw? true :token token} options)]
     (process-response (tt/delete-tag (s/context) tag-key options)))))

(defn- associate-tag
  "Associate a tag with collections by the JSON condition.
  Valid association types are :query and :concept-ids."
  [association-type token tag-key condition options]
  (let [options (merge {:raw? true :token token} options)
        response (tt/associate-tag association-type (s/context) tag-key condition options)]
    (index/wait-until-indexed)
    (process-response response)))

(defn associate-by-query
  "Associates a tag with collections found with a JSON query"
  ([token tag-key condition]
   (associate-by-query token tag-key condition nil))
  ([token tag-key condition options]
   (associate-tag :query token tag-key {:condition condition} options)))

(defn associate-by-concept-ids
  "Associates a tag with collections by collection concept ids"
  ([token tag-key coll-concept-ids]
   (associate-by-concept-ids token tag-key coll-concept-ids nil))
  ([token tag-key coll-concept-ids options]
   (associate-tag :concept-ids token tag-key coll-concept-ids options)))

(defn- disassociate-tag
  "Disassociates a tag with collections found with a JSON query"
  [association-type token tag-key condition options]
  (let [options (merge {:raw? true :token token} options)
        response (tt/disassociate-tag association-type (s/context) tag-key condition options)]
    (index/wait-until-indexed)
    (process-response response)))

(defn disassociate-by-query
  "Disassociates a tag with collections found with a JSON query"
  ([token tag-key condition]
   (disassociate-by-query token tag-key condition nil))
  ([token tag-key condition options]
   (disassociate-tag :query token tag-key {:condition condition} options)))

(defn disassociate-by-concept-ids
  "Disassociates a tag with collections by collection concept ids"
  ([token tag-key coll-concept-ids]
   (disassociate-by-concept-ids token tag-key coll-concept-ids nil))
  ([token tag-key coll-concept-ids options]
   (disassociate-tag :concept-ids token tag-key coll-concept-ids options)))

(defn save-tag
  "A helper function for creating or updating tags for search tests. If the tag does not have a
  :concept-id it saves it. If the tag has a :concept-id it updates the tag. Returns the saved tag
  along with :concept-id, :revision-id, :errors, and :status"
  ([token tag]
   (let [tag-to-save (select-keys tag [:tag-key :description])
         response (if-let [concept-id (:concept-id tag)]
                    (update-tag token (:tag-key tag) tag-to-save)
                    (create-tag token tag-to-save))
         tag (-> tag
                 (update :tag-key str/lower-case)
                 (into (select-keys response [:status :errors :concept-id :revision-id])))]

     (if (= (:revision-id tag) 1)
       ;; Get the originator id for the tag
       (assoc tag :originator-id (tokens/get-user-id (s/context) token))
       tag)))
  ([token tag associated-collections]
   (let [saved-tag (save-tag token tag)
         ;; Associate the tag with the collections using a query by concept id
         condition {:or (map #(hash-map :concept_id (:concept-id %)) associated-collections)}
         response (associate-by-query token (:tag-key saved-tag) condition)]
     (assert (= 200 (:status response)) (pr-str condition))
     (assoc saved-tag :revision-id (:revision-id response)))))

(defn search
  "Searches for tags using the given parameters"
  [params]
  (process-response (tt/search-for-tags (s/context) params {:raw? true})))

(defn assert-tag-saved
  "Checks that a tag was persisted correctly in metadata db. The tag should already have originator
  id set correctly. The user-id indicates which user updated this revision."
  [tag user-id concept-id revision-id]
  (let [concept (mdb/get-concept concept-id revision-id)
        ;; make sure a tag has associated collection ids for comparison in metadata db
        tag (update-in tag [:associated-concept-ids] #(or % #{}))]
    (is (= {:concept-type :tag
            :native-id (:tag-key tag)
            :provider-id "CMR"
            :format mt/edn
            :metadata (pr-str tag)
            :user-id user-id
            :deleted false
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date :transaction-id)))))

(defn assert-tag-deleted
  "Checks that a tag tombstone was persisted correctly in metadata db."
  [tag user-id concept-id revision-id]
  (let [concept (mdb/get-concept concept-id revision-id)]
    (is (= {:concept-type :tag
            :native-id (:tag-key tag)
            :provider-id "CMR"
            :metadata ""
            :format mt/edn
            :user-id user-id
            :deleted true
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date :transaction-id)))))

(defn sort-expected-tags
  "Sorts the tags using the expected default sort key."
  [tags]
  (sort-by identity
           (fn [t1 t2]
             (compare (:tag-key t1) (:tag-key t2)))
           tags))

(def tag-keys-in-expected-response
  [:concept-id :revision-id :tag-key :description :originator-id])

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

(defn- coll-tag-association->expected-tag-association
  "Returns the expected tag association for the given collection concept id to tag association
  mapping, which is in the format of, e.g.
  {[C1200000000-CMR 1] {:concept-id \"TA1200000005-CMR\" :revision-id 1}}."
  [coll-tag-association error?]
  (let [[[coll-concept-id coll-revision-id] tag-association] coll-tag-association
        {:keys [concept-id revision-id]} tag-association
        tagged-item (if coll-revision-id
                      {:concept_id coll-concept-id :revision_id coll-revision-id}
                      {:concept_id coll-concept-id})
        errors (select-keys tag-association [:errors :warnings])]
    (if (seq errors)
      (merge {:tagged_item tagged-item} errors)
      {:tag_association {:concept_id concept-id :revision_id revision-id}
       :tagged_item tagged-item})))

(defn- comparable-tag-associations
  "Returns the tag associations with the concept_id removed from the tag_association field.
  We do this to make comparision of created tag associations possible, as we can't assure
  the order of which the tag assocaitions are created."
  [tag-associations]
  (let [fix-ta-fn (fn [ta]
                    (if (:tag_association ta)
                      (update ta :tag_association dissoc :concept_id)
                      ta))]
    (map fix-ta-fn tag-associations)))

(defn assert-tag-association-response-ok?
  "Assert the tag association response when status code is 200 is correct."
  ([coll-tag-associations response]
   (assert-tag-association-response-ok? coll-tag-associations response true))
  ([coll-tag-associations response error?]
   (let [{:keys [status body errors]} response
         expected-tas (map #(coll-tag-association->expected-tag-association % error?)
                           coll-tag-associations)]
     (is (= [200
             (set (comparable-tag-associations expected-tas))]
            [status (set (comparable-tag-associations body))])))))

(defn assert-tag-disassociation-response-ok?
  "Assert the tag association response when status code is 200 is correct."
  [coll-tag-associations response]
  (assert-tag-association-response-ok? coll-tag-associations response false))

(defn assert-invalid-data-error
  "Assert tag association response when status code is 422 is correct"
  [expected-errors response]
  (let [{:keys [status body errors]} response]
    (is (= [422 (set expected-errors)]
           [status (set errors)]))))

(defn assert-tag-associated-with-query
  "Assert the collections found by the tag query matches the given collection revisions"
  [token query expected-colls]
  (let [refs (search/find-refs :collection
                               (util/remove-nil-keys (assoc query :token token :page_size 30))
                               {:all-revisions true})]
    (is (nil? (:errors refs)))
    (is (d/refs-match? expected-colls refs))))

