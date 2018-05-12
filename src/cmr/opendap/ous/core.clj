(ns cmr.opendap.ous.core
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.opendap.ous.collection.core :as collection]
   [cmr.opendap.ous.collection.params.core :as params]
   [cmr.opendap.ous.collection.results :as results]
   [cmr.opendap.ous.granule :as granule]
   [cmr.opendap.ous.service :as service]
   [cmr.opendap.ous.variable :as variable]
   [cmr.opendap.util :as util]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility/Support Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn bounding-info->opendap-lat-lon
  [{var-name :name opendap-bounds :opendap}]
  (variable/format-opendap-bounds var-name opendap-bounds))

(defn bounding-info->opendap-query
  ([bounding-info]
    (bounding-info->opendap-query bounding-info nil))
  ([bounding-info bounding-box]
   (when (seq bounding-info)
     (str
      (->> bounding-info
           (map bounding-info->opendap-lat-lon)
           (string/join ",")
           (str "?"))
      ","
      (variable/format-opendap-lat-lon
       (variable/create-opendap-bounds bounding-box))))))

;; XXX WARNING!!! The pattern matching code has been taken from the Node.js
;;                prototype ... and IT IS AWFUL. This is only temporary ...

(def fallback-pattern #"(.*)(/datapool/DEV01)(.*)")
(def fallback-replacement "/opendap/DEV01/user")

(defn data-file->opendap-url
  [pattern-info data-file]
  (let [pattern (re-pattern (:pattern-match pattern-info))
        data-url (:link-href data-file)]
    (if (re-matches pattern data-url)
      (do
        (log/debug "Granule URL matched provided pattern ...")
        (string/replace data-url
                        pattern
                        (str (:pattern-subs pattern-info) "$2")))
      (do
        (log/debug
         "Granule URL didn't match provided pattern; trying default ...")
        (if (re-matches fallback-pattern data-url)
          (string/replace data-url
                          fallback-pattern
                          (str "$1" fallback-replacement "$3")))))))

(defn data-files->opendap-urls
  [params pattern-info data-files query-string]
  (->> data-files
       (map (partial data-file->opendap-url pattern-info))
       (map #(str % "." (:format params) query-string))))

(defn apply-bounding-conditions
  "There are several variable and bounding scenarios we need to consider:

  * no spatial subsetting and no variables - return no query string in OPeNDAP
    URL; this will give users all variables for the entire extent defined in
    the variables' metadata.
  * variables but no spatial subsetting - return a query string with just the
    variables requested; a `Latitude,Longitude` will also be appended to the
    OPeNDAP URL; this will give users just these variables, but for the entire
    extent defined in each variable's metadata.
  * variables and spatial subsetting - return a query string with the variables
    requested as well as the subsetting requested; this will give users just
    these variables, with data limited to the specified spatial range.
  * spatial subsetting but no variables - this is a special case that needs to
    do a little more work: special subsetting without variables will link to
    an essentially empty OPeNDAP file; as such, we need to iterate through all
    the variables in the metadata and create an OPeNDAP URL query string that
    provides the sensible default of all variables.

  For each of those conditions, a different value of `vars` will be returned,
  allowing for the desired result. Respective to the bullet points above:

  * `vars` - empty vector
  * `vars` - metadata for all the specified variable ids
  * `vars` - metadata for all the specified variable ids
  * `vars` - metadata for all the variables associated in the collection"
  [search-endpoint user-token coll {:keys [bounding-box variables] :as params}]
  (log/debugf (str "Applying bounding conditions with bounding box %s and "
                   "variable ids %s ...")
              bounding-box
              variables)
  (cond
    ;; Condition 1 - no spatial subsetting and no variables
    (and (nil? bounding-box) (empty? variables))
    []

    ;; Condition 2 - variables but no spatial subsetting
    (and (nil? bounding-box) (not (empty? variables)))
    (variable/get-metadata search-endpoint user-token params)

    ;; Condition 3 - variables and spatial subsetting
    (and bounding-box (not (empty? variables)))
    (variable/get-metadata search-endpoint user-token params)

    ;; Condition 4 - spatial subsetting but no variables
    (and bounding-box (empty? variables))
    (variable/get-metadata search-endpoint
     user-token
     (assoc params :variables (collection/extract-variable-ids coll)))))

(defn get-opendap-urls
  [search-endpoint user-token raw-params]
  (log/trace "Got params:" raw-params)
  (let [start (util/now)
        params (params/parse raw-params)
        bounding-box (:bounding-box params)
        granules (granule/get-metadata search-endpoint user-token params)
        data-files (map granule/extract-datafile-link granules)
        coll (collection/get-metadata search-endpoint user-token params)
        service-ids (collection/extract-service-ids coll)
        services (service/get-metadata search-endpoint user-token service-ids)
        pattern-info (service/extract-pattern-info (first services))
        vars (apply-bounding-conditions search-endpoint user-token coll params)
        bounding-info (map #(variable/extract-bounding-info % bounding-box)
                           vars)
        query (bounding-info->opendap-query bounding-info bounding-box)]
    (log/trace "data-files:" (into [] data-files))
    (log/trace "pattern-info:" pattern-info)
    (log/debug "variable bounding-info:" (into [] bounding-info))
    (log/debug "query:" query)
    (results/create
     (data-files->opendap-urls params pattern-info data-files query)
     :elapsed (util/timed start))))
