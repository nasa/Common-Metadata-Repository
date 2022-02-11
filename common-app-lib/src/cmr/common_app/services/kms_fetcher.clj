(ns cmr.common-app.services.kms-fetcher
  "Provides functions to easily fetch keywords from the GCMD Keyword Management Service (KMS). It
  will use a cache in order to minimize calls to the GCMD KMS and improve performance. The job
  defined in this namespace should be used to keep the KMS keywords fresh. KMS keywords will be
  cached using a fallback cache with Redis as the backup store. See the documentation for
  cmr.common.cache.fallback-cache for more details. As a result of persisting the keywords in Redis,
  the CMR will still be able to lookup KMS keywords even when the GCMD KMS is unavailable. CMR will
  use the last keyword values which were retrieved from the GCMD KMS before it became unavailable.

  The KMS keywords are all cached under a single :kms key. The structure looks like the following:
  {:kms {:platforms [{:category \"C\" :sub-category \"S\"
                      :short-name \"SN-1\" :long-name \"LN\"}
                     {...}]}
         :providers [...]}"
  (:require
    [clojure.string :as str]
    [cmr.common-app.services.kms-lookup :as kms-lookup]
    [cmr.common.cache :as cache]
    [cmr.common.cache.deflating-cache :as deflating-cache]
    [cmr.common.cache.fallback-cache :as fallback-cache]
    [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
    [cmr.common.config :refer [defconfig]]
    [cmr.common.jobs :refer [def-stateful-job]]
    [cmr.common.log :as log :refer [debug info warn error]]
    [cmr.common.services.errors :as errors]
    [cmr.common.util :as util]
    [cmr.redis-utils.redis-cache :as redis-cache]
    [cmr.transmit.cache.consistent-cache :as consistent-cache]
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
   :granule-data-format [:short-name :uuid]
   :mime-type [:mime-type :uuid]})

(def FIELD_NOT_PRESENT
  "A string to indicate that a field is not present within a KMS keyword."
  "Not Provided")

(def kms-cache-key
  "The key used to store the KMS cache in the system cache map."
  :kms)

(defconfig kms-cache-consistent-timeout-seconds
  "The number of seconds between when the KMS cache should check with redis for consistence"
  {:default 3600
   :type Long})

(defn create-kms-cache
  "Used to create the cache that will be used for caching KMS keywords. All applications caching
  KMS keywords should use the same fallback cache to ensure functionality even if GCMD KMS becomes
  unavailable."
  []
  (stl-cache/create-single-thread-lookup-cache
    (fallback-cache/create-fallback-cache
      (consistent-cache/create-consistent-cache
       {:hash-timeout-seconds (kms-cache-consistent-timeout-seconds)})
      (deflating-cache/create-deflating-cache
        (redis-cache/create-redis-cache)
        kms-lookup/create-kms-index
        kms-lookup/deflate))))

(defn- fetch-gcmd-keywords-map
  "Calls GCMD KMS endpoints to retrieve the keywords. Response is a map structured in the same way
  as used in the KMS cache."
  [context]
  (kms-lookup/create-kms-index
   (into {}
         (for [keyword-scheme (keys kms/keyword-scheme->field-names)]
           [keyword-scheme (kms/get-keywords-for-keyword-scheme context keyword-scheme)]))))

(defn get-kms-index
  "Retrieves the GCMD keywords map from the cache."
  [context]
  (when-not (:ignore-kms-keywords context)
    (let [cache (cache/context->cache context kms-cache-key)]
      (cache/get-value cache kms-cache-key (partial fetch-gcmd-keywords-map context)))))

(defn- refresh-kms-cache
  "Refreshes the KMS keywords stored in the cache. This should be called from a background job on a
  timer to keep the cache fresh. This will throw an exception if there is a problem fetching the
  keywords from KMS. The caller is responsible for catching and logging the exception."
  [context]
  (let [cache (cache/context->cache context kms-cache-key)
        gcmd-keywords-map (fetch-gcmd-keywords-map context)]
    (info "Refreshed KMS cache.")
    (cache/set-value cache kms-cache-key gcmd-keywords-map)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Job for refreshing the KMS keywords cache. Only one node needs to refresh the cache because
;; we use a consistent cache which uses redis to coordinate any changes to the cache.
(def-stateful-job RefreshKmsCacheJob
  [_ system]
  (refresh-kms-cache {:system system}))

(defn refresh-kms-cache-job
  "The singleton job that refreshes the KMS cache. The keywords are infrequently updated by the
  GCMD team. They update the CSV file which we read from every 6 hours. I arbitrarily chose 2 hours
  so that we are never more than 8 hours from the time a keyword is updated."
  [job-key]
  {:job-type RefreshKmsCacheJob
   :job-key job-key
   :interval 7200})
