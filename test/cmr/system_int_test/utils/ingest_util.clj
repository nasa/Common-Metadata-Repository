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
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.system-int-test.utils.url-helper :as url]))

(defn create-provider
  "Create the provider with the given provider id"
  [provider-id]
  (client/post url/create-provider-url
               {:body (format "{\"provider-id\": \"%s\"}" provider-id)
                :content-type :json}))

(defn delete-provider
  "Delete the provider with the matching provider-id from the CMR metadata repo."
  [provider-id]
  (let [response (client/delete (url/delete-provider-url provider-id)
                                {:throw-exceptions false})
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
                    :throw-exceptions false})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn delete-concept
  "Delete a given concept."
  [{:keys [provider-id concept-type native-id] :as concept}]
  (let [response (client/request
                   {:method :delete
                    :url (url/ingest-url provider-id concept-type native-id)
                    :accept :json
                    :throw-exceptions false})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn get-concept
  ([concept-id]
   (get-concept concept-id nil))
  ([concept-id revision-id]
   (let [response (client/get (url/mdb-concept-url concept-id revision-id)
                              {:accept :json
                               :throw-exceptions false})]
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
  (client/post (url/mdb-reset-url))
  (client/post (url/indexer-reset-url)))

;; time related
;;;;;;;;;;;;;;;;;;;;
(defn get-joda-date-time
  "Return joda data time given date-time-tz string - yyyy-MM-ddTHH:mm:ss.SSSZ"
  [tz]
  (f/parse (f/formatters :date-time) tz))


(defn get-tz-date-time-str
  "Return date-time-tz string - yyyy-MM-ddTHH:mm:ss.SSSZ given joda data time"
  [jt]
  (f/unparse (f/formatters :date-time) jt))

(defn elapsed-time-in-secs
  "Return time diff in seconds between joda times.
  In one arg case return diff between now and t0
  Note: t/now cannot participate directly in t/interval computation"
  ([t0]
   (if (string? t0) (elapsed-time-in-secs (-> t0 get-joda-date-time))
     (elapsed-time-in-secs t0 (-> (t/now) get-tz-date-time-str get-joda-date-time))))
  ([t0 t1]
   (cond (string? t0)
         (elapsed-time-in-secs (-> t0 get-joda-date-time) t1)
         (string? t1)
         (elapsed-time-in-secs t0 (-> t1 get-joda-date-time))
         :else
         (t/in-seconds (t/interval t0 t1)))))


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

(comment
  ;;;;;;;;;

  (elapsed-time-in-secs "2014-05-02T07:24:13.799Z")
  (elapsed-time-in-secs (org.joda.time.DateTime. 1399015453788))
  (apply t/before? '((org.joda.time.DateTime. 1399015453788) (org.joda.time.DateTime. 1399015453788)))

  ;;;;;;;;;;
  )
