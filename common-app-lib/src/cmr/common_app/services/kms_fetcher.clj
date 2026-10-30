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
   [camel-snake-kebab.core :as csk]
   [clojure.string :as string]
   [cmr.common-app.services.kms-lookup :as kms-lookup]
   [cmr.common.cache :as cache]
   [cmr.common.log :refer [report error info]]
   [cmr.common.redis-log-util :as rl-util]
   [cmr.common.services.errors :as srv-err]
   [cmr.common.util :as util]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.transmit.kms :as transmit-kms])
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

(def kms-cache-ttl
  "The Time-To-Live value, use nil to never expire."
  nil)

;; Called by system.clj across several applications
(defn create-kms-cache
  "Used to create the cache that will be used for caching KMS keywords. All applications caching
  KMS keywords should use the same fallback cache to ensure functionality even if GCMD KMS becomes
  unavailable."
  []
  (redis-cache/create-redis-cache {:keys-to-track [kms-cache-key]
                                   :read-connection (redis-config/redis-read-conn-opts)
                                   :primary-connection (redis-config/redis-conn-opts)
                                   :ttl kms-cache-ttl}))

(defn- parse-concept-schemes
  "Parses and validates the concept_scheme query param. Accepts a comma-separated string or a
  vector of strings. Returns a seq of keyword scheme keys valid for use with transmit-kms."
  [param]
  (let [schemes (->> (util/seqify param)
                     (mapcat #(string/split % #","))
                     (map string/trim)
                     (remove string/blank?)
                     (map csk/->kebab-case-keyword)
                     (map transmit-kms/translate-keyword-scheme-to-gcmd)
                     distinct)
        valid-schemes  (set (keys transmit-kms/keyword-scheme->field-names))
        invalid (remove valid-schemes schemes)]
    (when (empty? schemes)
      (srv-err/throw-service-error
       :bad-request "concept_scheme param is required when provided and must not be blank."))
    (when (seq invalid)
      (let [valid-names (string/join ", " (sort (map #(-> % name csk/->snake_case) valid-schemes)))]
        (srv-err/throw-service-error
         :bad-request
         (format "Invalid concept_scheme value(s): [%s]. Valid schemes are: %s."
                 (string/join ", " (map name invalid))
                 valid-names))))
    schemes))

;; Called indirectly by bootstrap. This may call KMS service directly through transmit.
(defn- fetch-gcmd-keywords-map
  "Fetches keyword data from KMS for the given schemes and merges with cached-gcmd-keywords-map.
  Returns the merged keywords map or nil on Carmine error."
  [context keyword-schemes cached-gcmd-keywords-map]
  (try
    (let [fetched (->> (for [keyword-scheme keyword-schemes]
                         [keyword-scheme (transmit-kms/get-keywords-for-keyword-scheme context keyword-scheme)])
                       (into {})
                       util/remove-nil-keys)
          data (merge cached-gcmd-keywords-map fetched)]
      (when (seq data)
        (info "refresh-kms-cache: get-kms-index: have parsed keyword data, about to create cache.")
        data))
    (catch Exception e
      (if (string/includes? (ex-message e) "Carmine connection error")
        (error "refresh-kms-cache: fetch-gcmd-keywords-map found redis carmine exception. Will return nil result." e)
        (throw e)))))

;; This is only called by cmr.search.api.keywords (and tests). May be private in the future
(defn get-kms-index
  "Retrieves the GCMD keywords map from the cache."
  [context]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [cache (cache/context->cache context kms-cache-key)
            _ (rl-util/log-redis-reading-start kms-cache-key)
            [t1 kms-index] (util/time-execution (cache/get-value cache kms-cache-key))]
        ;; Do not worry about finding a value, assume cache is correct and return what is found,
        ;; an external process will sync the KMS cache
        (rl-util/log-redis-read-complete "get-kms-index" kms-cache-key t1)
        kms-index))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "refresh-kms-cache: get-kms-index found redis carmine exception. Will return nil result." e)
        (throw e)))))

;; This is only called by bootstrap and should not be used out side of that app
(defn- resolve-refresh-schemes
  "Determines which KMS keyword schemes to refresh and the already cached gcmd keyword map to merge them into.

   When concept-scheme-param is provided, returns the parsed schemes and the existing cache
   contents (or an empty map) so the refreshed schemes are merged into it. When nil, returns
   all known schemes and an empty cached gcmd keywords map so the entire cache is rebuilt.

   Returns a vector of [keyword-schemes cached-gcmd-keywords-map]."
  [cache concept-scheme-param]
  (if (some? concept-scheme-param)
    (let [schemes (parse-concept-schemes concept-scheme-param)]
      (info (str "refresh-kms-cache has been requested for schemes: "
                 (string/join ", " (map name schemes))))
      [schemes (or (cache/get-value cache kms-cache-key) {})])
    (do
      (info "refresh-kms-cache has been requested")
      [(keys transmit-kms/keyword-scheme->field-names) {}])))

(defn refresh-kms-cache
  "Bootstrap calls for a refresh of the KMS keywords stored in the cache. This should be called
   from a background job on a timer to keep the cache fresh.

   When concept-scheme-param is provided it must be a comma-separated string (or vector) of CMR
   keyword scheme names. Only those schemes are fetched from KMS and merged into the existing cache.
   When nil, all schemes are refreshed."
  ([context]
   (refresh-kms-cache context nil))
  ([context concept-scheme-param]
   (rl-util/log-refresh-start kms-cache-key)
   (let [cache (cache/context->cache context kms-cache-key)
         [keyword-schemes cached-gcmd-keywords-map] (resolve-refresh-schemes cache concept-scheme-param)]
     (try
       (let [[t1 gcmd-keywords-map] (util/time-execution
                                     (fetch-gcmd-keywords-map context keyword-schemes cached-gcmd-keywords-map))
             _ (when gcmd-keywords-map
                 ;; Refresh cache for either specific scheme or all schemes
                 (if (some? concept-scheme-param)
                   (kms-lookup/create-kms-index context gcmd-keywords-map keyword-schemes)
                   (kms-lookup/create-kms-index context gcmd-keywords-map)))
             gcmd-keywords-map (cmr.common.util/remove-empty-maps gcmd-keywords-map)
             [t2 _] (util/time-execution (cache/set-value cache kms-cache-key gcmd-keywords-map))]
         (rl-util/log-redis-data-write-complete "refresh-kms-cache" kms-cache-key t1 t2))
       (catch Exception e
         ;; refresh-kms-cache is being called by a ring route so return a standard CMR error
         (cmr.common.services.errors/throw-service-error
          :bad-request
          (.getLocalizedMessage e)))))))
