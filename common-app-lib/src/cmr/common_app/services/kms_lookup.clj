(ns cmr.common-app.services.kms-lookup
  "Functions to support fast lookup of KMS keywords. The kms-index structure is a map with keys for
  each of the different KMS keywords In addition the kms-index has 3 additional 'index' keys to
  support fast retrieval. For example:
  {:providers [{:level-0 \"ACADEMIC\" :uuid \"abc\" ...}]
   :science-keywords [...]
   :platforms [...]
   ...
   :short-name-index {:platforms {\"TERRA\" {:category \"SATELLITES\" :short-name \"TERRA\" :uuid \"abc\"...}
								                ...}
                      :instruments {\"ATM\" {...}}}
   :umm-c-index {:spatial-keywords {{:category \"CONTINENT\" :subregion1 \"WESTERN AFRICA\"} ;; key
                                    {:category \"CONTINENT\" :subregion1 \"WESTERN AFRICA\" :uuid \"123\"} ;; value
                                   ...}
                 :science-keywords ...}
  :locations-index {\"WESTERN AFRICA\" {:category \"CONTINENT\" :type \"AFRICA\"
                                        :subregion-1 \"WESTERN AFRICA\" :uuid \"123\"}
                    \"CHAD\" {:category \"CONTINENT\" :type \"AFRICA\" :subregion-1 \"WESTERN AFRICA\"
                              :subregion-2 \"CHAD\" :uuid \"456\"}}}"
  (:require
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as csk-extras]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.log :refer [error]]
   [cmr.common.redis-log-util :as rl-util]
   [cmr.common.util :as util]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis-hash-cache :as rhcache])
  (:import #_{:clj-kondo/ignore [:unused-import]}
           (clojure.lang ExceptionInfo)))

(def kms-short-name-cache-key
  "The key used to store the data generated from KMS into a short name index cache
  in the system hash cache map for fast lookups."
  :kms-short-name-index)

(def kms-umm-c-cache-key
  "The key used to store the data generated from KMS into a umm-c index cache
  in the system hash cache map for fast lookups."
  :kms-umm-c-index)

(def kms-location-cache-key
  "The key used to store the data generated from KMS into a locations index cache
  in the system hash cache map for fast lookups."
  :kms-location-index)

(def kms-measurement-cache-key
  "The key used to store the data generated from KMS into a measurment index cache
  in the system hash cache map for fast lookups."
  :kms-measurement-index)

(def kms-cache-ttl
  "Time To Live value for KMS caches. nil means never expire."
  nil)

(defn create-kms-short-name-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-short-name-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(defn create-kms-umm-c-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-umm-c-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(defn create-kms-location-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-location-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(defn create-kms-measurement-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-measurement-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(def kms-scheme->fields-for-umm-c-lookup
  "Maps the KMS keyword scheme to the list of fields that should be matched when
  comparing fields between KMS and UMM-C, UMM-G, UMM-S, UMM-T, or UMM-Var."
  {:science-keywords [:category :topic :term :variable-level-1 :variable-level-2 :variable-level-3]
   :platforms [:category :short-name :long-name] ;; :basis and :sub-category are not in metadata
   :instruments [:short-name :long-name]
   :projects [:short-name :long-name]
   :providers [:short-name]
   :spatial-keywords [:category :type :subregion-1 :subregion-2 :subregion-3]
   :concepts [:short-name]
   :iso-topic-categories [:iso-topic-category]
   :granule-data-format [:short-name]
   :mime-type [:mime-type]
   :related-urls [:url-content-type :type :subtype]
   :processing-levels [:processing-level]
   :temporal-keywords [:temporal-resolution-range]})

(def kms-scheme->remove-fields-for-umm-c-lookup
  "Remove these fields from the umm-c-lookup cache as its in its own cache."
  [:measurement-name])

(def kms-scheme->fields-for-umm-var-lookup
  "Maps the KMS keyword scheme to the list of fields that should be matched when comparing fields
  between UMM-Var and KMS."
  {:measurement-name [:context-medium :object :quantity]})

(defn- normalize-for-lookup
  "Takes a map (either a UMM-C keyword or a KMS keyword) or string m,
  and a list of fields from the map which we want to use for comparison.
  When m is a map we return a map containing only the keys we are interested
  in and with all values in lower case. When m is not a map, takes the first
  field from fields-to-compare as key and returns map of the form:
  {
    field-to-compare m
  }"
  [m fields-to-compare]
  (if (map? m)
    (->> (select-keys m fields-to-compare)
         util/remove-nil-keys
         (util/map-values string/lower-case))
    {(first fields-to-compare) (string/lower-case m)}))

(defn- generate-lookup-by-umm-c-map
  "Takes a GCMD keywords map and stores them in a way for faster lookup when trying to find
  a location keyword that matches a UMM-C collection with a location keyword in KMS. For each KMS
  keyword there are a set of fields which are used to match against the same fields in UMM-C. We
  store the GCMD keywords in a map with a hash of the map as the key to that map for fast lookup."
  [gcmd-keywords-map]
  (into {}
        (map (fn [[keyword-scheme keyword-maps]]
               [keyword-scheme (let [fields (get kms-scheme->fields-for-umm-c-lookup
                                                 keyword-scheme)]
                                 (into {}
                                       (map (fn [keyword-map]
                                              [(normalize-for-lookup keyword-map fields)
                                               keyword-map])
                                            keyword-maps)))])
             (apply dissoc gcmd-keywords-map kms-scheme->remove-fields-for-umm-c-lookup))))

(def keywords-to-lookup-by-short-name
  "Set of KMS keywords that we need to be able to lookup by short name."
  #{:providers :platforms :instruments})

(defn generate-lookup-by-short-name-map
  "Create a map with the leaf node identifier in all lower case as keys to the full hierarchy
   for that entry. GCMD ensures that no two leaf fields can be the same when compared in a case
   insensitive manner."
  [gcmd-keywords-map]
  (into {}
        (map (fn [[keyword-scheme keyword-maps]]
               (let [maps-by-short-name (into {}
                                              (for [entry keyword-maps]
                                                [(string/lower-case (:short-name entry)) entry]))]
                 [keyword-scheme maps-by-short-name]))
             (select-keys gcmd-keywords-map keywords-to-lookup-by-short-name))))

(def duplicate-keywords
  "Lookup table to account for any duplicate keywords. Will choose the preferred value.
  Common key is :uuid which is a field in the location-keyword map. "
   ;; Choose Black Sea here because it's more associated with Eastern Europe than Western Asia.
  {"BLACK SEA" {:category "CONTINENT" :type "EUROPE" :subregion-1 "EASTERN EUROPE"
                :subregion-2 "BLACK SEA" :uuid "afbc0a01-742e-49da-939e-3eaa3cf431b0"}
   ;; Choose a more specific SPACE element because the general SPACE is too broad and top-level.
   "SPACE" {:category "SPACE" :type "EARTH MAGNETIC FIELD" :subregion-1 "SPACE"
            :uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"}
   ;; Choose Georgia the country instead of Georgia the US State.
   "GEORGIA" {:category "CONTINENT" :type "ASIA" :subregion-1 "WESTERN ASIA" :subregion-2 "GEORGIA"
              :uuid "d79e134c-a4d0-44f2-9706-cad2b59de992"}})

(defn- generate-lookup-by-location-map
  "Create a map every location string as keys to the full hierarchy for that entry. If there are
  multiple strings, the one with the fewest hierarchical keys is chosen. For example 'OCEAN' will
  map to the keyword {:category \"OCEAN\"} rather than {:category \"OCEAN\" :type \"ARCTIC OCEAN\"}."
  [gcmd-keywords-map]
  (let [location-keywords (->> gcmd-keywords-map :spatial-keywords (sort-by count) reverse)
        location-keywords (into {}
                            (for [location-keyword-map location-keywords
                                  location (vals (dissoc location-keyword-map :uuid))]
                              [(string/upper-case location) location-keyword-map]))]
    (merge location-keywords duplicate-keywords)))

(defn generate-lookup-by-measurement-name
  "Create a map with the measurement field values defined in UMM-Var map to the KMS keywords."
  [gcmd-keywords-map]
  (into {}
        (let [keyword-scheme :measurement-name
              keyword-maps (keyword-scheme gcmd-keywords-map)]
          [[keyword-scheme (let [fields (get kms-scheme->fields-for-umm-var-lookup
                                             keyword-scheme)]
                             (into {}
                                   (map (fn [keyword-map]
                                          [(normalize-for-lookup keyword-map fields)
                                           keyword-map])
                                        keyword-maps)))]])))

;; Candidate to make private as this function is currently used very little outside of the
;; common_app package (kms_lookup.clj) and several tests. Try to not call this function in any
;; actual code to limit how many apps directly manage KMS cache.
(defn create-kms-index!
  "Creates the KMS index structure to be used for fast lookups and stores these values in
   redis. Calling this function will CHANGE an external resource."
  [context kms-keywords-map]
  (let [short-name-lookup-map (generate-lookup-by-short-name-map kms-keywords-map)
        umm-c-lookup-map (generate-lookup-by-umm-c-map kms-keywords-map)
        location-lookup-map (generate-lookup-by-location-map kms-keywords-map)
        measurement-lookup-map (generate-lookup-by-measurement-name kms-keywords-map)
        short-name-cache (hash-cache/context->cache context kms-short-name-cache-key)
        umm-c-cache (hash-cache/context->cache context kms-umm-c-cache-key)
        location-cache (hash-cache/context->cache context kms-location-cache-key)
        measurement-cache (hash-cache/context->cache context kms-measurement-cache-key)
        _ (rl-util/log-refresh-start (format "%s %s %s %s"
                                             kms-short-name-cache-key
                                             kms-umm-c-cache-key
                                             kms-location-cache-key
                                             kms-measurement-cache-key))
        [tm _] (util/time-execution (hash-cache/set-values short-name-cache kms-short-name-cache-key short-name-lookup-map))
        _ (rl-util/log-redis-write-complete "create-kms-index" kms-short-name-cache-key tm)
        [tm _] (util/time-execution (hash-cache/set-values umm-c-cache kms-umm-c-cache-key umm-c-lookup-map))
        _ (rl-util/log-redis-write-complete "create-kms-index" kms-umm-c-cache-key tm)
        [tm _] (util/time-execution (hash-cache/set-values location-cache kms-location-cache-key location-lookup-map))
        _ (rl-util/log-redis-write-complete "create-kms-index" kms-location-cache-key tm)
        [tm _] (util/time-execution (hash-cache/set-values measurement-cache kms-measurement-cache-key measurement-lookup-map))
        _ (rl-util/log-redis-write-complete "create-kms-index" kms-measurement-cache-key tm)]
    kms-keywords-map))

(defn lookup-by-short-name
  "Takes a kms-index, the keyword scheme, and a short name and returns the full KMS hierarchy for
  that short name. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context keyword-scheme short-name]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [short-name-cache (hash-cache/context->cache context kms-short-name-cache-key)
            [tm keywords] (util/time-execution (hash-cache/get-value short-name-cache kms-short-name-cache-key keyword-scheme))
            _ (rl-util/log-redis-read-complete "lookup-by-short-name" kms-short-name-cache-key tm)]
        (get keywords (util/safe-lowercase short-name))))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-by-short-name found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn lookup-by-location-string
  "Takes a kms-index and a location string and returns the full KMS hierarchy for that location
  string. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context location-string]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [location-cache (hash-cache/context->cache context kms-location-cache-key)
            [tm keywords] (util/time-execution (hash-cache/get-value location-cache kms-location-cache-key (string/upper-case location-string)))
            _ (rl-util/log-redis-read-complete "lookup-by-location-string" kms-location-cache-key tm)]
        keywords))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-by-location-string found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn- remove-long-name-from-kms-index
  "Removes long-name from the umm-c-index keys in order to prevent validation when
   long-name is not present in the umm-c-keyword.  We only want to validate long-name if it is not nil."
  [kms-index-value]
  (into {}
    (for [[k v] kms-index-value]
      [(dissoc k :long-name) v])))

(defn lookup-by-umm-c-keyword-data-format
  "Takes a keyword as represented in the UMM concepts as a map or as an individual string
  and returns the KMS keyword map as its stored in the cache. Returns nil if a keyword is not found.
  Comparison is made case insensitively."
  [context keyword-scheme umm-c-keyword]
  (try
    (let [umm-c-keyword (csk-extras/transform-keys csk/->kebab-case umm-c-keyword)
          format-map (if (string? umm-c-keyword)
                       {:short-name umm-c-keyword}
                       {:short-name (:format umm-c-keyword)})
          comparison-map (normalize-for-lookup format-map (kms-scheme->fields-for-umm-c-lookup
                                                            keyword-scheme))
          umm-c-cache (hash-cache/context->cache context kms-umm-c-cache-key)
          [tm value] (util/time-execution (hash-cache/get-value umm-c-cache kms-umm-c-cache-key keyword-scheme))
          _ (rl-util/log-redis-read-complete "lookup-by-umm-c-keyword-data-format" kms-umm-c-cache-key tm)]
      (get-in value [comparison-map]))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-by-umm-c-keyword-data-format found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn lookup-by-umm-c-keyword-platforms
  "Takes a keyword as represented in the UMM concepts as a map and returns the KMS keyword map
  as its stored in the cache. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context keyword-scheme umm-c-keyword]
  (try
    (let [umm-c-keyword (csk-extras/transform-keys csk/->kebab-case umm-c-keyword)
          ;; CMR-3696 This is needed to compare the keyword category, which is mapped
          ;; to the UMM Platform Type field.  This will avoid complications with facets.
          umm-c-keyword (set/rename-keys umm-c-keyword {:type :category})
          comparison-map (normalize-for-lookup umm-c-keyword (kms-scheme->fields-for-umm-c-lookup keyword-scheme))
          umm-c-cache (hash-cache/context->cache context kms-umm-c-cache-key)
          [tm value] (util/time-execution (hash-cache/get-value umm-c-cache kms-umm-c-cache-key keyword-scheme))
          _ (rl-util/log-redis-read-complete "lookup-by-umm-c-keyword-platforms" kms-umm-c-cache-key tm)]
      (if (get umm-c-keyword :long-name)
        ;; Check both longname and shortname
        (get-in value [comparison-map])
        ;; Check just shortname
        (-> value
            (remove-long-name-from-kms-index)
            (get comparison-map))))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-by-umm-c-keyword-platforms found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn lookup-by-umm-c-keyword
  "Takes a keyword as represented in UMM concepts as a map and returns the KMS keyword as it exists
   in the cache. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context keyword-scheme umm-c-keyword]
  (try
    (when-not (:ignore-kms-keywords context)
      (case keyword-scheme
        :platforms (lookup-by-umm-c-keyword-platforms context keyword-scheme umm-c-keyword)
        :granule-data-format (lookup-by-umm-c-keyword-data-format context keyword-scheme umm-c-keyword)
        ;; default
        (let [umm-c-keyword (csk-extras/transform-keys csk/->kebab-case umm-c-keyword)
              comparison-map (normalize-for-lookup umm-c-keyword
                                                   (kms-scheme->fields-for-umm-c-lookup keyword-scheme))
              umm-c-cache (hash-cache/context->cache context kms-umm-c-cache-key)
              [tm value] (util/time-execution (hash-cache/get-value umm-c-cache kms-umm-c-cache-key keyword-scheme))
              _ (rl-util/log-redis-read-complete "lookup-by-umm-c-keyword" kms-umm-c-cache-key tm)]
          (get-in value [comparison-map]))))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-by-umm-c-keyword found redis carmine exception. Will return nil result." e)
        (throw e)))))

;; Note, this still may not work with other tests
(defn lookup-by-measurement
  "Takes a keyword as represented in UMM concepts as a map. Returns a map of invalid measurement
   keywords which are then returned as a message to the operator. Comparison is made case
   insensitively."
  [context value]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [measurement-cache (hash-cache/context->cache context kms-measurement-cache-key)
            [tm measurement-index] (util/time-execution (hash-cache/get-value
                                                         measurement-cache
                                                         kms-measurement-cache-key
                                                         :measurement-name))
            _ (rl-util/log-redis-read-complete "lookup-by-measurement" kms-measurement-cache-key tm)
            {:keys [MeasurementContextMedium MeasurementObject MeasurementQuantities]} value
            ;; build either a 3 or 2 item map depending on if we have Quantities
            measurements-to-find (if (seq MeasurementQuantities)
                           (map (fn [quantity]
                                  {:context-medium MeasurementContextMedium
                                   :object MeasurementObject
                                   :quantity (:Value quantity)})
                                MeasurementQuantities)
                           [{:context-medium MeasurementContextMedium
                             :object MeasurementObject}])]
        ;; remove will always return something when dealing with a nil keyword cache which we do not
        ;; what if there is no cache at all. When we have a cache, then do the removes
        (when measurement-index
          (seq (remove #(get measurement-index %) measurements-to-find)))))
    (catch Exception e
      (if (string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-by-measurement found redis carmine exception. Will return nil result." e)
        (throw e)))))
