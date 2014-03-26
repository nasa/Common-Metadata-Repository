(ns cmr.ingest.data.mdb
  "Implements Ingest App datalayer access interface. Takes on the role of a proxy to metadata db."
  (:require [clj-http.client :as client]
            [cheshire.core :as  cheshire]
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
                                          :headers (ch/context->http-headers context)})
        status (:status response)
        body (cheshire/decode (:body response))
        errors-str (cheshire/generate-string (flatten (get body "errors")))  ;; use this for logging purposes ??
        err-msg (format "concept-type: %s provider-id: %s native-id: %s does not exist" concept-type provider-id native-id)]
    (cond (= 404 status) (errors/throw-service-error :not-found err-msg) ;; forward not available message to the provider
          :else (when-not (= 200 status)
                  (errors/internal-error! (str "Concept id fetch failed. MetadataDb app response status code: "  status (str response)))))
    (get (cheshire/parse-string (:body response)) "concept-id")))

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
        errors-str (cheshire/generate-string (flatten (get body "errors")))
        {:strs [concept-id revision-id]} body]
    (cond (= 422 status) ;; catalog rest supplied invalid concept id (either no revision id or one supplied)
          (errors/throw-service-error :invalid-data errors-str) 
          :else (when-not (= 201 status)
                  (errors/internal-error! (str "Save concept failed. MetadataDb app response status code: "  status (str response)))))
    {:concept-id concept-id :revision-id revision-id}))

(deftracefn delete-concept
  "Delete a concept from metatdata db."
  [context concept-id]
  (let [mdb-url (context->metadata-db-url context)
        response (client/delete (str mdb-url "/concepts/" concept-id) {:accept :json
                                                                       :throw-exceptions false
                                                                       :headers (ch/context->http-headers context)})
        status (:status response)
        body (cheshire/decode (:body response))
        errors-str (cheshire/generate-string (flatten (get body "errors")))]    
    (cond (= 200 status) (get body "revision-id")
          (= 404 status) (errors/throw-service-error :not-found errors-str) ;; donot drop errors from other services
          :else (errors/internal-error! (str "Delete concept operation failed. MetadataDb app response status code: "  status (str response))))))



