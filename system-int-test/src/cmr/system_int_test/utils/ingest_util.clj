(ns cmr.system-int-test.utils.ingest-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as json]
            [cmr.system-int-test.data.collection-helper :as ch]
            [cmr.system-int-test.data.granule-helper :as gh]
            [cmr.umm.echo10.collection :as c]
            [cmr.umm.echo10.granule :as g]
            [cmr.umm.echo10.core :as echo10]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.echo-util :as echo-util]))


(defn create-mdb-provider
  "Create the provider with the given provider id in the metadata db"
  [provider-id]
  (client/post (url/create-provider-url)
               {:body (format "{\"provider-id\": \"%s\"}" provider-id)
                :content-type :json
                :connection-manager (url/conn-mgr)}))

(defn get-providers
  []
  (-> (client/get (url/create-provider-url) {:connection-manager (url/conn-mgr)})
      :body
      (json/decode true)
      :providers))

(defn delete-provider
  "Delete the provider with the matching provider-id from the CMR metadata repo."
  [provider-id]
  (let [response (client/delete (url/delete-provider-url provider-id)
                                {:throw-exceptions false
                                 :connection-manager (url/conn-mgr)})
        status (:status response)]
    (is (some #{200 404} [status]))))

(defn reindex-collection-permitted-groups
  "Tells ingest to run the reindex-collection-permitted-groups job"
  []
  (let [response (client/post (url/reindex-collection-permitted-groups-url))]
    (is (= 200 (:status response)))))

(defn cleanup-expired-collections
  "Tells ingest to run the cleanup-expired-collections job"
  []
  (let [response (client/post (url/cleanup-expired-collections-url))]
    (is (= 200 (:status response)))))

(defn ingest-concept
  "Ingest a concept and return a map with status, concept-id, and revision-id"
  [{:keys [metadata format concept-type concept-id revision-id provider-id native-id] :as concept}]
  (let [headers (merge {}
                       (when concept-id {"concept-id" concept-id})
                       (when revision-id {"revision-id" revision-id}))
        response (client/request
                   {:method :put
                    :url (url/ingest-url provider-id concept-type native-id)
                    :body  metadata
                    :content-type format
                    :headers headers
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (url/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn save-concept
  "Save a concept to the metadata db and return a map with status, concept-id, and revision-id"
  [concept]
  (let [response (client/request
                   {:method :post
                    :url (url/mdb-concepts-url)
                    :body  (json/generate-string concept)
                    :content-type :json
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (url/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn tombstone-concept
  "Create a tombstone in mdb for the concept, but don't delete it from elastic."
  [concept]
  (let [{:keys [concept-id revision-id]} concept
        response (client/request
                   {:method :delete
                    :url (str (url/mdb-concepts-url) "/" concept-id "/" revision-id)
                    :body  (json/generate-string concept)
                    :content-type :json
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (url/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn delete-concept
  "Delete a given concept."
  [{:keys [provider-id concept-type native-id] :as concept}]
  (let [response (client/request
                   {:method :delete
                    :url (url/ingest-url provider-id concept-type native-id)
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (url/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))


(defn ingest-concepts
  "Ingests all the given concepts assuming that they should all be successful."
  [concepts]
  (doseq [concept concepts]
    (is (= {:status 200
            :concept-id (:concept-id concept)
            :revision-id (:revision-id concept)}
           (ingest-concept concept)))))

(defn delete-concepts
  "Deletes all the given concepts assuming that they should all be successful."
  [concepts]
  (doseq [concept concepts]
    (is (#{404 200} (:status (delete-concept concept))))))

(defn get-concept
  ([concept-id]
   (get-concept concept-id nil))
  ([concept-id revision-id]
   (let [response (client/get (url/mdb-concept-url concept-id revision-id)
                              {:accept :json
                               :throw-exceptions false
                               :connection-manager (url/conn-mgr)})]
     (is (some #{200 404} [(:status response)]))
     (when (= (:status response) 200)
       (-> response
           :body
           (json/decode true)
           (update-in [:concept-type] keyword))))))

(defn concept-exists-in-mdb?
  "Check concept in mdb with the given concept and revision-id"
  [concept-id revision-id]
  (not (nil? (get-concept concept-id revision-id))))

(defn admin-connect-options
  "This returns the options to send when executing admin commands"
  []
  {:connection-manager (url/conn-mgr)
   :query-params {:token "mock-echo-system-token"}})

(defn reset
  "Resets the database and the elastic indexes"
  []
  (client/post (url/dev-system-reset-url) (admin-connect-options))
  (index/refresh-elastic-index))

(defn clear-caches
  []
  (client/post (url/dev-system-clear-cache-url) (admin-connect-options)))

;;; fixture - each test to call this fixture
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-provider
  ([provider-guid provider-id]
   (create-provider provider-guid provider-id true))
  ([provider-guid provider-id grant-all?]
   (create-mdb-provider provider-id)
   (echo-util/create-providers {provider-guid provider-id})

   (when grant-all?
     (echo-util/grant [echo-util/guest-ace
                       echo-util/registered-user-ace]
                      (assoc (echo-util/catalog-item-id provider-guid)
                             :collection-applicable true
                             :granule-applicable true)
                      nil))))

(defn reset-fixture
  "Creates the given providers in ECHO and the CMR then clears out all data at the end."
  ([]
   (reset-fixture {}))
  ([provider-guid-id-map]
   (reset-fixture provider-guid-id-map true))
  ([provider-guid-id-map grant-all?]
   (fn [f]
     (try
       (reset)
       (doseq [[provider-guid provider-id] provider-guid-id-map]
         (create-provider provider-guid provider-id grant-all?))
       (f)
       (finally
         (reset))))))
