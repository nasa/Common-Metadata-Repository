(ns cmr.search.api.smart-handoff
  "Defines the API for retrieving smart handoff schemas defined in CMR."
  (:require
   [clojure.java.io :as io]
   [cmr.common-app.api.routes :as cr]
   [cmr.common.mime-types :as mt]
   [compojure.core :refer :all]
   [compojure.route :as route]))

(def ^:private soto-schema
  "relative path of SOTO schema in the resources directory"
  "smart-handoff/soto-schema.json")

(def ^:private giovanni-schema
  "relative path of Giovanni schema in the resources directory"
  "smart-handoff/giovanni-schema.json")

(def ^:private edsc-schema
  "relative path of EDSC schema in the resources directory"
  "smart-handoff/edsc-schema.json")

(def ^:private smart-handoff-headers
  "smart handoff headers"
  (assoc (:headers cr/options-response)
                  cr/CONTENT_TYPE_HEADER (mt/with-utf-8 mt/json)))

(def smart-handoff-routes
  (context "/smart-handoff" []

    (GET "/soto" request
        {:status 200
         :body (slurp (io/resource soto-schema))
         :headers smart-handoff-headers})

    (GET "/giovanni" request
        {:status 200
         :body (slurp (io/resource giovanni-schema))
         :headers smart-handoff-headers})

    (GET "/edsc" request
        {:status 200
         :body (slurp (io/resource edsc-schema))
         :headers smart-handoff-headers})))
