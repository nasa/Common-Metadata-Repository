(ns cmr.metadata-db.int-test.utility
  "Contains various utitiltiy methods to support integeration tests."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]))

;;; Enpoints for services - change this for tcp-mon
(def port 3001)

(def concepts-url (str "http://localhost:" port "/concepts/"))

(def concept-id-url (str "http://localhost:" port "/concept-id/"))

(def reset-url (str "http://localhost:" port "/reset"))


;;; utility methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn concept
  "Creates a concept to be used for testing."
  []
  {:concept-type :collection
   :native-id "provider collection id"
   :concept-id "C1-PROV1"
   :provider-id "PROV1"
   :metadata "xml here"
   :format "echo10"})

(defn get-concept-id
  "Make a GET to retrieve the id for a given concept-type, provider-id, and native-id."
  [concept-type provider-id native-id]
  (let [response (client/get (str concept-id-url concept-type "/" provider-id "/" native-id)
                             {:accept :json
                              :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        concept-id (get body  "concept-id")
        errors (get body "errors")]
    {:status status :concept-id concept-id :error-messages errors}))

(defn get-concept-by-id-and-revision
  "Make a GET to retrieve a concept by concept-id and revision."
  [concept-id revision-id]
  (let [response (client/get (str concepts-url concept-id "/" revision-id)
                             {:accept :json
                              :throw-exceptions false})
        status (:status response)]
    (if (= status 200)
      (let [found-concept (clojure.walk/keywordize-keys (cheshire/parse-string (:body response)))]
        {:status status :concept found-concept})
      {:status status :concept nil})))

(defn get-concept-by-id
  "Make a GET to retrieve a concept by concept-id."
  [concept-id]
  (let [response (client/get (str concepts-url concept-id)
                             {:accept :json
                              :throw-exceptions false})
        status (:status response)]
    (if (= status 200)
      (let [found-concept (clojure.walk/keywordize-keys (cheshire/parse-string (:body response)))]
        {:status status :concept found-concept})
      {:status status :concept nil})))

(defn get-concepts
  "Make a POST to retrieve concepts by concept-id and revision."
  [tuples]
  (let [body {:concept-revisions tuples}]
    (let [response (client/post (str concepts-url "search")
                                {:body (cheshire/generate-string body)
                                 :body-encoding "UTF-8"
                                 :content-type :json
                                 :accept :json
                                 :throw-exceptions false})
          status (:status response)
          concepts (vec (cheshire/parse-string (:body response)))]
      {:status status :concepts concepts})))

(defn concepts-and-concept-id-revisions-equal?
  "Compare a vector of concepts returned by the API to a set of concept-id/revision-ids."
  [concepts concept-id-revision-ids]
  (if (not= (count concepts) (count concept-id-revision-ids))
    false
    (every? true? 
            (map (fn [[con-id rev-id]] 
                   (some (fn [concept]
                           (and (= (get concept "concept-id") con-id) 
                                (= (get concept "revision-id" rev-id))))
                         concepts))
                 concept-id-revision-ids))))

#_(defn concepts-and-ids-equal?
  "Compare a vector of concepts returned by the API to a set of concept-ids"
  [concepts concept-ids]
  (if (not= (count concepts) (count concept-ids))
    false
    (every? true? (map #(= (get %1 "concept-id") %2) concepts concept-ids))))

(defn save-concept
  "Make a POST request to save a concept with JSON encoding of the concept.  Returns a map with
  status, revision-id, and a list of error messages"
  [concept]
  (let [response (client/post concepts-url
                              {:body (cheshire/generate-string concept)
                               :body-encoding "UTF-8"
                               :content-type :json
                               :accept :json
                               :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")
        error-messages (get body "errors")]
    {:status status :revision-id revision-id :error-messages error-messages}))

(defn delete-concept
  "Make a DELETE request to mark a concept as deleted. Returns the status and revision id of the
  tombstone."
  [concept-id]
  (let [response (client/delete (str concepts-url concept-id)
                                {:throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")
        error-messages (get body "errors")]
    {:status status :revision-id revision-id :error-messages error-messages}))

(defn reset-database
  "Make a request to reset the database by clearing out all stored concepts."
  []
  (let [response (client/delete (str concepts-url "reset")
                                {:throw-exceptions false})
        status (:status response)]
    status))

(defn verify-concept-was-saved
  "Check to make sure a concept is stored in the database."
  [concept revision-id]
  (let [concept-id (:concept-id concept)
        stored-concept-and-status (get-concept-by-id-and-revision concept-id revision-id)
        stored-concept (:concept stored-concept-and-status)
        stored-concept-id (:concept-id stored-concept)]
    (is (= stored-concept-id concept-id))))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn reset-database-fixture
  "Reset the database after every test."
  [f]
  (try
    (f)
    (finally (reset-database))))