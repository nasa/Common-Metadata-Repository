(ns cmr.search.api.autocomplete
  "Defines the API for autocomplete-suggest in the CMR."
  (:require
    [cmr.search.services.autocomplete-service :as ac]
    [cmr.common-app.api.routes :as common-routes]
    [cmr.common.mime-types :as mt]
    [compojure.core :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Support Functions

(defn- get-autocomplete-suggestions
  "Invokes Elasticsearch for autocomplete query result"
  [query types]
  (if query
    {:status  200
     :headers {common-routes/CONTENT_TYPE_HEADER (str (mt/format->mime-type :json) "; charset=utf-8")}
     :body    {:query   query
               :results (ac/autocomplete query)}}
    {:status  400
     :headers {common-routes/CONTENT_TYPE_HEADER (str (mt/format->mime-type :json) "; charset=utf-8")}
     :body    {:message "No query term provided"
               :usage   "/autocomplete?q=<value>"}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Route Definitions

(def autocomplete-api-routes
  (context "/autocomplete" []
     (GET "/"
        [q types]
        (get-autocomplete-suggestions q types))))
