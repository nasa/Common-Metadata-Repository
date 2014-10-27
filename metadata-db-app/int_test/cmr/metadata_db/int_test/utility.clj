(ns cmr.metadata-db.int-test.utility
  "Contains various utility methods to support integration tests."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            [clj-time.coerce :as cr]
            [inflections.core :as inf]
            [cmr.metadata-db.config :as config]
            [clj-http.conn-mgr :as conn-mgr]
            [cmr.acl.core :as acl]
            [cmr.transmit.config :as transmit-config]))

(def conn-mgr-atom (atom nil))

(defn conn-mgr
  "Returns the HTTP connection manager to use. This allows integration tests to use persistent
  HTTP connections"
  []
  (when-not @conn-mgr-atom
    (reset! conn-mgr-atom  (conn-mgr/make-reusable-conn-manager {}))))

(def concepts-url (str "http://localhost:" (config/app-port) "/concepts/"))

(def concept-id-url (str "http://localhost:" (config/app-port) "/concept-id/"))

(def reset-url (str "http://localhost:" (config/app-port) "/reset"))

(def old-revision-concept-cleanup-url
  (str "http://localhost:" (config/app-port) "/jobs/old-revision-concept-cleanup"))

(def expired-concept-cleanup-url
  (str "http://localhost:" (config/app-port) "/jobs/expired-concept-cleanup"))

(def providers-url (str "http://localhost:" (config/app-port) "/providers"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; utility methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; concepts
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn collection-concept
  "Creates a collection concept"
  ([provider-id uniq-num]
   (collection-concept provider-id uniq-num {}))
  ([provider-id uniq-num extra-fields]
   {:concept-type :collection
    :native-id (str "native-id " uniq-num)
    :provider-id provider-id
    :metadata (str "xml here " uniq-num)
    :format "application/echo10+xml"
    :deleted false
    :extra-fields (merge {:short-name (str "short" uniq-num)
                          :version-id (str "V" uniq-num)
                          :entry-title (str "dataset" uniq-num)
                          :delete-time nil}
                         extra-fields)}))

(defn granule-concept
  "Creates a granule concept"
  [provider-id parent-collection-id uniq-num & concept-id]
  (let [granule {:concept-type :granule
                 :native-id (str "native-id " uniq-num)
                 :provider-id provider-id
                 :metadata (str "xml here " uniq-num)
                 :format "application/echo10+xml"
                 :deleted false
                 :extra-fields {:parent-collection-id parent-collection-id
                                :delete-time nil}}]
    (if concept-id
      (assoc granule :concept-id (first concept-id))
      granule)))


(defn- parse-concept
  "Parses a concept from a JSON response"
  [response]
  (-> response
      :body
      (cheshire/decode true)
      (update-in [:revision-date] (partial f/parse (f/formatters :date-time)))
      (update-in [:concept-type] keyword)))

(defn- parse-errors
  "Parses an error response from a JSON response"
  [response]
  (-> response
      :body
      (cheshire/decode true)))

(defn- parse-concepts
  "Parses multiple concept from a JSON response"
  [response]
  (map #(-> %
            (update-in [:revision-date] (partial f/parse (f/formatters :date-time)))
            (update-in [:concept-type] keyword))
       (cheshire/decode (:body response) true)))

(defn get-concept-id
  "Make a GET to retrieve the id for a given concept-type, provider-id, and native-id."
  [concept-type provider-id native-id]
  (let [response (client/get (str concept-id-url concept-type "/" provider-id "/" native-id)
                             {:accept :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)})
        status (:status response)
        body (cheshire/parse-string (:body response))
        {:strs [concept-id errors]} body]
    {:status status :concept-id concept-id :errors errors}))

(defn get-concept-by-id-and-revision
  "Make a GET to retrieve a concept by concept-id and revision."
  [concept-id revision-id]
  (let [response (client/get (str concepts-url concept-id "/" revision-id)
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
  (let [response (client/get (str concepts-url concept-id)
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
         response (client/post (str concepts-url path)
                               {:query-params query-params
                                :body (cheshire/generate-string tuples)
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
         response (client/post (str concepts-url path)
                               {:query-params query-params
                                :body (cheshire/generate-string concept-ids)
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
  [concept-type params]
  (let [response (client/get (str concepts-url "search/" (inf/plural (name concept-type)))
                             {:query-params params
                              :accept :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)})
        status (:status response)]
    (if (= status 200)
      {:status status
       :concepts (parse-concepts response)}
      (assoc (parse-errors response) :status status))))

(defn get-expired-collection-concept-ids
  "Make a get to retrieve expired collection concept ids."
  [provider-id]
  (let [response (client/get (str concepts-url "search/expired-collections")
                             {:query-params (when provider-id {:provider provider-id})
                              :accept :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)})
        status (:status response)]
    (if (= status 200)
      {:status status
       :concept-ids (cheshire/parse-string (:body response))}
      (assoc (parse-errors response) :status status))))

(defn save-concept
  "Make a POST request to save a concept with JSON encoding of the concept.  Returns a map with
  status, revision-id, and a list of error messages"
  [concept]
  (let [response (client/post concepts-url
                              {:body (cheshire/generate-string concept)
                               :content-type :json
                               :accept :json
                               :throw-exceptions false
                               :connection-manager (conn-mgr)})
        status (:status response)
        body (cheshire/parse-string (:body response))
        {:strs [revision-id concept-id errors]} body]
    {:status status :revision-id revision-id :concept-id concept-id :errors errors}))

(defn delete-concept
  "Make a DELETE request to mark a concept as deleted. Returns the status and revision id of the
  tombstone."
  [concept-id & revision-id]
  (let [revision-id (first revision-id)
        url (if revision-id
              (format "%s%s/%s" concepts-url concept-id revision-id)
              (format "%s%s" concepts-url concept-id))
        response (client/delete url
                                {:throw-exceptions false
                                 :connection-manager (conn-mgr)})
        status (:status response)
        body (cheshire/parse-string (:body response))
        {:strs [revision-id errors]} body]
    {:status status :revision-id revision-id :errors errors}))

(defn force-delete-concept
  "Make a DELETE request to permanently remove a revison of a concept."
  [concept-id revision-id]
  (let [url (format "%sforce-delete/%s/%s" concepts-url concept-id revision-id)
        response (client/delete url
                                {:throw-exceptions false
                                 :connection-manager (conn-mgr)})
        status (:status response)
        body (cheshire/parse-string (:body response))
        {:strs [revision-id errors]} body]
    {:status status :revision-id revision-id :errors errors}))

(defn verify-concept-was-saved
  "Check to make sure a concept is stored in the database."
  [concept]
  (let [{:keys [concept-id revision-id]} concept
        stored-concept (:concept (get-concept-by-id-and-revision concept-id revision-id))]
    (= concept (dissoc stored-concept :revision-date))))

(defn create-and-save-collection
  "Creates, saves, and returns a concept with its data from metadata-db. "
  ([provider-id uniq-num]
   (create-and-save-collection provider-id uniq-num 1))
  ([provider-id uniq-num num-revisions]
   (create-and-save-collection provider-id uniq-num num-revisions {}))
  ([provider-id uniq-num num-revisions extra-fields]
   (let [concept (collection-concept provider-id uniq-num extra-fields)
         _ (dotimes [n (dec num-revisions)] (save-concept concept))
         {:keys [concept-id revision-id]} (save-concept concept)]
     (assoc concept :concept-id concept-id :revision-id revision-id))))

(defn create-and-save-granule
  "Creates, saves, and returns a concept with its data from metadata-db"
  ([provider-id parent-collection-id uniq-num]
   (create-and-save-granule provider-id parent-collection-id uniq-num 1))
  ([provider-id parent-collection-id uniq-num num-revisions]
   (let [concept (granule-concept provider-id parent-collection-id uniq-num)
         _ (dotimes [n (dec num-revisions)] (save-concept concept))
         {:keys [concept-id revision-id]} (save-concept concept)]
     (assoc concept :concept-id concept-id :revision-id revision-id))))

;;; providers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn save-provider
  "Make a POST request to save a provider with JSON encoding of the provider  Returns a map with
  status and a list of error messages."
  [provider-id]
  (let [response (client/post providers-url
                              {:body (cheshire/generate-string {:provider-id provider-id})
                               :content-type :json
                               :accept :json
                               :throw-exceptions false
                               :connection-manager (conn-mgr)})
        status (:status response)
        body (cheshire/parse-string (:body response))
        errors (get body "errors")
        provider-id (get body "provider-id")]
    {:status status :errors errors :provider-id provider-id}))

(defn get-providers
  "Make a GET request to retrieve the list of providers."
  []
  (let [response (client/get providers-url
                             {:accept :json
                              :throw-exceptions false
                              :connection-manager (conn-mgr)})
        status (:status response)
        body (cheshire/parse-string (:body response))
        errors (get body "errors")
        providers (get body "providers")]
    {:status status :errors errors :providers providers}))

(defn delete-provider
  "Make a DELETE request to remove a provider."
  [provider-id]
  (let [response (client/delete (format "%s/%s" providers-url provider-id)
                                {:accept :json
                                 :throw-exceptions false
                                 :connection-manager (conn-mgr)})
        status (:status response)
        body (cheshire/parse-string (:body response))
        errors (get body "errors")]
    {:status status :errors errors}))


(defn verify-provider-was-saved
  "Verify that the given provider-id is in the list of providers."
  [provider-id]
  (some #{provider-id} (:providers (get-providers))))

;;; miscellaneous
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn old-revision-concept-cleanup
  "Runs the old revision concept cleanup job"
  []
  (:status
    (client/post old-revision-concept-cleanup-url
                 {:throw-exceptions false
                  :headers {acl/token-header (transmit-config/echo-system-token)}
                  :connection-manager (conn-mgr)})))

(defn expired-concept-cleanup
  "Runs the expired concept cleanup job"
  []
  (:status
    (client/post expired-concept-cleanup-url
                 {:throw-exceptions false
                  :headers {acl/token-header (transmit-config/echo-system-token)}
                  :connection-manager (conn-mgr)})))

(defn reset-database
  "Make a request to reset the database by clearing out all stored concepts."
  []
  (:status
    (client/post reset-url {:throw-exceptions false
                            :headers {acl/token-header (transmit-config/echo-system-token)}
                            :connection-manager (conn-mgr)})))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn reset-database-fixture
  "Creates a database fixture function to reset the database after every test.
  Optionally accepts a list of provider-ids to create before the test"
  [& provider-ids]
  (fn [f]
    (try
      (doseq [pid provider-ids] (save-provider pid))
      (f)
      (finally (reset-database)))))

