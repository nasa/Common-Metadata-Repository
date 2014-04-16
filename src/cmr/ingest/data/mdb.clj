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
    (cond (= 404 status) (let [err-msg (format "concept-type: %s provider-id: %s native-id: %s does not exist" concept-type provider-id native-id)]
                           (errors/throw-service-error :not-found err-msg))
          :else (when-not (= 200 status) (let [errors-str (cheshire/generate-string (flatten (get body "errors")))
                                               err-msg (str "Concept id fetch failed. MetadataDb app response status code: "  status)]
                                           (errors/internal-error! (str err-msg  " " errors-str)))))
    (get body "concept-id")))

(deftracefn get-collection-concept-id
  "Search metadata db and return the collection-concept-id that matches the search params"
  [context search-params]
  (let [mdb-url (context->metadata-db-url context)
        ;; TODO: Here we are working around the fact that the collection search API
        ;; is not ready in metadata db. Switch to that API when it is ready.
        ;; This hack is wrong and only works for system-int-test.
        ; request-url (str mdb-url "/search/collection?" (codec/form-encode search-params))
        request-url (format "%s/concept-id/collection/%s/%s"
                            mdb-url
                            (:provider-id search-params)
                            (:entry-title search-params))
        response (client/get request-url {:accept :json
                                          :headers (ch/context->http-headers context)
                                          :throw-exceptions false})
        status (:status response)
        body (cheshire/decode (:body response))]
    (cond (= 404 status) (let [err-msg (str "Unable to find collection-concept-id for search params: " search-params)]
                           (errors/throw-service-error :not-found err-msg))
          :else (when-not (= 200 status) (let [errors-str (cheshire/generate-string (flatten (get body "errors")))
                                               err-msg (str "Collection concept id fetch failed. MetadataDb app response status code: "  status)]
                                           (errors/internal-error! (str err-msg  " " errors-str)))))
    (get body "concept-id")))

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
    (cond (= 422 status) (let [errors-str (cheshire/generate-string (flatten (get body "errors")))]
                           ;; catalog rest supplied invalid concept id
                           (errors/throw-service-error :invalid-data errors-str))
          :else (when-not (= 201 status)
                  (errors/internal-error! (str "Save concept failed. MetadataDb app response status code: "  status " " response))))
    {:concept-id concept-id :revision-id revision-id}))

(deftracefn delete-concept
  "Delete a concept from metatdata db."
  [context concept-id]
  (let [mdb-url (context->metadata-db-url context)
        response (client/delete (str mdb-url "/concepts/" concept-id) {:accept :json
                                                                       :throw-exceptions false
                                                                       :headers (ch/context->http-headers context)})
        status (:status response)
        body (cheshire/decode (:body response))]
    (cond (= 200 status) (get body "revision-id")
          (= 404 status) (let [errors-str (cheshire/generate-string (flatten (get body "errors")))]
                           (errors/throw-service-error :not-found errors-str))
          :else (errors/internal-error! (str "Delete concept operation failed. MetadataDb app response status code: "  status " " response)))))
