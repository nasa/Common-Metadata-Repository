(ns cmr.transmit.metadata-db
  "Provide functions to invoke metadata db app"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cmr.transmit.config :as config]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [cmr.system-trace.http :as ch]
            [cmr.system-trace.core :refer [deftracefn]]))

(deftracefn get-concept
  "Retrieve the concept with the given concept and revision-id"
  [context concept-id revision-id]
  (let [mdb-url (config/context->app-root-url context :metadata-db)
        response (client/get (format "%s/concepts/%s/%s" mdb-url concept-id revision-id)
                             {:accept :json
                              :throw-exceptions false
                              :headers (ch/context->http-headers context)})]
    (if (= 200 (:status response))
      (cheshire/decode (:body response) true)
      (errors/throw-service-error
        :not-found
        (str "Failed to retrieve concept " concept-id "/" revision-id " from metadata-db: " (:body response))))))

(deftracefn get-latest-concept
  "Retrieve the latest version of the concept"
  [context concept-id]
  (let [mdb-url (config/context->app-root-url context :metadata-db)
        response (client/get (format "%s/concepts/%s" mdb-url concept-id)
                             {:accept :json
                              :throw-exceptions false
                              :headers (ch/context->http-headers context)})]
    (if (= 200 (:status response))
      (cheshire/decode (:body response) true)
      (errors/throw-service-error
        :not-found
        (str "Failed to retrieve concept " concept-id " from metadata-db: " (:body response))))))

(deftracefn get-concept-id
  "Return a distinct identifier for the given arguments."
  [context concept-type provider-id native-id]
  (let [mdb-url (config/context->app-root-url context :metadata-db)
        request-url (str mdb-url "/concept-id/" (name concept-type) "/" provider-id "/" native-id)
        response (client/get request-url {:accept :json
                                          :headers (ch/context->http-headers context)
                                          :throw-exceptions false})
        status (:status response)
        body (cheshire/decode (:body response))]
    (case status
      404
      (let [err-msg (format "concept-type: %s provider-id: %s native-id: %s does not exist" concept-type provider-id native-id)]
        (errors/throw-service-error :not-found err-msg))

      200
      (get body "concept-id")

      ;; default
      (let [errors-str (cheshire/generate-string (flatten (get body "errors")))
            err-msg (str "Concept id fetch failed. MetadataDb app response status code: "  status)]
        (errors/internal-error! (str err-msg  " " errors-str))))))

(deftracefn get-collection-concept-id
  "Search metadata db and return the collection-concept-id that matches the search params"
  [context search-params]
  (let [mdb-url (config/context->app-root-url context :metadata-db)
        request-url (str mdb-url "/concepts/search/collections")
        response (client/get request-url {:accept :json
                                          :query-params search-params
                                          :headers (ch/context->http-headers context)
                                          :throw-exceptions false})
        status (:status response)
        body (cheshire/decode (:body response))]
    (case status
      404
      (let [err-msg (str "Unable to find collection-concept-id for search params: " search-params)]
        (errors/throw-service-error :not-found err-msg))

      200
      (get (first body) "concept-id")

      ;; default
      (let [errors-str (cheshire/generate-string (flatten (get body "errors")))
            err-msg (str "Collection concept id fetch failed. MetadataDb app response status code: "  status)]
        (errors/internal-error! (str err-msg  " " errors-str))))))


(deftracefn save-concept
  "Saves a concept in metadata db and index."
  [context concept]
  (let [mdb-url (config/context->app-root-url context :metadata-db)
        concept-json-str (cheshire/generate-string concept)
        response (client/post (str mdb-url "/concepts") {:body concept-json-str
                                                         :content-type :json
                                                         :accept :json
                                                         :throw-exceptions false
                                                         :headers (ch/context->http-headers context)})
        status (:status response)
        body (cheshire/decode (:body response))
        {:strs [concept-id revision-id]} body]
    (case status
      422
      (let [errors-str (cheshire/generate-string (flatten (get body "errors")))]
                           ;; catalog rest supplied invalid concept id
                           (errors/throw-service-error :invalid-data errors-str))

      201
      {:concept-id concept-id :revision-id revision-id}

      ;; default
      (errors/internal-error! (str "Save concept failed. MetadataDb app response status code: "
                                   status
                                   " "
                                   response)))))

(deftracefn delete-concept
  "Delete a concept from metatdata db."
  [context concept-id]
  (let [mdb-url (config/context->app-root-url context :metadata-db)
        response (client/delete (str mdb-url "/concepts/" concept-id) {:accept :json
                                                                       :throw-exceptions false
                                                                       :headers (ch/context->http-headers context)})
        status (:status response)
        body (cheshire/decode (:body response))]
    (case status
      404
      (let [errors-str (cheshire/generate-string (flatten (get body "errors")))]
                           (errors/throw-service-error :not-found errors-str))

      200
      (get body "revision-id")

      ;; default
      (errors/internal-error! (str "Delete concept operation failed. MetadataDb app response status code: "
                                   status
                                   " "
                                   response)))))