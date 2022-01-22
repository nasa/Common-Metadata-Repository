(ns cmr.system-int-test.utils.metadata-db-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [cmr.system-int-test.data2.provider-holdings :as ph]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.transmit.config :as transmit-config]
            [cmr.system-int-test.system :as s]))

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
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn provider-holdings
  "Returns the provider holdings from metadata db."
  []
  (let [url (url/mdb-provider-holdings-url)
        response (client/get url {:accept :json
                                  :connection-manager (s/conn-mgr)})
        {:keys [status body headers]} response]
    (if (= status 200)
      {:status status
       :headers headers
       :results (ph/parse-provider-holdings :json false body)}
      response)))

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
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn get-concept
  ([concept-id]
   (get-concept concept-id nil))
  ([concept-id revision-id]
   (let [response (client/get (url/mdb-concept-url concept-id revision-id)
                              {:accept :json
                               :throw-exceptions false
                               :connection-manager (s/conn-mgr)})]
     (is (some #{200 404} [(:status response)]))
     (when (= (:status response) 200)
       (-> response
           :body
           (json/decode true)
           (update-in [:concept-type] keyword))))))

(defn force-delete-concept
  "Force deletes a concept revision from Metadata db"
  ([concept-id revision-id]
   (force-delete-concept concept-id revision-id nil))
  ([concept-id revision-id force?]
   (let [query-params (if force?
                        {:force true}
                        {})]
     (client/request
      {:method :delete
       :url (url/mdb-force-delete-concept-url concept-id revision-id)
       :query-params query-params
       :accept :json
       :throw-exceptions false
       :connection-manager (s/conn-mgr)}))))


(defn concept-exists-in-mdb?
  "Check concept in mdb with the given concept and revision-id"
  [concept-id revision-id]
  (not (nil? (get-concept concept-id revision-id))))

(defn cleanup-old-revisions
  []
  (client/request {:method :post
                   :url (url/mdb-old-revision-cleanup-job-url)
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :connection-manager (s/conn-mgr)}))

(defn cleanup-expired-concepts
  []
  (client/request {:method :post
                   :url (url/mdb-expired-concept-cleanup-url)
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :connection-manager (s/conn-mgr)}))
