(ns cmr.opendap.ous.core
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.errors :as errors]
   [cmr.opendap.ous.collection :as collection]
   [cmr.opendap.ous.granule :as granule]
   [cmr.opendap.ous.query.params.core :as params]
   [cmr.opendap.ous.query.results :as results]
   [cmr.opendap.ous.service :as service]
   [cmr.opendap.ous.util.core :as ous-util]
   [cmr.opendap.ous.variable :as variable]
   [cmr.opendap.util :as util]
   [cmr.opendap.validation :as validation]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility/Support Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn bbox->bounding-info
  ;; XXX coll is required as an arg here because it's needed in a
  ;;     workaround for different data sets using different starting
  ;;     points for their indices in OPeNDAP
  ;;
  ;; XXX This is being tracked in CMR-4982
  [coll bounding-box]
  (variable/map->BoundingInfo
    {:bounds bounding-box
     :opendap (variable/create-opendap-bounds
               bounding-box
               {:reversed? (variable/lat-reversed? coll)})}))

(defn format-opendap-lat-lon
  ;; XXX coll is required as an arg here because it's needed in a
  ;;     workaround for different data sets using different starting
  ;;     points for their indices in OPeNDAP
  ;;
  ;; XXX This is being tracked in CMR-4982
  [coll bounding-infos bounding-box]
  (if-let [bounding-info (first bounding-infos)]
    (variable/format-opendap-lat-lon bounding-info)
    (variable/format-opendap-lat-lon
     (bbox->bounding-info coll bounding-box))))

(defn bounding-infos->opendap-query
  ;; XXX coll is required as an arg here because it's needed in a
  ;;     workaround for different data sets using different starting
  ;;     points for their indices in OPeNDAP
  ;;
  ;; XXX This is being tracked in CMR-4982
  ([coll bounding-infos]
    (bounding-infos->opendap-query coll bounding-infos nil))
  ([coll bounding-infos bounding-box]
   (when (seq bounding-infos)
     (str
      (->> bounding-infos
           (map variable/format-opendap-bounds)
           (string/join ",")
           (str "?"))
      ","
      (format-opendap-lat-lon coll bounding-infos bounding-box)))))


;; XXX The `fallback-*` vars are left-overs from previous work done in the
;;     Node.js prorotype. For more context, see CMR-4901 abd CMR-4912.
;;     Their continued use is a special case that needs to be addressed
;;     before CMR OPeNDAP can be used in general, for all granules. As such.
;;     the work in CMR-4912 will need to be finished before we can remove/
;;     update the following:

(def fallback-pattern #"(.*)(/datapool/DEV01)(.*)")
(def fallback-replacement "/opendap/DEV01/user")

(defn data-file->opendap-url
  [data-file]
  (let [data-url (:link-href data-file)]
    (log/debug "Data file:" data-file)
    (cond (string/includes? data-url fallback-replacement)
          (do
            (log/debug (str "Data file already has the expected OPeNDAP URL; "
                            "skipping replacement ..."))
            data-url)

          (re-matches fallback-pattern data-url)
          (do
            (log/debug
              "Attempting Granule URL match/replace ...")
            (string/replace data-url
                            fallback-pattern
                            (str "$1" fallback-replacement "$3")))

          :else
          (let [msg (format errors/no-matching-service-pattern
                            fallback-pattern
                            data-url)]
            (log/error msg)
            {:errors [msg]}))))

(defn replace-double-slashes
  [url]
  (string/replace url #"(?<!(http:|https:))[//]+" "/"))

(defn data-files->opendap-urls
  [params data-files query-string]
  (when data-files
    (let [urls (map (comp replace-double-slashes
                          data-file->opendap-url)
                    data-files)]
      (if (errors/any-erred? urls)
        (do
          (log/error "Some problematic urls:" (vec urls))
          (apply errors/collect urls))
        (map #(str % "." (:format params) query-string) urls)))))

(defn apply-level-conditions
  ""
  [coll params]
  (let [level (collection/extract-processing-level coll)]
    (log/warn "Got level:" level)
    params))

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
    (and (nil? bounding-box) (seq variables))
    (variable/get-metadata search-endpoint user-token params)

    ;; Condition 3 - variables and spatial subsetting
    (and bounding-box (seq variables))
    (variable/get-metadata search-endpoint user-token params)

    ;; Condition 4 - spatial subsetting but no variables
    (and bounding-box (empty? variables))
    (variable/get-metadata search-endpoint
     user-token
     (assoc params :variables (collection/extract-variable-ids coll)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Stages for URL Generation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;; The various stage functions below were originally called as part of a `let`
;;; block in `get-opendap-urls` but now have been split out into stages
;;; organized by dependency.
;;;
;;; In particular:
;;;
;;; Functions which depend only upon the parameters (or parsing of those
;;; parameters) are placed in the first stage. Functions which depend upon
;;; either the parameters or the results of the first stage are placed in the
;;; second stage, etc.
;;;
;;; The reason for this was to make it very clear when various functions
;;; could be called as late as possible, and only call those which were
;;; absolutely necessary at a given point. And the reason for _that_ was so
;;; the code could be properly prepared for async execution.

(defn stage1
  [component search-endpoint user-token raw-params]
  (log/debug "Starting stage 1 ...")
  (let [params (params/parse raw-params)
        bounding-box (:bounding-box params)
        valid-lat (when bounding-box
                    (validation/validate-latitude
                     (ous-util/bounding-box-lat bounding-box)))
        valid-lon (when bounding-box
                    (validation/validate-longitude
                     (ous-util/bounding-box-lon bounding-box)))
        grans-promise (granule/async-get-metadata
                       component search-endpoint user-token params)
        coll-promise (collection/async-get-metadata
                      search-endpoint user-token params)
        errs (errors/collect params valid-lat valid-lon)]
    (log/debug "Params: " params)
    (log/debug "Bounding box: " bounding-box)
    (log/debug "Finishing stage 1 ...")
    [params bounding-box grans-promise coll-promise errs]))

(defn stage2
  [search-endpoint user-token params coll-promise grans-promise]
  (log/debug "Starting stage 2 ...")
  (let [granules (granule/extract-metadata grans-promise)
        coll (collection/extract-metadata coll-promise)
        data-files (map granule/extract-datafile-link granules)
        service-ids (collection/extract-service-ids coll)
        params (apply-level-conditions coll params)
        vars (apply-bounding-conditions search-endpoint user-token coll params)
        errs (apply errors/collect (concat [granules coll vars] data-files))]
    (when errs
      (log/error "Stage 2 errors:" errs))
    (log/trace "data-files:" (vec data-files))
    (log/trace "service ids:" service-ids)
    (log/debug "Finishing stage 2 ...")
    ;; XXX coll is returned here because it's needed in a workaround
    ;;     for different data sets using different starting points
    ;;     for their indices in OPeNDAP
    ;;
    ;; XXX This is being tracked in CMR-4982
    [coll params data-files service-ids vars errs]))

(defn stage3
  [coll search-endpoint user-token bounding-box service-ids vars]
  ;; XXX coll is required as an arg here because it's needed in a
  ;;     workaround for different data sets using different starting
  ;;     points for their indices in OPeNDAP
  ;;
  ;; XXX This is being tracked in CMR-4982
  (log/debug "Starting stage 3 ...")
  (let [services-promise (service/async-get-metadata
                          search-endpoint user-token service-ids)
        bounding-infos (map #(variable/extract-bounding-info
                              coll % bounding-box)
                            vars)
        errs (apply errors/collect bounding-infos)]
    (when errs
      (log/error "Stage 3 errors:" errs))
    (log/trace "variables bounding-info:" (vec bounding-infos))
    (log/debug "Finishing stage 3 ...")
    [services-promise bounding-infos errs]))

(defn stage4
  [coll bounding-box services-promise bounding-infos]
  (log/debug "Starting stage 4 ...")
  (let [services (service/extract-metadata services-promise)
        query (bounding-infos->opendap-query coll bounding-infos bounding-box)
        errs (errors/collect services)]
    (when errs
      (log/error "Stage 4 errors:" errs))
    (log/debug "services:" services)
    (log/debug "Generated OPeNDAP query:" query)
    (log/debug "Finishing stage 4 ...")
    [query errs]))

(defn get-opendap-urls
  [component user-token raw-params]
  (log/trace "Got params:" raw-params)
  (let [start (util/now)
        search-endpoint (config/get-search-url component)
        ;; Stage 1
        [params bounding-box granules coll s1-errs] (stage1
                                                     component
                                                     search-endpoint
                                                     user-token
                                                     raw-params)
        ;; Stage 2
        [coll params data-files service-ids vars s2-errs] (stage2
                                                           search-endpoint
                                                           user-token
                                                           params
                                                           coll
                                                           granules)
        ;; Stage 3
        [services bounding-info s3-errs] (stage3
                                          coll
                                          search-endpoint
                                          user-token
                                          bounding-box
                                          service-ids
                                          vars)
        ;; Stage 4
        [query s4-errs] (stage4 coll
                                bounding-box
                                services
                                bounding-info)
        ;; Error handling for all stages
        errs (errors/collect
              start params bounding-box granules coll s1-errs
              data-files service-ids vars s2-errs
              services bounding-info s3-errs
              query s4-errs
              {:errors (errors/check
                        [not data-files errors/empty-gnl-data-files])})]
    (log/debug "Got data-files:" (vec data-files))
    (if errs
      (do
        (log/debug "Got errors:" errs)
        errs)
      (let [urls-or-errs (data-files->opendap-urls params
                                                   data-files
                                                   query)]
        ;; Error handling for post-stages processing
        (if (errors/erred? urls-or-errs)
          urls-or-errs
          (results/create urls-or-errs :elapsed (util/timed start)))))))
