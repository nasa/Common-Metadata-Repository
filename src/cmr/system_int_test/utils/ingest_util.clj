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
            [cmr.system-int-test.utils.index-util :as index]))


(defn create-provider
  "Create the provider with the given provider id"
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

(defn ingest-concept
  "Ingest a concept and return a map with status, concept-id, and revision-id"
  [{:keys [metadata content-type concept-type concept-id revision-id provider-id native-id] :as concept}]
  (let [headers (merge {}
                       (when concept-id {"concept-id" concept-id})
                       (when concept-id {"revision-id" revision-id}))
        response (client/request
                   {:method :put
                    :url (url/ingest-url provider-id concept-type native-id)
                    :body  metadata
                    :content-type content-type
                    :headers headers
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
       (json/decode (:body response) true)))))

(defn concept-exists-in-mdb?
  "Check concept in mdb with the given concept and revision-id"
  [concept-id revision-id]
  (not (nil? (get-concept concept-id revision-id))))

(defn reset
  "Resets the database and the elastic indexes"
  []
  (client/post (url/mdb-reset-url) {:connection-manager (url/conn-mgr)})
  (client/post (url/indexer-reset-url) {:connection-manager (url/conn-mgr)})
  (client/post (url/search-reset-url) {:connection-manager (url/conn-mgr)})
  (index/refresh-elastic-index))



;;; fixture - each test to call this fixture
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reset-fixture
  "Creates a database fixture function to reset the database after every test.
  Optionally accepts a list of provider-ids to create before the test"
  [& provider-ids]
  (fn [f]
    (try
      (reset)
      (doseq [pid provider-ids] (create-provider pid))
      (f)
      (finally
        (reset)))))
