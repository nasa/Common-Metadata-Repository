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
   [clojure.string :as string]
   [cmr.common-app.services.kms-lookup :as kms-lookup]
   [cmr.common.cache :as cache]
   [cmr.common.log :refer [error]]
   [cmr.common.redis-log-util :as rl-util]
   [cmr.common.util :as util]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.transmit.kms :as kms])
  (:import #_{:clj-kondo/ignore [:unused-import]}
           (clojure.lang ExceptionInfo)))

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
  (redis-cache/create-redis-cache {:keys-to-track [kms-cache-key]
                                   :read-connection (redis-config/redis-read-conn-opts)
                                   :primary-connection (redis-config/redis-conn-opts)}))

(defn- fetch-gcmd-keywords-map
  "Calls GCMD KMS endpoints to retrieve the keywords. Response is a map structured in the same way
  as used in the KMS cache."
  [context]
  (try
    (let [kms-cache (cache/context->cache context kms-cache-key)
          _ (rl-util/log-redis-reading-start "kms-fetcher.fetch-gcmd-keywords-map" kms-cache-key)
          [t1 kms-cache-value] (util/time-execution (cache/get-value kms-cache kms-cache-key))]
      (rl-util/log-redis-read-complete "fetch-gcmd-keywords-map" kms-cache-key t1)
      (kms-lookup/create-kms-index
        context
        (into {}
              (for [keyword-scheme (keys kms/keyword-scheme->field-names)]
                ;; if the keyword-scheme-value is nil that means we could not get the KMS keywords
                ;; in this case use the cached value instead so that we don't wipe out the cache.
                (if-let [keyword-scheme-value (kms/get-keywords-for-keyword-scheme context keyword-scheme)]
                  [keyword-scheme keyword-scheme-value]
                  [keyword-scheme (get kms-cache-value keyword-scheme)])))))
    (catch Exception e
      (if (string/includes? (ex-message e) "Carmine connection error")
        (error "fetch-gcmd-keywords-map found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn get-kms-index
  "Retrieves the GCMD keywords map from the cache."
  [context]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [cache (cache/context->cache context kms-cache-key)
            _ (rl-util/log-redis-reading-start kms-cache-key)
            [t1 kms-index] (util/time-execution (cache/get-value cache kms-cache-key))]
        (if kms-index
          (do
            (rl-util/log-redis-read-complete "get-kms-index" kms-cache-key t1)
            kms-index)
          (let [[t1 kms-index] (util/time-execution (fetch-gcmd-keywords-map context))
                [t2 _] (util/time-execution (cache/set-value cache kms-cache-key kms-index))]
            (rl-util/log-data-gathering-stats "get-kms-index" kms-cache-key t1)
            (rl-util/log-redis-write-complete "get-kms-index" kms-cache-key t2)
            kms-index))))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "get-kms-index found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn refresh-kms-cache
  "Refreshes the KMS keywords stored in the cache. This should be called from a background job on a
  timer to keep the cache fresh."
  [context]
  (rl-util/log-refresh-start kms-cache-key)
  (let [cache (cache/context->cache context kms-cache-key)
        [t1 gcmd-keywords-map] (util/time-execution (fetch-gcmd-keywords-map context))
        [t2 _] (util/time-execution (cache/set-value cache kms-cache-key gcmd-keywords-map))]
    (rl-util/log-redis-data-write-complete "refresh-kms-cache" kms-cache-key t1 t2)))
