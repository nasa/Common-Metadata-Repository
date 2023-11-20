(ns cmr.common-app.services.kms-fetcher
  "Provides functions to easily fetch keywords from the GCMD Keyword Management Service (KMS). It
  will use a cache in order to minimize calls to the GCMD KMS and improve performance. The job
  defined in this namespace should be used to keep the KMS keywords fresh. As a result of persisting the
  keywords in Redis, the CMR will still be able to lookup KMS keywords even when the GCMD KMS is unavailable.
  CMR will use the last keyword values which were retrieved from the GCMD KMS before it became unavailable.

  The KMS keywords are all cached under a single :kms key. The structure looks like the following:
  {:kms {:platforms [{:category \"C\" :sub-category \"S\"
                      :short-name \"SN-1\" :long-name \"LN\"}
                     {...}]}
         :providers [...]}"
  (:require
   [cmr.common-app.services.kms-lookup :as kms-lookup]
   [cmr.common.cache :as cache]
   [cmr.common.jobs :refer [def-stateful-job]]
   [cmr.common.log :as log :refer [debug info warn error]]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.transmit.kms :as kms]))

(def nested-fields-mappings
  "Mapping from field name to the list of subfield names in order from the top of the hierarchy to
  the bottom."
  {:data-centers [:level-0 :level-1 :level-2 :level-3 :short-name :long-name :url]
   :archive-centers [:level-0 :level-1 :level-2 :level-3 :short-name :long-name :url]
   :platforms [:basis :category :sub-category :short-name :long-name]
   :platforms2 [:basis :category :sub-category :short-name :long-name]
   :instruments [:category :class :type :subtype :short-name :long-name]
   :projects [:short-name :long-name]
   :temporal-keywords [:temporal-resolution-range]
   :location-keywords [:category :type :subregion-1 :subregion-2 :subregion-3]
   :science-keywords [:category :topic :term :variable-level-1 :variable-level-2 :variable-level-3 :detailed-variable]
   :measurement-name [:context-medium :object :quantity]
   :concepts [:short-name]
   :iso-topic-categories [:iso-topic-category]
   :related-urls [:url-content-type :type :subtype]
   :granule-data-format [:short-name]
   :mime-type [:mime-type]
   :processing-levels [:processing-level]})

(def FIELD_NOT_PRESENT
  "A string to indicate that a field is not present within a KMS keyword."
  "Not Provided")

(def kms-cache-key
  "The key used to store the KMS cache in the system cache map."
  :kms)

(defn create-kms-cache
  "Used to create the cache that will be used for caching KMS keywords. All applications caching
  KMS keywords should use the same fallback cache to ensure functionality even if GCMD KMS becomes
  unavailable."
  []
  (redis-cache/create-redis-cache {:keys-to-track [kms-cache-key]}))

(defn- fetch-gcmd-keywords-map
  "Calls GCMD KMS endpoints to retrieve the keywords. Response is a map structured in the same way
  as used in the KMS cache."
  [context]
  (let [kms-cache (cache/context->cache context kms-cache-key)
        kms-cache-value (cache/get-value kms-cache kms-cache-key)]
    (kms-lookup/create-kms-index
     (into {}
           (for [keyword-scheme (keys kms/keyword-scheme->field-names)]
             ;; if the keyword-scheme-value is nil that means we could not get the KMS keywords
             ;; in this case use the cached value value instead so that we dont wipe out the cache.
             (if-let [keyword-scheme-value (kms/get-keywords-for-keyword-scheme context keyword-scheme)]
               [keyword-scheme keyword-scheme-value]
               [keyword-scheme (get kms-cache-value keyword-scheme)]))))))

(defn get-kms-index
  "Retrieves the GCMD keywords map from the cache."
  [context]
  (when-not (:ignore-kms-keywords context)
    (let [cache (cache/context->cache context kms-cache-key)]
      (info (format "*** the KMS cache size is %s" (cache/cache-size cache)))
      (cache/get-value cache kms-cache-key (partial fetch-gcmd-keywords-map context)))))

(defn refresh-kms-cache
  "Refreshes the KMS keywords stored in the cache. This should be called from a background job on a
  timer to keep the cache fresh."
  [context]
  (let [cache (cache/context->cache context kms-cache-key)
        gcmd-keywords-map (fetch-gcmd-keywords-map context)]
    (info "Refreshed KMS cache.")
    (cache/set-value cache kms-cache-key gcmd-keywords-map)
    (info (format "*** the KMS cache size is %d" (cache/cache-size cache)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Job for refreshing the KMS keywords cache. Only one node needs to refresh the cache.
(def-stateful-job RefreshKmsCacheJob
  [_ system]
  (refresh-kms-cache {:system system}))

(defn refresh-kms-cache-job
  "The singleton job that refreshes the KMS cache. The keywords are infrequently updated by the
  GCMD team, usually once a week."
  [job-key]
  {:job-type RefreshKmsCacheJob
   :job-key job-key
   :daily-at-hour-and-minute [02 00]})
