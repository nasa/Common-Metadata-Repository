(ns cmr.opendap.ous.common
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.exchange.common.results.core :as results]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.exchange.query.core :as query]
   [cmr.exchange.query.util :as query-util]
   [cmr.opendap.components.concept :as concept]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.ous.concepts.collection :as collection]
   [cmr.opendap.ous.concepts.granule :as granule]
   [cmr.opendap.ous.concepts.service :as service]
   [cmr.opendap.ous.concepts.variable :as variable]
   [cmr.opendap.ous.util.geog :as geog]
   [cmr.opendap.results.errors :as ous-errors]
   [cmr.opendap.results.warnings :as warnings]
   [cmr.opendap.util :as util]
   [cmr.opendap.validation :as validation]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility/Support Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn format-opendap-lat-lon
  [bounding-infos bounding-box]
  (when-let [bounding-info (first bounding-infos)]
    (variable/format-opendap-lat-lon bounding-info)))

(defn bounding-infos->opendap-query
  ([bounding-infos]
   (bounding-infos->opendap-query bounding-infos nil))
  ([bounding-infos bounding-box]
   (when (seq bounding-infos)
     (str
      (->> bounding-infos
           (map variable/format-opendap-bounds)
           (string/join ",")
           (str "?"))
      ","
      (format-opendap-lat-lon bounding-infos bounding-box)))))

(defn lat-dim?
  [dim]
  (= "LATITUDE_DIMENSION" (:Type dim)))

(defn lon-dim?
  [dim]
  (= "LONGITUDE_DIMENSION" (:Type dim)))

(defn gridded-dim?
  "Variables have a collection of dims; this function tests just one."
  [dim]
  (or (lat-dim? dim) (lon-dim? dim)))

(defn gridded-dims?
  "This function is intended to test all the dims in a var. To count as
  gridded data, the dimensions of a variable must contain both a latitude
  and longitude value."
  [dims]
  (->> dims
       (map gridded-dim?)
       (remove false?)
       count
       (= 2)))

(defn gridded-vars?
  "Given a collection of vars, extract the dims and test those. To count
  as gridded, all vars must be gridded."
  [vars]
  (->> vars
       (map #(gridded-dims? (get-in % [:umm :Dimensions])))
       (every? true?)))

(defn strip-spatial
  [params]
  (assoc params :bounding-box [] :subset []))

;; XXX The 'fallback' values are left-overs from previous work done in the
;;     Node.js prorotype. For more context, see CMR-4901 abd CMR-4912.
;; XXX Note that CMR-4912 has been moved to DURT-153.
;; XXX Note that CMR-5131 was created to keep using regex's in the
;;     short-term :-(
;; XXX The continued use of the fallbacks is a special case that needs to be
;;     addressed before CMR OPeNDAP can be used in general, for all granules.
;;     As such. the work in CMR-4912 will need to be finished before we can
;;     remove/update the following:

(def fallback-datafile-support
  "Note that the keys are the regex's and the values are what to replace with."
  {;; Initial testing for prorotype used the following replacement
   "(.*)(/datapool/DEV01)(.*)" "/opendap/DEV01/user"
   ;; First-pass at GES-DESC rollout used the following replacement
   "(.*)(/data/)(.*)" "/opendap/"})

(def fallback-patterns
  "This is for use in error messages."
  (format "'%s'" (string/join "','" (keys fallback-datafile-support))))

(defn match-data
  [match regex]
  {:match match
   :regex regex
   :replacement (get fallback-datafile-support regex)})

(defn fallback-matches
  [data-url]
  (->> fallback-datafile-support
       keys
       (map (fn [x] (match-data (re-matches (re-pattern x) data-url) x)))
       (remove #(nil? (:match %)))))

(defn matches-fallback?
  [matched-data]
  (->> matched-data
       (map :match)
       (some #(not (nil? %)))))

(defn has-fallback-replacement?
  [data-url matched-data]
  (->> matched-data
       (map (comp #(string/includes? data-url %) :replacement))
       (some true?)))

(defn replace-with-first-matching-fallback
  [data-url matched-data]
  (log/trace "Attempting Granule URL match/replace ...")
  (let [match (first matched-data)]
    (string/replace data-url
                    (re-pattern (:regex match))
                    (str "$1" (:replacement match) "$3"))))

(defn data-file->opendap-url
  [data-file]
  (let [data-url (:link-href data-file)
        matched-fallbacks (fallback-matches data-url)]
    (log/trace "Data file:" data-file)
    (cond (has-fallback-replacement? data-url matched-fallbacks)
          (do
            (log/debug (str "Data file already has the expected OPeNDAP URL; "
                            "skipping replacement ..."))
            data-url)

          (matches-fallback? matched-fallbacks)
          (replace-with-first-matching-fallback data-url matched-fallbacks)

          :else
          (let [msg (format ous-errors/no-matching-service-pattern
                            (fallback-patterns)
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

(defn process-results
  ([results start errs]
    (process-results results start errs {:warnings nil}))
  ([{:keys [params data-files query]} start errs warns]
    (log/trace "Got data-files:" (vec data-files))
    (if errs
      (do
        (log/error errs)
        errs)
      (let [urls-or-errs (data-files->opendap-urls params data-files query)]
        ;; Error handling for post-stages processing
        (if (errors/erred? urls-or-errs)
          (do
            (log/error urls-or-errs)
            urls-or-errs)
          (do
            (log/debug "Generated URLs:" (vec urls-or-errs))
            (results/create urls-or-errs :elapsed (util/timed start)
                                         :warnings warns)))))))

(defn apply-bounding-conditions
  "This function is where variable queries to the CMR are made. There are
  several conditions that apply when extracting variables, all related to
  spatial subsetting (bounding box), and these determine which variables
  are returned.

  The conditions are as follows:

  * no spatial subsetting and no variables - return no query string in OPeNDAP
    URL; this will give users (by default) all variables for the entire extent
    defined in the granule metadata.
  * variables but no spatial subsetting - return a query string with just the
    variables requested; a spatial subsetting for the granule's extent (e.g.,
    `Latitude,Longitude`) will also be appended to the OPeNDAP URL; this will
    give users just these variables, but for the entire extent defined in
    the granule metadata.
  * variables and spatial subsetting - return a query string with the variables
    requested as well as the subsetting requested; this will give users just
    these variables, with data limited to the specified spatial range.
  * spatial subsetting but no variables - this is a special case that needs to
    do a little more work: spatial subsetting without variables will link to
    an essentially empty OPeNDAP file; as such, we need to iterate through all
    the variables in the metadata and create an OPeNDAP URL query string that
    includes all of the variables.

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

(defn apply-gridded-conditions
  "This function is responsible for identifying whether data is girdded or not.
  Originally, the plan was to use processing level to make this determination,
  but due to issues with bad values in the metadata (for processing level),
  that wasn't practical. Instead, we decided to use the presence or absence of
  dimensional metadata that includes latitude and longitude references (new as
  of UMM-Var 1.2).

  This function is thus responsible for:

  * examining each variable for the presence or absence of lat/lon dimensional
    metadata
  * flagging the granule as gridded when all vars have this metadata
  * implicitly flagging the granule as non-gridded when some vars don't have
    this metadata
  * removing bounding information from params when the granule is non-gridded
  * adding a warning to API consumers that the spatial subsetting parameters
    have been stripped due to non-applicability."
  [vars params bounding-box]
  (if (gridded-vars? vars)
    [vars params bounding-box {}]
    (let [var-ids (string/join ", " (map #(get-in % [:meta :concept-id]) vars))
          warn1 (format warnings/non-gridded var-ids)]
      (log/warn warn1)
      (log/warn warnings/non-gridded-stripped)
      [vars
       (strip-spatial params)
       []
       {:warnings [warn1 warnings/non-gridded-stripped]}])))

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
  [component {:keys [endpoint token params]}]
  (log/debug "Starting stage 1 ...")
  (let [params (query/parse params)
        bounding-box (:bounding-box params)
        valid-lat (when bounding-box
                    (validation/validate-latitude
                     (query-util/bounding-box-lat bounding-box)))
        valid-lon (when bounding-box
                    (validation/validate-longitude
                     (query-util/bounding-box-lon bounding-box)))
        grans-promise (granule/async-get-metadata
                       component endpoint token params)
        ; grans-promise (concept/get :granules
        ;                component search-endpoint user-token params)
        coll-promise (concept/get :collection
                      component endpoint token params)
        errs (errors/collect params valid-lat valid-lon)]
    (log/debug "Params: " params)
    (log/debug "Bounding box: " bounding-box)
    (log/debug "Finishing stage 1 ...")
    [params bounding-box grans-promise coll-promise errs]))

(defn stage2
  "Note that this function is different for versions before v2.1 and versions
  after that. As such, no common implementation is provided."
  [component coll-promise grans-promise {:keys [endpoint token params]}]
  :not-implemented)

(defn stage3
  [component service-ids vars bounding-box {:keys [endpoint token]}]
  (log/debug "Starting stage 3 ...")
  (let [services-promise (service/async-get-metadata endpoint token service-ids)
        bounding-infos (map #(variable/extract-bounding-info % bounding-box)
                            vars)
        errs (apply errors/collect bounding-infos)]
    (when errs
      (log/error "Stage 3 errors:" errs))
    (log/trace "variables bounding-info:" (vec bounding-infos))
    (log/debug "Finishing stage 3 ...")
    [services-promise bounding-infos errs]))

(defn stage4
  [_component services-promise bounding-box bounding-infos _options]
  (log/debug "Starting stage 4 ...")
  (let [services (service/extract-metadata services-promise)
        query (bounding-infos->opendap-query bounding-infos bounding-box)
        errs (errors/collect services)]
    (when errs
      (log/error "Stage 4 errors:" errs))
    (log/trace "services:" services)
    (log/debug "Generated OPeNDAP query:" query)
    (log/debug "Finishing stage 4 ...")
    [query errs]))
