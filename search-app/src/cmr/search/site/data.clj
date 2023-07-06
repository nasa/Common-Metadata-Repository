(ns cmr.search.site.data
  "The functions of this namespace are specifically responsible for generating
  data structures to be consumed by site page templates.

  Of special note: this namespace and its sibling `page` namespace are only
  ever meant to be used in the `cmr.search.site` namespace, particularly in
  support of creating site routes for access in a browser.

  Under no circumstances should `cmr.search.site.data` be accessed from outside
  this context; the data functions defined herein are specifically for use
  in page templates, structured explicitly for their needs."
  (:require
   [clojure.string :as string]
   [cmr.common-app.config :as common-config]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-execution :as query-exec]
   [cmr.common-app.services.search.query-model :as query-model]
   [cmr.common-app.services.search.query-to-elastic :as q2e]
   [cmr.common-app.site.data :as common-data]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.doi :as doi]
   [cmr.common.log :refer [debug error]]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :refer [defn-timed]]
   [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]
   [cmr.search.services.query-service :as query-svc]
   [cmr.search.site.util :as util]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.metadata-db :as mdb]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Data utility functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defconfig metadata-preview-root
  "URL root of the metadata preview plugin"
  {:default "https://access.sit.earthdata.nasa.gov"})

(defconfig metadata-preview-version
  "version of the metadata preview plugin"
  {:default "0.0.25"})

(defmulti get-providers
  "Get the providers, based on contextual data.

  The `:cli` variant uses a special constructed context (see
  `static.StaticContext`).

  The default variant is the original, designed to work with the regular
  request context which contains the state of a running CMR."
  :execution-context)

(defmethod get-providers :cli
  [context]
  (let [providers-url (format "%sproviders"
                              (transmit-config/application-public-root-url :ingest))]
    (util/endpoint-get providers-url {:accept mt/json})))

(defmethod get-providers :default
  [context]
  (mdb/get-providers context))

(defmulti collection-data
  "Get the collection data associated with a provider and tag.

  The `:cli` variant uses a special constructed context (see
  `static.StaticContext`).

  The default variant is the original, designed to work with the regular
  request context which contains the state of a running CMR."
  (fn [context & args] (:execution-context context)))

(defmethod collection-data :cli
  [context tag provider-id]
  (as-> (transmit-config/application-public-root-url :search) data
        (format "%scollections" data)
        (util/endpoint-get data {:accept mt/umm-json-results
                                 :query-params {:provider provider-id
                                                :tag-key tag
                                                :include_facets "v2"
                                                :page-size 2000}})
        (:items data)
        (sort-by #(get-in % [:umm :EntryTitle]) data)))

(defmethod gcrf/query-results->concept-ids :query-specified
  [results]
  (map :concept-id (:items results)))

(defn-timed get-collection-data
  "Get the collection data from elastic by provider id and tag. Sort results
  by entry title"
  ([context tag provider-id]
   (get-collection-data context tag provider-id []))
  ([context tag provider-id granule-conditions]
   (let [conditions (query-svc/generate-query-conditions-for-parameters
                     context
                     :collection
                     {:tag-key tag
                      :provider provider-id})
         query (query-model/query {:concept-type :collection
                                   :condition (gc/and-conds conditions)
                                   :skip-acls? false
                                   :page-size :unlimited
                                   :result-format :query-specified
                                   :result-features [:granule-counts]
                                   :result-fields [:concept-id
                                                   :doi
                                                   :entry-title
                                                   :short-name
                                                   :version-id]})
         result (query-exec/execute-query context query)
         collection-concept-ids (remove nil? (mapv :concept-id (:items result)))]

     (when (seq collection-concept-ids)
       (let [gran-agg-condition (gc/and-conds
                                 (concat granule-conditions
                                         [(query-model/string-conditions :collection-concept-id collection-concept-ids true)]))
             granule-query (query-model/query {:concept-type :granule
                                               :condition gran-agg-condition
                                               :page-size 0
                                               :result-format :query-specified
                                               :result-fields []
                                               :aggregations {:granule-counts-by-collection-id
                                                              {:terms {:field (q2e/query-field->elastic-field
                                                                               :collection-concept-id :granule)
                                                                       :size (count collection-concept-ids)}}}})
             elastic-result (query-exec/execute-query context granule-query)
             collection-concept-id-to-count-map (gcrf/search-results->granule-counts elastic-result)
             {:keys [items]} result]
         (sort-by :entry-title
                  (map #(assoc % :granule-count
                               (get collection-concept-id-to-count-map (:concept-id %))) items)))))))

(defmethod collection-data :default
  ([context tag provider-id]
   (get-collection-data context tag provider-id))
  ([context tag provider-id granule-conditions]
   (get-collection-data context tag provider-id granule-conditions)))

(defn-timed provider-data
  "Create a provider data structure suitable for template iteration to
  generate links.

  Note that the given tag will be used to filter provider collections data
  that is used on the destination page."
  [context tag data]
  (let [provider-id (:provider-id data)
        collections (collection-data context tag provider-id)]
    (if (seq collections)
      {:id provider-id
       :tag tag
       :collections collections
       :collections-count (count collections)}
      {:id provider-id
       :tag tag
       :collections []
       :collection-count 0})))

(defn-timed providers-data
  "Given a list of provider maps, create the nested data structure needed
  for rendering providers in a template."
  [context tag providers]
  (debug "Using providers:" providers)
  (->> providers
       (pmap (partial provider-data context tag))
       ;; Only want to include providers with EOSDIS collections
       (remove #(zero? (get % :collections-count 0)))
       (sort-by :id)))

(defn make-holding-data
  "Given a single item from a query's collections, update the item with data
  for linking to its landing page."
  [cmr-base-url item]
  (assoc item
         :canonical-link-href (doi/get-landing-page cmr-base-url item)
         :cmr-link-href (doi/get-cmr-landing-page cmr-base-url (:concept-id item))))

(defn make-holdings-data
  "Given a collection from an elastic search query, generate landing page
  links appropriate for the collection."
  [cmr-base-url coll]
  (map (partial make-holding-data cmr-base-url) coll))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Page data functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn app-url->stac-urls
  "Convert the search-app url into the stac related urls.
  The reason is we need to access cmr-stac endpoints from CMR search landing page.
  For example, search base-url locally is localhost:3003, stac base-url is localhost:3000,
  search base-url in sit is https://cmr.sit.earthdata.nasa.gov:443/search,
  stac base-url is https://cmr.sit.earthdata.nasa.gov/"
  [base-url]
  (let [stac-base-url (-> base-url
                          (string/replace #"gov.*" "gov/")
                          (string/replace #"3003/?" "3000/"))]
    {:stac-url (str stac-base-url "stac")
     :cloudstac-url (str stac-base-url "cloudstac")
     :stac-docs-url (if (string/includes? stac-base-url "3000")
                      (str stac-base-url "stac/docs")
                      (str stac-base-url "stac/docs/index.html"))
     :static-cloudstac-url (str stac-base-url "static-cloudstac")}))

(defmulti base-page
  "Data that all app pages have in common.

  The `:cli` variant uses a special constructed context (see
  `static.StaticContext`).

  The default variant is the original, designed to work with the regular
  request context which contains the state of a running CMR."
  :execution-context)

(defmethod base-page :cli
  [context]
  (assoc (common-data/base-static) :app-title "CMR Search"
         :release-version (str "v " (common-config/release-version))))

(defmethod base-page :default
  [context]
  (let [base-page (common-data/base-page context)
        base-url (:base-url base-page)
        {:keys [stac-url cloudstac-url stac-docs-url static-cloudstac-url]} (app-url->stac-urls base-url)]
    (assoc base-page :app-title "CMR Search"
                     :release-version (str "v " (common-config/release-version))
                     :stac-url stac-url
                     :cloudstac-url cloudstac-url
                     :stac-docs-url stac-docs-url
                     :static-cloudstac-url static-cloudstac-url)))

(defn get-collection
  "Provide collection data that will be rendered on collection landing page."
  [context concept-id]
  (assoc (base-page context)
         :concept-id concept-id
         :token (:token context)
         :preview-root (metadata-preview-root)
         :preview-version (metadata-preview-version)))

(defn get-directory-links
  "Provide the list of links that will be rendered on the top-level directory
  page."
  [context]
  (merge
   (base-page context)
   {:links [{:href "site/collections/directory/eosdis"
             :text "EOSDIS Collections"}]}))

(defn get-eosdis-directory-links
  [context]
  (->> context
       (get-providers)
       (providers-data context "gov.nasa.eosdis")
       (hash-map :providers)
       (merge (base-page context))))

(defn app-url->virtual-directory-url
  "Convert the search-app url into the Virtual Directory url."
  [base-url]
  (string/replace base-url #"search\/?" "virtual-directory/"))

(defn get-provider-tag-landing-links
  "Generate the data necessary to render EOSDIS landing page links."
  ([context provider-id tag]
   (get-provider-tag-landing-links context provider-id tag (constantly true)))
  ([context provider-id tag filter-fn]
   (let [holdings (filter filter-fn
                          (make-holdings-data
                           (util/get-app-url context)
                           (collection-data context tag provider-id [(query-model/boolean-condition :downloadable true)])))
         common-data (base-page context)
         virtual-directory-url (app-url->virtual-directory-url (get common-data :base-url))]
     (merge
      common-data
      {:virtual-directory-url virtual-directory-url
       :provider-id provider-id
       :tag-name (util/supported-directory-tags tag)
       :holdings holdings}))))

(defn get-provider-tag-sitemap-landing-links
  "Generate the data necessary to render EOSDIS landing page links that will
  be included in a sitemap.xml file.

  Note that generally the sitemap spec does not support cross-site inclusions,
  thus the filtering-out of non-CMR links."
  [context provider-id tag]
  (get-provider-tag-landing-links
   context
   provider-id
   tag
   #(string/includes?
     (str %)
     (util/get-app-url context))))

(defn csw-page
  [context]
  (let [base-page (common-data/base-page context "CSW Retirement Page")
        base-url (:base-url base-page)
        {:keys [stac-url]} (app-url->stac-urls base-url)
        opensearch-url (string/replace stac-url #"/stac" "/opensearch")]
    (assoc base-page
           :stac-url stac-url
           :opensearch-url opensearch-url)))
