(ns cmr.ingest.data.indexer
  "Implements Ingest App datalayer access interface. Takes on the role of a proxy to indexer app."
  (:require [clj-http.client :as client]
            [cheshire.core :as  cheshire]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.system-trace.http :as ch]
            [cmr.transmit.config :as transmit-config]
            [cmr.transmit.connection :as transmit-conn]))

(deftracefn index-concept
  "Forward newly created concept for indexer app consumption."
  [context concept-id revision-id]
  (let [conn (transmit-config/context->app-connection context :indexer)
        indexer-url (transmit-conn/root-url conn)
        concept-attribs {:concept-id concept-id, :revision-id revision-id}
        response (client/post indexer-url {:body (cheshire/generate-string concept-attribs)
                                           :content-type :json
                                           :throw-exceptions false
                                           :accept :json
                                           :headers (ch/context->http-headers context)
                                           :connection-manager (transmit-conn/conn-mgr conn)})
        status (:status response)]
    (when-not (= 201 status)
      (errors/internal-error! (str "Operation to index a concept failed. Indexer app response status code: "  status  " " response)))))

(deftracefn delete-concept-from-index
  "Delete a concept with given revision-id from index."
  [context concept-id revision-id]
  (let [conn (transmit-config/context->app-connection context :indexer)
        indexer-url (transmit-conn/root-url conn)
        response (client/delete (format "%s/%s/%s" indexer-url concept-id revision-id)
                                {:accept :json
                                 :throw-exceptions false
                                 :headers (ch/context->http-headers context)
                                 :connection-manager (transmit-conn/conn-mgr conn)})
        status (:status response)]
    (when-not (some #{200, 201} [status])
      (errors/internal-error! (str "Delete concept operation failed. Indexer app response status code: "  status " " response)))))

