(ns cmr.search.api.autocomplete
  "Defines the API for autocomplete-suggest in the CMR."
  (:require
    [clojure.string :as str]
    [cmr.common.log :refer (debug info warn error)]
    [cmr.common.services.errors :as svc-errors]
    [cmr.search.services.autocomplete-service :as ac]
    [cmr.search.services.query-service :as qs]
    [cmr.common-app.api.routes :as common-routes]
    [cmr.common.mime-types :as mt]
    [compojure.core :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Support Functions

(defn- get-autocomplete-suggestions
  "Invokes Elasticsearch for autocomplete query result"
  [ctx params]
  (let [term      (:q params)
        raw-types (:types params)]
    (if term
      (let [types   (if raw-types
                      (filter #(not (empty? %))
                              (map str/lower-case (map str/trim (str/split raw-types #",")))))
            results (ac/autocomplete ctx (str/lower-case term) types)]
        {:status  200
         :headers {common-routes/CONTENT_TYPE_HEADER (str (mt/format->mime-type :json) "; charset=utf-8")
                   common-routes/CORS_ORIGIN_HEADER  "*"}
         :body    {:query   term
                   :results results}})
      (svc-errors/throw-service-errors
       :bad-request
       ["Missing param [q]" "Usage [/autocomplete?q=<term>]"]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Route Definitions

(def autocomplete-api-routes
  (context "/autocomplete" []
           (GET "/"
                {params :params ctx :request-context}
                (get-autocomplete-suggestions ctx params))))
