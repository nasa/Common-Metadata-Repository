(ns cmr.search.api.core
  "Core functions used by multiple API/routes namespaces."
  (:require
   [cmr.common-app.api.routes :as common-routes]
   [cmr.common-app.services.search :as search]
   [cmr.common.cache :as cache]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as svc-errors]
   [cmr.umm-spec.versioning :as umm-version]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Constants, Utilities, and Support Functions

(def search-result-supported-mime-types
  "The mime types supported by search."
  #{mt/any
    mt/xml
    mt/json
    mt/umm-json
    mt/umm-json-results
    mt/legacy-umm-json
    mt/echo10
    mt/dif
    mt/dif10
    mt/atom
    mt/iso19115
    mt/opendata
    mt/csv
    mt/kml
    mt/native})

(defn- add-scroll-id-to-cache
  "Adds the given ES scroll-id to the cache and returns the generated key"
  [context scroll-id]
  (when scroll-id
    (let [short-scroll-id (str (hash scroll-id))
          id-cache (cache/context->cache context search/scroll-id-cache-key)]
      (cache/set-value id-cache short-scroll-id scroll-id)
      short-scroll-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Core Functions

(defn path-w-extension->concept-id
  "Parses the path-w-extension to remove the concept id from the beginning"
  [path-w-extension]
  (second (re-matches #"([^\.]+?)(?:/[0-9]+)?(?:\..+)?" path-w-extension)))

(defn path-w-extension->revision-id
  "Parses the path-w-extension to extract the revision id. URL path should
  be of the form :concept-id[/:revision-id][.:format], e.g.,
  http://localohst:3003/concepts/C120000000-PROV1/2.xml."
  [path-w-extension]
  (when-let [revision-id (nth (re-matches #"([^\.]+)/([^\.]+)(?:\..+)?" path-w-extension) 2)]
    (try
      (when revision-id
        (Integer/parseInt revision-id))
      (catch NumberFormatException e
        (svc-errors/throw-service-error
          :invalid-data
          (format "Revision id [%s] must be an integer greater than 0." revision-id))))))

(defn get-search-results-format
  "Returns the requested search results format parsed from headers or from the URL extension,
  The search result format is keyword for any format other than umm-json. For umm-json,
  it is a map in the format of {:format :umm-json :version \"1.2\"}"
  ([concept-type path-w-extension headers default-mime-type]
   (get-search-results-format
     concept-type path-w-extension headers search-result-supported-mime-types default-mime-type))
  ([concept-type path-w-extension headers valid-mime-types default-mime-type]
   (let [result-format (mt/mime-type->format
                         (or (mt/path->mime-type path-w-extension valid-mime-types)
                             (mt/extract-header-mime-type valid-mime-types headers "accept" true)
                             (mt/extract-header-mime-type valid-mime-types headers "content-type" false))
                         default-mime-type)]
     (if (contains? #{:umm-json :umm-json-results} result-format)
       {:format result-format
        :version (or (mt/version-of (mt/get-header headers "accept"))
                     (umm-version/current-version concept-type))}
       result-format))))

(defn process-params
  "Processes the parameters by removing unecessary keys and adding other keys
  like result format."
  [concept-type params ^String path-w-extension headers default-mime-type]
  (let [result-format (get-search-results-format
                       concept-type path-w-extension headers default-mime-type)
        ;; Continue to treat the search extension "umm-json" as the legacy umm json response for now
        ;; to avoid breaking clients
        result-format (if (.endsWith path-w-extension ".umm-json")
                        :legacy-umm-json
                        result-format)
        ;; For search results umm-json is an alias of umm-json-results since we can't actually return
        ;; a set of search results that would match the UMM-C JSON schema
        result-format (if (= :umm-json (:format result-format))
                        (assoc result-format :format :umm-json-results)
                        result-format)]
    (-> params
        (dissoc :path-w-extension :token)
        (assoc :result-format result-format))))

(defn search-response
  "Returns the response map for finding concepts"
  [context response]
  (let [short-scroll-id (add-scroll-id-to-cache context (:scroll-id response))
        response (-> response
                     (update :result mt/format->mime-type)
                     (update :scroll-id (constantly short-scroll-id)))]
    (common-routes/search-response response)))
