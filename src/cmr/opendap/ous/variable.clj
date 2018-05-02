(ns cmr.opendap.ous.variable
  (:require
   [clojure.string :as string]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [ring.util.codec :as codec]
   [taoensso.timbre :as log]))

(defn build-query
  [passed-vars default-vars]
  (string/join "&" (map #(str (codec/url-encode "concept_id[]")
                              "=" %)
                        (if (seq passed-vars)
                            passed-vars
                            default-vars))))

(defn get-metadata
  "Given a 'params' data structure with a ':variables' key (which may or may
  not have values) and a list of all collection variable-ids, return the
  metadata for the passed variables, if defined, and for all associated
  variables, if params does not contain any."
  [search-endpoint user-token params variable-ids]
  (log/debug "Getting variable metadata for:" variable-ids)
  (let [url (str search-endpoint
                 "/variables?"
                 (build-query (:variables params) variable-ids))
        results (request/async-get url
                 (-> {}
                     (request/add-token-header user-token)
                     (request/add-accept "application/vnd.nasa.cmr.umm+json"))
                 response/json-handler)]
    (log/debug "Got results from CMR variable search:" results)
    (:items @results)))

(defn parse-dimensions
  [dim]
  {:x (:Size (first (filter #(= "XDim" (:Name %)) dim)))
   :y (:Size (first (filter #(= "YDim" (:Name %)) dim)))})

(defn parse-bounds
  [bounds]
  (let [lon-regex "Lon:\\s*(-?[0-9]+),\\s*(-?[0-9]+).*;\\s*"
        lat-regex "Lat:\\s*(-[0-9]+),\\s*(-?[0-9]+).*"
        [lon-lo lon-hi lat-lo lat-hi]
         (rest (re-find (re-pattern (str lon-regex lat-regex)) bounds))]
    {:lat {:begin lat-lo
           :end lat-hi}
     :lon {:begin lon-lo
           :end lon-hi}}))

(defn extract-bounding-info
  [entry]
  {:concept-id (get-in entry [:meta :concept-id])
   :name (get-in entry [:umm :Name])
   :dimensions (parse-dimensions (get-in entry [:umm :Dimensions]))
   :bounds (parse-bounds (get-in entry [:umm :Characteristics :Bounds]))
   :size (get-in entry [:umm :Characteristics :Size])})
