(ns cmr.transmit.index-set
  "Provide functions to invoke index set app"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cheshire.core :as cheshire]
            [cmr.system-trace.core :refer [deftracefn]]))

;; Defines the host and port of index set app
(def endpoint
  {:host "localhost"
   :port "3005"})

(def index-set-root-url
  (format "http://%s:%s" (:host endpoint) (:port endpoint)))

;; url applicable to create, get and delete index-sets
(def index-set-url
  (format "%s/%s" index-set-root-url "index-sets"))

(defn get-index-set
  "Submit a request to index-set app to fetch an index-set assoc with an id"
  [id]
  (let [response (client/request
                   {:method :get
                    :url (format "%s/%s" index-set-url (str id))
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/decode (:body response) true)]
    (case status
      404 nil
      200 body
      (errors/internal-error! (format "Unexpected error fetching index-set with id: %s,
                                      Index set app reported status: %s, error: %s"
                                      id status (pr-str (flatten (:errors body))))))))
