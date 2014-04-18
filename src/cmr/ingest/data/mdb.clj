(ns cmr.ingest.data.mdb
  "Implements Ingest App datalayer access interface. Takes on the role of a proxy to metadata db."
  (:require [clj-http.client :as client]
            [cheshire.core :as  cheshire]
            [ring.util.codec :as codec]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.system-trace.http :as ch]))

(defn- context->metadata-db-url
  [context]
  (-> context :system :config :mdb-url))

(deftracefn get-concept-id
  "Return a distinct identifier for the given arguments."
  [context concept-type provider-id native-id]
  (let [mdb-url (context->metadata-db-url context)
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
  (let [mdb-url (context->metadata-db-url context)
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
  (let [mdb-url (context->metadata-db-url context)
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
  (let [mdb-url (context->metadata-db-url context)
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