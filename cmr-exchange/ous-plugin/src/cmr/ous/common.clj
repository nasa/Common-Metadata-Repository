(ns cmr.ous.common
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.exchange.common.results.core :as results]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.exchange.common.util :as util]
   [cmr.exchange.query.core :as query]
   [cmr.exchange.query.const :as const]
   [cmr.exchange.query.util :as query-util]
   [cmr.metadata.proxy.components.concept :as concept]
   [cmr.metadata.proxy.concepts.collection :as collection]
   [cmr.metadata.proxy.concepts.granule :as granule]
   [cmr.metadata.proxy.concepts.service :as service]
   [cmr.metadata.proxy.concepts.variable :as variable]
   [cmr.metadata.proxy.results.errors :as metadata-errors]
   [cmr.ous.components.config :as config]
   [cmr.ous.results.errors :as ous-errors]
   [cmr.ous.results.warnings :as warnings]
   [cmr.ous.util.geog :as geog]
   [cmr.ous.util.validation :as validation]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility/Support Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- format-opendap-lat-lon
  [bounding-infos is-dap4?]
  (when-let [bounding-info (first bounding-infos)]
    (geog/format-opendap-lat-lon bounding-info geog/default-lat-lon-stride is-dap4?)))

(defn bounding-infos->opendap-query
  ([bounding-infos]
   (bounding-infos->opendap-query bounding-infos false))
  ([bounding-infos is-dap4?]
   (when (seq bounding-infos)
     (let [delimiter (geog/get-delimiter is-dap4?)
           head (if is-dap4? "?dap4.ce=" "?")]
       (str
        (->> bounding-infos
             (map #(geog/format-opendap-bounds % geog/default-lat-lon-stride is-dap4?))
             (string/join delimiter)
             (str head))
        delimiter
        (format-opendap-lat-lon bounding-infos is-dap4?))))))

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

(defn process-tag-datafile-replacement
  "Takes a data file URL and replaces part of the URL based on the passed in tag data."
  [data-url tag-data]
  (log/trace "Attempting Granule URL match/replace using tag data ...")
  (let [pattern (re-pattern (format "(.*)(%s)(.*)" (:match tag-data)))]
    (string/replace data-url pattern (str "$1" (:replace tag-data) "$3"))))

(defn remove-trailing-html
  "Takes a url and remove the .html in the end."
  [url]
  (log/trace "Attempting trailing .html removal from url...")
  (when url
    (string/replace url #"\.html$" "")))

(defn granule-link->opendap-url
  "Converts a granule-link to an OPeNDAP URL."
  [granule-link tag-data]
  (let [data-url (-> granule-link :datafile-link :href remove-trailing-html)
        data-url-replaced-by-tags (when tag-data
                                    (process-tag-datafile-replacement data-url tag-data))
        opendap-url (-> granule-link :opendap-link :href remove-trailing-html)]
    (log/trace "Data file:" granule-link)
    (log/trace "Tag data:" tag-data)
    (log/trace "Data URL replaced by tags:" data-url-replaced-by-tags)
    (or data-url-replaced-by-tags
        opendap-url
        {:errors ["Could not determine OPeNDAP URL from tags and datafile URL or base OPeNDAP URL."]})))

(defn replace-double-slashes
  "Replaces double slashes in a URL. Note that this function could be called with an error map
   instead of a string in which case we do not perform any string replacement."
  [url]
  (if (string? url)
    (string/replace url #"(?<!(http:|https:))[//]+" "/")
    url))

(defn- is-dap-version-4?
  "Returns true if the given dap-version is DAP4"
  [dap-version]
  (= "4" dap-version))

(defn- granule-links->opendap-urls
  "Takes a collection of granule links maps and converts each one to an OPeNDAP URL. Returns an
  error if unable to determine any of the OPeNDAP URLs."
  [params dap-version granule-links tag-data query-string]
  (when granule-links
    (let [urls (map (comp replace-double-slashes
                          #(granule-link->opendap-url % tag-data))
                    granule-links)
          format (or (:format params) const/default-format)
          dap-format (if (and (is-dap-version-4? dap-version)
                              (= "nc" format))
                       "dap.nc4"
                       format)]
      (if (errors/any-erred? urls)
        (do
          (log/error "Some problematic urls:" (vec urls))
          (apply errors/collect urls))
        (map #(str % "." dap-format query-string) urls)))))

;; XXX This function is nearly identical to one of the same name in
;;     cmr.sizing.core -- we should put this somewhere both can use,
;;     after generalizing to take a func and the func's args ...
(defn process-results
  ([results start errs]
   (process-results results start errs {:warnings nil}))
  ([{:keys [params dap-version granule-links sa-header hits-header tag-data query]} start errs warns]
   (log/trace "Got granule-links:" (vec granule-links))
   (log/trace "Process-results tag-data:" tag-data)
   (if errs
     (do
       (log/error errs)
       errs)
     (let [urls-or-errs (granule-links->opendap-urls params dap-version granule-links tag-data query)]
       ;; Error handling for post-stages processing
       (if (errors/erred? urls-or-errs)
         (do
           (log/error urls-or-errs)
           urls-or-errs)
         (do
           (log/info (format "request-id: %s generated-urls: %s"
                             (:request-id params)
                             (vec urls-or-errs)))
           (results/create urls-or-errs :request-id (:request-id params)
                           :elapsed (util/timed start)
                           :hits-header hits-header
                           :sa-header sa-header
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
                   "variable ids %s")
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
    (and bounding-box (or (seq variables)))
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
  [component {:keys [endpoint token params sa-header]}]
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
                       component endpoint token params sa-header)
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
        bounding-infos (map #(geog/extract-bounding-info % bounding-box)
                            vars)
        errs (apply errors/collect bounding-infos)]
    (when errs
      (log/error "Stage 3 errors:" errs))
    (log/trace "variables bounding-info:" (vec bounding-infos))
    (log/debug "Finishing stage 3 ...")
    [services-promise bounding-infos errs]))

(defn stage4
  [_component services-promise bounding-box bounding-infos options]
  (log/debug "Starting stage 4 ...")
  (let [services (service/extract-metadata services-promise)
        is-dap4? (is-dap-version-4? (:dap-version options))
        query (bounding-infos->opendap-query bounding-infos is-dap4?)
        errs (errors/collect services)]
    (when errs
      (log/error "Stage 4 errors:" errs))
    (log/trace "services:" services)
    (log/debug "Generated OPeNDAP query:" query)
    (log/debug "Finishing stage 4 ...")
    [query errs]))
