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

(defn parse-annotated-bounds
  "Parse bounds that are annotated with Lat and Lon, returning values
  in the same order that CMR uses for spatial bounding boxes."
  [bounds]
  (let [lon-regex "Lon:\\s*(-?[0-9]+),\\s*(-?[0-9]+).*;\\s*"
        lat-regex "Lat:\\s*(-[0-9]+),\\s*(-?[0-9]+).*"
        [lon-lo lon-hi lat-lo lat-hi]
         (rest (re-find (re-pattern (str lon-regex lat-regex)) bounds))]
    [lon-lo lat-lo lon-hi lat-hi]))

(defn parse-cmr-bounds
  [bounds]
  "Parse a list of lat/lon values ordered according to the CMR convention
  of lower-left lon, lower-left lat, upper-right long, upper-right lat."
  (map string/trim (string/split bounds #",\s*")))

(defn parse-bounds
  [bounds]
  (if (string/starts-with? bounds "Lon")
    (parse-annotated-bounds bounds)
    (parse-cmr-bounds bounds)))

(defn extract-bounds
  [entry]
  (->> entry
       (#(get-in % [:umm :Characteristics :Bounds]))
       parse-bounds
       (map #(Integer/parseInt %))))

(defn extract-bounding-info
  [entry]
  {:concept-id (get-in entry [:meta :concept-id])
   :name (get-in entry [:umm :Name])
   :dimensions (parse-dimensions (get-in entry [:umm :Dimensions]))
   :bounds (extract-bounds entry)
   :size (get-in entry [:umm :Characteristics :Size])})

(defn most-frequent
  "This identifies the most frequently occuring data in a collection
  and returns it."
  [data]
  (->> data
       frequencies
       ;; the 'frequencies' function puts data first; let's swap the order
       (map (fn [[k v]] [v k]))
       ;; sort in reverse order to get the highest counts first
       (sort (comp - compare))
       ;; just get the highest
       first
       ;; the first element is the count, the second is the bounding data
       second))

(defn dominant-bounds
  "This function is intended to be used if spatial subsetting is not
  proivded in the query: in that case, all the bounds of all the variables
  will be counted, and the one most-used is what will be returned."
  [bounding-info]
  (->> bounding-info
       (map :bounds)
       most-frequent))

(defn dominant-dimensions
  "Get the most common dimensions from the bounding-info."
  [bounding-info]
  (->> bounding-info
       (map :dimensions)
       most-frequent))
