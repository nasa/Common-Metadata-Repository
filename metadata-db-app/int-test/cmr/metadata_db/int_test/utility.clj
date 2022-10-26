(ns cmr.metadata-db.int-test.utility
  "Contains various utility methods to support integration tests."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clj-http.conn-mgr :as conn-mgr]
   [clj-time.format :as f]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.util :as util]
   [cmr.common.validations.core :as validations]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.metadata-db.services.concept-service :as concept-service]
   [cmr.transmit.config :as transmit-config]
   [inflections.core :as inf]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Constants and utility functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def conn-mgr-atom (atom nil))

(defn conn-mgr
  "Returns the HTTP connection manager to use. This allows integration tests to use persistent
  HTTP connections"
  []
  (when-not @conn-mgr-atom
    (reset! conn-mgr-atom  (conn-mgr/make-reusable-conn-manager {}))))

(defn concepts-url
  []
  (str "http://localhost:" (transmit-config/metadata-db-port) "/concepts/"))

(defn concept-id-url
  []
  (str "http://localhost:" (transmit-config/metadata-db-port) "/concept-id/"))

(defn reset-url
  []
  (str "http://localhost:" (transmit-config/metadata-db-port) "/reset"))

(defn old-revision-concept-cleanup-url
  []
  (str "http://localhost:" (transmit-config/metadata-db-port) "/jobs/old-revision-concept-cleanup"))

(defn expired-concept-cleanup-url
  []
  (str "http://localhost:" (transmit-config/metadata-db-port) "/jobs/expired-concept-cleanup"))

(defn providers-url
  []
  (str "http://localhost:" (transmit-config/metadata-db-port) "/providers"))

(defn old-revision-concept-cleanup
  "Runs the old revision concept cleanup job"
  []
  (:status
    (client/post (old-revision-concept-cleanup-url)
                 {:throw-exceptions false
                  :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                  :connection-manager (conn-mgr)})))

(defn expired-concept-cleanup
  "Runs the expired concept cleanup job"
  []
  (:status
    (client/post (expired-concept-cleanup-url)
                 {:throw-exceptions false
                  :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                  :connection-manager (conn-mgr)})))

(defn reset-database
  "Make a request to reset the database by clearing out all stored concepts."
  []
  (:status
   (client/post (reset-url)
                {:throw-exceptions false
                 :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                 :connection-manager (conn-mgr)})))

(defn created-at-same?
  "Returns true if the `created-at` for the given concept revisions are the same
  and none of them are nil"
  [& concepts]
  (let [created-ats (map :created-at concepts)]
    (and (apply = created-ats)
         (not-any? nil? created-ats))))

(defn assert-no-errors
  [save-result]
  (is (nil? (:errors save-result)))
  save-result)

(defn- parse-concept
  "Parses a concept from a JSON response"
  [response]
  (-> response
      :body
      (json/decode true)
      (update-in [:revision-date] (partial f/parse (f/formatters :date-time)))
      (update-in [:concept-type] keyword)))

(defn- parse-errors
  "Parses an error response from a JSON response"
  [response]
  (-> response
      :body
      (json/decode true)))

(defn- parse-concepts
  "Parses multiple concept from a JSON response"
  [response]
  (map #(-> %
            (update-in [:revision-date] (partial f/parse (f/formatters :date-time)))
            (update-in [:concept-type] keyword))
       (json/decode (:body response) true)))

(defn get-concept-id
  "Make a GET to retrieve the id for a given concept-type, provider-id, and native-id."
  [concept-type provider-id native-id]
  (let [response (client/get (str (concept-id-url) (name concept-type) "/" provider-id "/" native-id)
                             {:accept :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)})
        status (:status response)
        body (json/decode (:body response) true)
        {:keys [concept-id errors]} body]
    {:status status :concept-id concept-id :errors errors}))

(defn get-concept-by-id-and-revision
  "Make a GET to retrieve a concept by concept-id and revision."
  [concept-id revision-id]
  (let [response (client/get (str (concepts-url) concept-id "/" revision-id)
                             {:accept :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)})
        status (:status response)]
    (if (= status 200)
      {:status status :concept (parse-concept response)}
      {:status status})))

(defn get-concept-by-id
  "Make a GET to retrieve a concept by concept-id."
  [concept-id]
  (let [response (client/get (str (concepts-url) concept-id)
                             {:accept :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)})
        status (:status response)]
    (if (= status 200)
      {:status status :concept (parse-concept response)}
      {:status status})))

(defn get-concepts
  "Make a POST to retrieve concepts by concept-id and revision."
  ([tuples]
   (get-concepts tuples nil))
  ([tuples allow-missing?]
   (let [query-params (if (nil? allow-missing?)
                        {}
                        {:allow_missing allow-missing?})
         path "search/concept-revisions"
         response (client/post (str (concepts-url) path)
                               {:query-params query-params
                                :body (json/generate-string tuples)
                                :content-type :json
                                :accept :json
                                :throw-exceptions false
                                :connection-manager (conn-mgr)})
         status (:status response)]
     (if (= status 200)
       {:status status
        :concepts (parse-concepts response)}
       (assoc (parse-errors response) :status status)))))

(defn get-latest-concepts
  "Make a POST to retreive the latest revision of concpets by concept-id."
  ([concept-ids]
   (get-latest-concepts concept-ids nil))
  ([concept-ids allow-missing?]
   (let [query-params (if (nil? allow-missing?)
                        {}
                        {:allow_missing allow-missing?})
         path "search/latest-concept-revisions"
         response (client/post (str (concepts-url) path)
                               {:query-params query-params
                                :body (json/generate-string concept-ids)
                                :content-type :json
                                :accept :json
                                :throw-exceptions false
                                :connection-manager (conn-mgr)})
         status (:status response)]
     (if (= status 200)
       {:status status
        :concepts (parse-concepts response)}
       (assoc (parse-errors response) :status status)))))

(defn find-concepts
  "Make a get to retrieve concepts by parameters for a specific concept type"
  ([concept-type]
   (find-concepts concept-type {}))
  ([concept-type params]
   (let [response (client/get (str (concepts-url) "search/" (inf/plural (name concept-type)))
                              {:query-params params
                               :accept :json
                               :throw-exceptions false
                               :connection-manager (conn-mgr)})
         status (:status response)]
     (if (= status 200)
       {:status status
        :concepts (parse-concepts response)}
       (assoc (parse-errors response) :status status)))))

(defn find-latest-concepts
  "Make a get to retrieve the latest revision of concepts by parameters for a specific concept type"
  [concept-type params]
  (find-concepts concept-type (assoc params :latest true)))

(defn get-expired-collection-concept-ids
  "Make a get to retrieve expired collection concept ids."
  [provider-id]
  (let [response (client/get (str (concepts-url) "search/expired-collections")
                             {:query-params (when provider-id {:provider provider-id})
                              :accept :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)})
        status (:status response)]
    (if (= status 200)
      {:status status
       :concept-ids (json/decode (:body response) true)}
      (assoc (parse-errors response) :status status))))

(defn- save-concept-core
  "Fundamental save operation"
  ([concept]
   (save-concept-core concept nil))
  ([concept headers]
   (let [response (client/post (concepts-url)
                               {:body (json/generate-string concept)
                                :content-type :json
                                :accept :json
                                :headers headers
                                :throw-exceptions false
                                :connection-manager (conn-mgr)})
         status (:status response)
         body (json/decode (:body response) true)
         {:keys [revision-id concept-id errors variable-association]} body]
     (if (= :variable (:concept-type concept))
       {:status status
        :revision-id revision-id
        :concept-id concept-id
        :errors errors
        :variable-association variable-association}
       {:status status
        :revision-id revision-id
        :concept-id concept-id
        :errors errors}))))

(defn save-concept
  "Make a POST request to save a concept with JSON encoding of the concept.

  Returns a map with status, revision-id, transaction-id, and a list of error
  messages."
  ([concept]
   (save-concept concept 1))
  ([concept num-revisions]
   (save-concept concept num-revisions nil))
  ([concept num-revisions header]
   (let [concept (update-in concept [:revision-date]
                            ;; Convert date times to string but allow invalid
                            ;; strings to be passed through
                            #(when % (str %)))]
     (dotimes [n (dec num-revisions)]
       (assert-no-errors (save-concept-core concept header)))
     (save-concept-core concept header))))

(defn delete-concept
  "Make a DELETE request to mark a concept as deleted. Returns the status and revision id of the
  tombstone."
  ([concept-id]
   (delete-concept concept-id nil nil))
  ([concept-id revision-id]
   (delete-concept concept-id revision-id nil))
  ([concept-id revision-id revision-date]
   (let [url (if revision-id
               (format "%s%s/%s" (concepts-url) concept-id revision-id)
               (format "%s%s" (concepts-url) concept-id))
         query-params (when revision-date
                        {:revision-date (str revision-date)})
         response (client/delete url
                                 {:throw-exceptions false
                                  :query-params query-params
                                  :connection-manager (conn-mgr)})
         status (:status response)
         body (json/decode (:body response) true)
         {:keys [revision-id errors]} body]
     {:status status :revision-id revision-id :errors errors})))

(defn force-delete-concept
  "Make a DELETE request to permanently remove a revison of a concept."
  [concept-id revision-id]
  (let [url (format "%sforce-delete/%s/%s" (concepts-url) concept-id revision-id)
        response (client/delete url
                                {:throw-exceptions false
                                 :connection-manager (conn-mgr)})
        status (:status response)
        body (json/decode (:body response) true)
        {:keys [revision-id errors]} body]
    {:status status :revision-id revision-id :errors errors}))

(defmulti expected-concept
  "Modifies a concept for comparison with a retrieved concept."
  (fn [concept]
    (let [{:keys [concept-type]} concept]
      (if (contains? concept-service/system-level-concept-types concept-type)
        ;; system level concept
        :system-level-concept
        concept-type))))

(defmethod expected-concept :granule
  [concept]
  ;; :parent-entry-title is saved but not retrieved
  (if (:extra-fields concept)
    (update-in concept [:extra-fields] dissoc :parent-entry-title)
    concept))

(defmethod expected-concept :access-group
  [concept]
  (if (:provider-id concept)
    concept
    (assoc concept :provider-id "CMR")))

(defmethod expected-concept :system-level-concept
  [concept]
  (assoc concept :provider-id "CMR"))

(defmethod expected-concept :default
  [concept]
  concept)

(defn verify-concept-was-saved
  "Check to make sure a concept is stored in the database."
  [concept]
  (let [{:keys [concept-id revision-id]} concept
        stored-concept (:concept (get-concept-by-id-and-revision concept-id revision-id))]
    (if (= :variable (:concept-type concept))
      ;; We added :coll-concept-id in the attribute in the tests, it'll appear in both concept and
      ;; stored-concept in memory db, but not in the stored-concept in real db. This is the case
      ;; for all the concept types when you add new attributes in the test. So, we want to remove
      ;; :coll-concept-id from both.
      (is (= (dissoc (expected-concept concept) :coll-concept-id)
             (dissoc stored-concept :revision-date :transaction-id :created-at :coll-concept-id)))
      (is (= (expected-concept concept)
             (dissoc stored-concept :revision-date :transaction-id :created-at))))))

(defn is-tag-association-deleted?
  "Returns if the ta is marked as deleted in metadata-db"
  [tag-association deleted?]
  (let [{:keys [status concept]} (get-concept-by-id (:concept-id tag-association))]
    (is (= 200 status))
    (is (= deleted? (:deleted concept)))))

(defn concepts-for-comparison
  "Removes irrelevant fields from concepts so they can be compared in search tests."
  [concepts]
  (map #(dissoc % :revision-date :transaction-id :created-at) concepts))

(defn get-revisions
  "This is a utility function that returns revisions of interest (given the
  respective revision ids).

  The results of this function are intended to be used with `(apply ...)`."
  [concept-id initial-revision-id second-revision-id tombstone-revision-id
   final-revision-id]
  (mapv #(:concept (get-concept-by-id-and-revision concept-id %))
        [initial-revision-id second-revision-id tombstone-revision-id
         final-revision-id]))

(defn concept-created-at-assertions
  "This function is used in tests to do the following:
    1) Save a service
    2) Then wait for a small period of time before saving it again
    3) Then wait again and save a tombstone.
    4) Finally, wait a bit and save a new (non-tombstone) revision.

  All should have the same `created-at` value."
  [test-type initial-concept]
  (testing (format "Save %s multiple times gets same created-at" test-type)
    (let [{concept-id :concept-id
           initial-revision-id :revision-id} (save-concept initial-concept)
          ;; Note - Originally planned to use the time-keeper functionality
          ;; for this, but metadata-db tests don't have access to the control
          ;; api that would allow this to work in CI.
          _ (Thread/sleep 100)
          {second-revision-id :revision-id} (save-concept initial-concept)
          _ (Thread/sleep 100)
          {tombstone-revision-id :revision-id} (delete-concept concept-id)
          _ (Thread/sleep 100)
          {final-revision-id :revision-id} (save-concept initial-concept)
          revisions (get-revisions concept-id
                                   initial-revision-id
                                   second-revision-id
                                   tombstone-revision-id
                                   final-revision-id)]
      (is (apply created-at-same? revisions)))))

(defn concept-with-conflicting-native-id-assertions
  "For use in tests that need to check the for conflicting concept ids."
  [test-type field-type concept1 different-native-id]
  (testing (str "Save a " test-type)
    (let [{:keys [status revision-id concept-id]} (save-concept concept1)]
      (is (= status 201))
      (is (= 1 revision-id))
      (testing "and another with all the same data"
        (let [concept2 concept1
              {:keys [status revision-id]} (save-concept concept2)]
          (is (= 201 status))
          (is (= 2 revision-id))))
      (testing "and another with same data but different provider"
        (let [concept3 (assoc concept1 :provider-id "PROV2")
              {:keys [status revision-id]} (save-concept concept3)]
          (is (= status 201))
          (is (= 1 revision-id))))
      (testing "and another the same data but with a different native-id"
        (let [concept4 (assoc concept1 :native-id different-native-id)
              response (save-concept concept4)
              humanized-field (validations/humanize-field field-type)
              ;; after the saving of concept2, the revision id was 2; the
              ;; saving of concept3 was for a different provider, so it
              ;; has a revision-id of 1; if the call above with concept4
              ;; passed the constaint checks (which it shouldn't) the
              ;; revision-id would now be 3 (but it shouldn't be)
              failed-revision-id 3
              find-response (get-concept-by-id-and-revision
                             concept-id failed-revision-id)]
          (is (= nil (:revision-id response)))
          (is (= 409 (:status response)))
          (is (= 404 (:status find-response)))
          (is (= [(format (str "The Provider Id [%s] and %s [%s] "
                               "combination must be unique for a given native-id. "
                               "The following concept with the same "
                               "Provider Id and %s was found: [%s].")
                          "PROV1"
                          humanized-field
                          (get-in concept1 [:extra-fields field-type])
                          humanized-field
                          concept-id)]
                 (:errors response))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Providers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn save-provider
  "Make a POST request to save a provider with JSON encoding of the provider. Returns a map with
  status and a list of error messages."
  [params]
  (let [response (client/post (providers-url)
                              {:body (json/generate-string
                                       (util/remove-nil-keys params))
                               :content-type :json
                               :accept :json
                               :throw-exceptions false
                               :connection-manager (conn-mgr)
                               :headers {transmit-config/token-header (transmit-config/echo-system-token)}})
        status (:status response)
        {:keys [errors provider-id]} (json/decode (:body response) true)]
    {:status status :errors errors :provider-id provider-id}))

(defn get-providers
  "Make a GET request to retrieve the list of providers."
  []
  (let [response (client/get (providers-url)
                             {:accept :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)})
        status (:status response)
        body (json/decode (:body response) true)]
    {:status status
     :errors (:errors body)
     :providers (when (= status 200) body)}))

(defn update-provider
  "Updates the provider with the given parameters, which is a map of key and value for
  provider-id, short-name, cmr-only and small fields of the provider."
  [params]
  (let [response (client/put (format "%s/%s" (providers-url) (:provider-id params))
                             {:body (json/generate-string params)
                              :content-type :json
                              :accept :json
                              :as :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)
                              :headers {transmit-config/token-header (transmit-config/echo-system-token)}})
        {:keys [status body]} response
        {:keys [errors]} (when (not= 200 status)
                           (json/decode (:body response) true))]
    {:status status :errors errors}))

(defn delete-provider
  "Make a DELETE request to remove a provider."
  [provider-id]
  (let [response (client/delete (format "%s/%s" (providers-url) provider-id)
                                {:accept :json
                                 :throw-exceptions false
                                 :connection-manager (conn-mgr)
                                 :headers {transmit-config/token-header (transmit-config/echo-system-token)}})
        status (:status response)
        {:keys [errors]} (json/decode (:body response) true)]
    {:status status :errors errors}))


(defn verify-provider-was-saved
  "Verify that the given provider-map is in the list of providers."
  [provider-map]
  (some #{(merge {:short-name (:provider-id provider-map)
                  :cmr-only false
                  :small false}
                 (util/remove-nil-keys provider-map))}
        (:providers (get-providers))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reset-database-fixture
  "Creates a database fixture function to reset the database after every test.
  Optionally accepts a list of provider-ids to create before the test"
  [& providers]
  (fn [f]
    (try
      ;; We set this to false during a test so that messages won't be published
      ;; when this is run in dev system and cause exceptions in the indexer.
      (side/eval-form `(mdb-config/set-publish-messages! false))
      (reset-database)
      (doseq [provider providers]
        (let [{:keys [provider-id short-name cmr-only small]} provider
              short-name (if short-name short-name provider-id)]
          (save-provider {:provider-id provider-id
                          :short-name short-name
                          :cmr-only (if cmr-only true false)
                          :small (if small true false)})))
      (f)
      (finally
        (side/eval-form `(mdb-config/set-publish-messages! true))))))
