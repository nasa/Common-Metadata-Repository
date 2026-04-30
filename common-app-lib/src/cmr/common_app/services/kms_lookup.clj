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

(def kms-projects-cache-key
  "The key used to store the data generated from KMS into a project index cache
  in the system hash cache map for fast lookups."
  :kms-project-index)

(def kms-umm-c-cache-key
  "The key used to store the data generated from KMS into a umm-c index cache
  in the system hash cache map for fast lookups."
  :kms-umm-c-index)

(def kms-location-cache-key
  "The key used to store the data generated from KMS into a locations index cache
  in the system hash cache map for fast lookups."
  :kms-location-index)

(def kms-measurement-cache-key
  "The key used to store the data generated from KMS into a measurement index cache
  in the system hash cache map for fast lookups."
  :kms-measurement-index)

(def kms-processing-level-cache-key
  "The key used to store the data generated from KMS into a processing level index cache
  in the system hash cache map for fast lookups."
  :kms-processing-level-index)

(def kms-science-keywords-cache-key
  "The key used to store the data generated from KMS into a science keywords index cache
  in the system hash cache map for fast lookups."
  :kms-science-keywords-index)

(def kms-platforms-cache-key
  "The key used to store the data generated from KMS into a platforms index cache
  in the system hash cache map for fast lookups."
  :kms-platforms-index)

(def kms-instruments-cache-key
  "The key used to store the data generated from KMS into an instruments index cache
  in the system hash cache map for fast lookups."
  :kms-instruments-index)

(def kms-providers-cache-key
  "The key used to store the data generated from KMS into a providers index cache
  in the system hash cache map for fast lookups."
  :kms-providers-index)

(def kms-spatial-keywords-cache-key
  "The key used to store the data generated from KMS into a spatial keywords index cache
  in the system hash cache map for fast lookups."
  :kms-spatial-keywords-index)

(def kms-concepts-cache-key
  "The key used to store the data generated from KMS into a concepts index cache
  in the system hash cache map for fast lookups."
  :kms-concepts-index)

(def kms-iso-topic-categories-cache-key
  "The key used to store the data generated from KMS into an iso topic categories index cache
  in the system hash cache map for fast lookups."
  :kms-iso-topic-categories-index)

(def kms-granule-data-format-cache-key
  "The key used to store the data generated from KMS into a granule data format index cache
  in the system hash cache map for fast lookups."
  :kms-granule-data-format-index)

(def kms-mime-type-cache-key
  "The key used to store the data generated from KMS into a mime type index cache
  in the system hash cache map for fast lookups."
  :kms-mime-type-index)

(def kms-related-urls-cache-key
  "The key used to store the data generated from KMS into a related urls index cache
  in the system hash cache map for fast lookups."
  :kms-related-urls-index)

(def kms-temporal-keywords-cache-key
  "The key used to store the data generated from KMS into a temporal keywords index cache
  in the system hash cache map for fast lookups."
  :kms-temporal-keywords-index)

(def kms-cache-ttl
  "Time To Live value for KMS caches. nil means never expire."
  nil)

;; what is needed into the value
(defn create-kms-short-name-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-short-name-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))
(defn create-kms-project-uuid-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-projects-cache-key]
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

(defn create-kms-processing-level-uuid-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-processing-level-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(defn create-kms-science-keywords-uuid-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-science-keywords-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(defn create-kms-platforms-uuid-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-platforms-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(defn create-kms-instruments-uuid-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-instruments-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(defn create-kms-providers-uuid-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-providers-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(defn create-kms-spatial-keywords-uuid-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-spatial-keywords-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(defn create-kms-concepts-uuid-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-concepts-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(defn create-kms-iso-topic-categories-uuid-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-iso-topic-categories-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(defn create-kms-granule-data-format-uuid-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-granule-data-format-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(defn create-kms-mime-type-uuid-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-mime-type-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(defn create-kms-related-urls-uuid-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-related-urls-cache-key]
                                    :read-connection (redis-config/redis-read-conn-opts)
                                    :primary-connection (redis-config/redis-conn-opts)
                                    :ttl kms-cache-ttl}))

(defn create-kms-temporal-keywords-uuid-cache
  "Creates an instance of the cache."
  []
  (rhcache/create-redis-hash-cache {:keys-to-track [kms-temporal-keywords-cache-key]
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
  a umm-c keyword that matches a UMM-C collection with a umm-c keyword in KMS. For each KMS
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

(defn generate-lookup-by-project-name-map
  "Create a map with the project short name in all lower case as keys to the UUID for that project."
  [gcmd-keywords-map]
  (into {}
        (for [entry (:projects gcmd-keywords-map)
              :when (:uuid entry)]
          [(string/lower-case (:short-name entry)) (:uuid entry)])))

(defn generate-lookup-by-processing-level-map
  "Create a map with the processing level in all lower case as keys to the UUID for that processing level."
  [gcmd-keywords-map]
  (into {}
        (for [entry (:processing-levels gcmd-keywords-map)
              :when (:uuid entry)]
          [(string/lower-case (:processing-level entry)) (:uuid entry)])))

(defn generate-lookup-by-platforms-name-map
  "Create a map with the platform short name in all lower case as keys to the UUID for that platform."
  [gcmd-keywords-map]
  (into {}
        (for [entry (:platforms gcmd-keywords-map)
              :when (:uuid entry)]
          [(string/lower-case (:short-name entry)) (:uuid entry)])))

(defn generate-lookup-by-instruments-name-map
  "Create a map with the instrument short name in all lower case as keys to the UUID for that instrument."
  [gcmd-keywords-map]
  (into {}
        (for [entry (:instruments gcmd-keywords-map)
              :when (:uuid entry)]
          [(string/lower-case (:short-name entry)) (:uuid entry)])))

(defn generate-lookup-by-providers-name-map
  "Create a map with the provider short name in all lower case as keys to the UUID for that provider."
  [gcmd-keywords-map]
  (into {}
        (for [entry (:providers gcmd-keywords-map)
              :when (:uuid entry)]
          [(string/lower-case (:short-name entry)) (:uuid entry)])))

(defn generate-lookup-by-concepts-name-map
  "Create a map with the concept short name in all lower case as keys to the UUID for that concept."
  [gcmd-keywords-map]
  (into {}
        (for [entry (:concepts gcmd-keywords-map)
              :when (:uuid entry)]
          [(string/lower-case (:short-name entry)) (:uuid entry)])))

(defn generate-lookup-by-granule-data-format-name-map
  "Create a map with the granule data format short name in all lower case as keys to the UUID for that granule data format."
  [gcmd-keywords-map]
  (into {}
        (for [entry (:granule-data-format gcmd-keywords-map)
              :when (:uuid entry)]
          [(string/lower-case (:short-name entry)) (:uuid entry)])))

(defn generate-lookup-by-iso-topic-categories-name-map
  "Create a map with the iso topic category in all lower case as keys to the UUID for that iso topic category."
  [gcmd-keywords-map]
  (into {}
        (for [entry (:iso-topic-categories gcmd-keywords-map)
              :when (:uuid entry)]
          [(string/lower-case (:iso-topic-category entry)) (:uuid entry)])))

(defn generate-lookup-by-mime-type-name-map
  "Create a map with the mime type in all lower case as keys to the UUID for that mime type."
  [gcmd-keywords-map]
  (into {}
        (for [entry (:mime-type gcmd-keywords-map)
              :when (:uuid entry)]
          [(string/lower-case (:mime-type entry)) (:uuid entry)])))

(defn generate-lookup-by-temporal-keywords-name-map
  "Create a map with the temporal keyword in all lower case as keys to the UUID for that temporal keyword."
  [gcmd-keywords-map]
  (into {}
        (for [entry (:temporal-keywords gcmd-keywords-map)
              :when (:uuid entry)]
          [(string/lower-case (:temporal-resolution-range entry)) (:uuid entry)])))

(defn- generate-lookup-by-comparison-map
  "Takes a GCMD keywords map, a keyword scheme, and a list of fields to compare.
  Returns a map from comparison map -> UUID for the specified keyword scheme."
  [gcmd-keywords-map keyword-scheme]
  (let [keyword-maps (keyword-scheme gcmd-keywords-map)
        fields (get kms-scheme->fields-for-umm-c-lookup keyword-scheme)]
    (into {}
          (for [entry keyword-maps
                :when (:uuid entry)]
            [(normalize-for-lookup entry fields) (:uuid entry)]))))

(defn generate-lookup-by-science-keywords-map
  "Create a map with the science keyword comparison map as keys to the UUID for that science keyword."
  [gcmd-keywords-map]
  (generate-lookup-by-comparison-map gcmd-keywords-map :science-keywords))

(defn generate-lookup-by-spatial-keywords-map
  "Create a map with the spatial keyword comparison map as keys to the UUID for that spatial keyword."
  [gcmd-keywords-map]
  (generate-lookup-by-comparison-map gcmd-keywords-map :spatial-keywords))

(defn generate-lookup-by-related-urls-map
  "Create a map with the related url comparison map as keys to the UUID for that related url."
  [gcmd-keywords-map]
  (generate-lookup-by-comparison-map gcmd-keywords-map :related-urls))

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
(defn create-kms-index
  "Creates the KMS index structure to be used for fast lookups and stores these values in
   redis. Calling this function will CHANGE an external resource."
  [context kms-keywords-map]
  (let [short-name-lookup-map (generate-lookup-by-short-name-map kms-keywords-map)
        project-uuid-lookup-map (generate-lookup-by-project-name-map kms-keywords-map)
        processing-level-uuid-lookup-map (generate-lookup-by-processing-level-map kms-keywords-map)
        umm-c-lookup-map (generate-lookup-by-umm-c-map kms-keywords-map)
        location-lookup-map (generate-lookup-by-location-map kms-keywords-map)
        measurement-lookup-map (generate-lookup-by-measurement-name kms-keywords-map)
        science-keywords-uuid-lookup-map (generate-lookup-by-science-keywords-map kms-keywords-map)
        platforms-uuid-lookup-map (generate-lookup-by-platforms-name-map kms-keywords-map)
        instruments-uuid-lookup-map (generate-lookup-by-instruments-name-map kms-keywords-map)
        providers-uuid-lookup-map (generate-lookup-by-providers-name-map kms-keywords-map)
        spatial-keywords-uuid-lookup-map (generate-lookup-by-spatial-keywords-map kms-keywords-map)
        concepts-uuid-lookup-map (generate-lookup-by-concepts-name-map kms-keywords-map)
        iso-topic-categories-uuid-lookup-map (generate-lookup-by-iso-topic-categories-name-map kms-keywords-map)
        granule-data-format-uuid-lookup-map (generate-lookup-by-granule-data-format-name-map kms-keywords-map)
        mime-type-uuid-lookup-map (generate-lookup-by-mime-type-name-map kms-keywords-map)
        related-urls-uuid-lookup-map (generate-lookup-by-related-urls-map kms-keywords-map)
        temporal-keywords-uuid-lookup-map (generate-lookup-by-temporal-keywords-name-map kms-keywords-map)
        project-cache (hash-cache/context->cache context kms-projects-cache-key)
        processing-level-cache (hash-cache/context->cache context kms-processing-level-cache-key)
        short-name-cache (hash-cache/context->cache context kms-short-name-cache-key)
        umm-c-cache (hash-cache/context->cache context kms-umm-c-cache-key)
        location-cache (hash-cache/context->cache context kms-location-cache-key)
        measurement-cache (hash-cache/context->cache context kms-measurement-cache-key)
        science-keywords-cache (hash-cache/context->cache context kms-science-keywords-cache-key)
        platforms-cache (hash-cache/context->cache context kms-platforms-cache-key)
        instruments-cache (hash-cache/context->cache context kms-instruments-cache-key)
        providers-cache (hash-cache/context->cache context kms-providers-cache-key)
        spatial-keywords-cache (hash-cache/context->cache context kms-spatial-keywords-cache-key)
        concepts-cache (hash-cache/context->cache context kms-concepts-cache-key)
        iso-topic-categories-cache (hash-cache/context->cache context kms-iso-topic-categories-cache-key)
        granule-data-format-cache (hash-cache/context->cache context kms-granule-data-format-cache-key)
        mime-type-cache (hash-cache/context->cache context kms-mime-type-cache-key)
        related-urls-cache (hash-cache/context->cache context kms-related-urls-cache-key)
        temporal-keywords-cache (hash-cache/context->cache context kms-temporal-keywords-cache-key)
        _ (rl-util/log-refresh-start (format "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s"
                                             kms-short-name-cache-key
                                             kms-umm-c-cache-key
                                             kms-location-cache-key
                                             kms-measurement-cache-key
                                             kms-processing-level-cache-key
                                             kms-science-keywords-cache-key
                                             kms-platforms-cache-key
                                             kms-instruments-cache-key
                                             kms-providers-cache-key
                                             kms-spatial-keywords-cache-key
                                             kms-concepts-cache-key
                                             kms-iso-topic-categories-cache-key
                                             kms-granule-data-format-cache-key
                                             kms-mime-type-cache-key
                                             kms-related-urls-cache-key
                                             kms-temporal-keywords-cache-key))]
    ;; Only update caches that exist
    (when-not (empty? short-name-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values short-name-cache kms-short-name-cache-key short-name-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-short-name-cache-key tm)))
    (when-not (empty? project-uuid-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values project-cache kms-projects-cache-key project-uuid-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-projects-cache-key tm)))
    (when-not (empty? processing-level-uuid-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values processing-level-cache kms-processing-level-cache-key processing-level-uuid-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-processing-level-cache-key tm)))
    (when-not (empty? umm-c-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values umm-c-cache kms-umm-c-cache-key umm-c-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-umm-c-cache-key tm)))
    (when-not (empty? location-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values location-cache kms-location-cache-key location-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-location-cache-key tm)))
    (when-not (empty? measurement-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values measurement-cache kms-measurement-cache-key measurement-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-measurement-cache-key tm)))
    (when-not (empty? science-keywords-uuid-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values science-keywords-cache kms-science-keywords-cache-key science-keywords-uuid-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-science-keywords-cache-key tm)))
    (when-not (empty? platforms-uuid-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values platforms-cache kms-platforms-cache-key platforms-uuid-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-platforms-cache-key tm)))
    (when-not (empty? instruments-uuid-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values instruments-cache kms-instruments-cache-key instruments-uuid-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-instruments-cache-key tm)))
    (when-not (empty? providers-uuid-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values providers-cache kms-providers-cache-key providers-uuid-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-providers-cache-key tm)))
    (when-not (empty? spatial-keywords-uuid-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values spatial-keywords-cache kms-spatial-keywords-cache-key spatial-keywords-uuid-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-spatial-keywords-cache-key tm)))
    (when-not (empty? concepts-uuid-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values concepts-cache kms-concepts-cache-key concepts-uuid-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-concepts-cache-key tm)))
    (when-not (empty? iso-topic-categories-uuid-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values iso-topic-categories-cache kms-iso-topic-categories-cache-key iso-topic-categories-uuid-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-iso-topic-categories-cache-key tm)))
    (when-not (empty? granule-data-format-uuid-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values granule-data-format-cache kms-granule-data-format-cache-key granule-data-format-uuid-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-granule-data-format-cache-key tm)))
    (when-not (empty? mime-type-uuid-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values mime-type-cache kms-mime-type-cache-key mime-type-uuid-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-mime-type-cache-key tm)))
    (when-not (empty? related-urls-uuid-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values related-urls-cache kms-related-urls-cache-key related-urls-uuid-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-related-urls-cache-key tm)))
    (when-not (empty? temporal-keywords-uuid-lookup-map)
      (let [[tm _] (util/time-execution (hash-cache/set-values temporal-keywords-cache kms-temporal-keywords-cache-key temporal-keywords-uuid-lookup-map))]
        (rl-util/log-redis-write-complete "create-kms-index" kms-temporal-keywords-cache-key tm)))
    kms-keywords-map))

(defn lookup-project-by-short-name
  "Takes a kms-index and a short name and returns the UUID for
  that short name. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context short-name]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [project-cache (hash-cache/context->cache context kms-projects-cache-key)
            [tm uuid] (util/time-execution (hash-cache/get-value project-cache kms-projects-cache-key (util/safe-lowercase short-name)))]
        (rl-util/log-redis-read-complete "lookup-project-by-short-name" kms-projects-cache-key tm)
        uuid))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-project-by-short-name found redis carmine exception. Will return nil result." e)
        (throw e)))))


(defn lookup-science-keyword-by-map
  "Takes a context and a UMM-C keyword map and returns the UUID for
  that science keyword. Returns nil if a keyword is not found."
  [context umm-c-keyword]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [umm-c-keyword (csk-extras/transform-keys csk/->kebab-case umm-c-keyword)
            comparison-map (normalize-for-lookup umm-c-keyword (kms-scheme->fields-for-umm-c-lookup :science-keywords))
            cache (hash-cache/context->cache context kms-science-keywords-cache-key)
            [tm uuid] (util/time-execution (hash-cache/get-value cache kms-science-keywords-cache-key comparison-map))]
        (rl-util/log-redis-read-complete "lookup-science-keyword-by-map" kms-science-keywords-cache-key tm)
        uuid))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-science-keyword-by-map found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn lookup-spatial-keyword-by-map
  "Takes a context and a UMM-C keyword map and returns the UUID for
  that spatial keyword. Returns nil if a keyword is not found."
  [context umm-c-keyword]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [umm-c-keyword (csk-extras/transform-keys csk/->kebab-case umm-c-keyword)
            comparison-map (normalize-for-lookup umm-c-keyword (kms-scheme->fields-for-umm-c-lookup :spatial-keywords))
            cache (hash-cache/context->cache context kms-spatial-keywords-cache-key)
            [tm uuid] (util/time-execution (hash-cache/get-value cache kms-spatial-keywords-cache-key comparison-map))]
        (rl-util/log-redis-read-complete "lookup-spatial-keyword-by-map" kms-spatial-keywords-cache-key tm)
        uuid))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-spatial-keyword-by-map found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn lookup-related-url-by-map
  "Takes a context and a UMM-C keyword map and returns the UUID for
  that related url. Returns nil if a keyword is not found."
  [context umm-c-keyword]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [umm-c-keyword (csk-extras/transform-keys csk/->kebab-case umm-c-keyword)
            comparison-map (normalize-for-lookup umm-c-keyword (kms-scheme->fields-for-umm-c-lookup :related-urls))
            cache (hash-cache/context->cache context kms-related-urls-cache-key)
            [tm uuid] (util/time-execution (hash-cache/get-value cache kms-related-urls-cache-key comparison-map))]
        (rl-util/log-redis-read-complete "lookup-related-url-by-map" kms-related-urls-cache-key tm)
        uuid))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-related-url-by-map found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn lookup-platform-by-short-name
  "Takes a context and a short name and returns the UUID for
  that platform. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context short-name]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [cache (hash-cache/context->cache context kms-platforms-cache-key)
            [tm uuid] (util/time-execution (hash-cache/get-value cache kms-platforms-cache-key (util/safe-lowercase short-name)))]
        (rl-util/log-redis-read-complete "lookup-platform-by-short-name" kms-platforms-cache-key tm)
        uuid))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-platform-by-short-name found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn lookup-instrument-by-short-name
  "Takes a context and a short name and returns the UUID for
  that instrument. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context short-name]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [cache (hash-cache/context->cache context kms-instruments-cache-key)
            [tm uuid] (util/time-execution (hash-cache/get-value cache kms-instruments-cache-key (util/safe-lowercase short-name)))]
        (rl-util/log-redis-read-complete "lookup-instrument-by-short-name" kms-instruments-cache-key tm)
        uuid))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-instrument-by-short-name found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn lookup-provider-by-short-name
  "Takes a context and a short name and returns the UUID for
  that provider. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context short-name]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [cache (hash-cache/context->cache context kms-providers-cache-key)
            [tm uuid] (util/time-execution (hash-cache/get-value cache kms-providers-cache-key (util/safe-lowercase short-name)))]
        (rl-util/log-redis-read-complete "lookup-provider-by-short-name" kms-providers-cache-key tm)
        uuid))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-provider-by-short-name found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn lookup-concept-by-short-name
  "Takes a context and a short name and returns the UUID for
  that concept. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context short-name]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [cache (hash-cache/context->cache context kms-concepts-cache-key)
            [tm uuid] (util/time-execution (hash-cache/get-value cache kms-concepts-cache-key (util/safe-lowercase short-name)))]
        (rl-util/log-redis-read-complete "lookup-concept-by-short-name" kms-concepts-cache-key tm)
        uuid))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-concept-by-short-name found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn lookup-granule-data-format-by-short-name
  "Takes a context and a short name and returns the UUID for
  that granule data format. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context short-name]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [cache (hash-cache/context->cache context kms-granule-data-format-cache-key)
            [tm uuid] (util/time-execution (hash-cache/get-value cache kms-granule-data-format-cache-key (util/safe-lowercase short-name)))]
        (rl-util/log-redis-read-complete "lookup-granule-data-format-by-short-name" kms-granule-data-format-cache-key tm)
        uuid))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-granule-data-format-by-short-name found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn lookup-iso-topic-category-by-name
  "Takes a context and a name and returns the UUID for
  that iso topic category. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context short-name]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [cache (hash-cache/context->cache context kms-iso-topic-categories-cache-key)
            [tm uuid] (util/time-execution (hash-cache/get-value cache kms-iso-topic-categories-cache-key (util/safe-lowercase short-name)))]
        (rl-util/log-redis-read-complete "lookup-iso-topic-category-by-name" kms-iso-topic-categories-cache-key tm)
        uuid))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-iso-topic-category-by-name found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn lookup-mime-type-by-name
  "Takes a context and a name and returns the UUID for
  that mime type. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context short-name]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [cache (hash-cache/context->cache context kms-mime-type-cache-key)
            [tm uuid] (util/time-execution (hash-cache/get-value cache kms-mime-type-cache-key (util/safe-lowercase short-name)))]
        (rl-util/log-redis-read-complete "lookup-mime-type-by-name" kms-mime-type-cache-key tm)
        uuid))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-mime-type-by-name found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn lookup-temporal-keyword-by-name
  "Takes a context and a name and returns the UUID for
  that temporal keyword. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context short-name]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [cache (hash-cache/context->cache context kms-temporal-keywords-cache-key)
            [tm uuid] (util/time-execution (hash-cache/get-value cache kms-temporal-keywords-cache-key (util/safe-lowercase short-name)))]
        (rl-util/log-redis-read-complete "lookup-temporal-keyword-by-name" kms-temporal-keywords-cache-key tm)
        uuid))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-temporal-keyword-by-name found redis carmine exception. Will return nil result." e)
        (throw e)))))

(defn lookup-processing-level-by-id
  "Takes a kms-index and a processing level ID and returns the UUID for
  that processing level. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context processing-level-id]
  (try
    (when-not (:ignore-kms-keywords context)
      (let [processing-level-cache (hash-cache/context->cache context kms-processing-level-cache-key)
            [tm uuid] (util/time-execution (hash-cache/get-value processing-level-cache kms-processing-level-cache-key (util/safe-lowercase processing-level-id)))]
        (rl-util/log-redis-read-complete "lookup-processing-level-by-id" kms-processing-level-cache-key tm)
        uuid))
    (catch Exception e
      (if (clojure.string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-processing-level-by-id found redis carmine exception. Will return nil result." e)
        (throw e)))))

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

(defn- skip-long-name-validation?
  "Returns true if the long name should be skipped in validation.
   Long name is skipped if it's nil, empty string, or 'Not provided' (case-insensitive)."
  [long-name]
  (or (nil? long-name)
      (string/blank? long-name)
      (when (string? long-name)
        (= "not provided" (string/lower-case (string/trim long-name))))))

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

(defn- lookup-by-umm-c-keyword-with-long-name
  "Generic lookup function for keyword types that have long-name field (platforms, instruments, projects).
  Takes a keyword as represented in the UMM concepts as a map and returns the KMS keyword map
  as its stored in the cache. Returns nil if a keyword is not found. Comparison is made case insensitively.
  The transform-fn parameter is an optional function to apply additional transformations to the keyword
  before lookup (e.g., renaming :type to :category for platforms)."
  ([context keyword-scheme umm-c-keyword]
   (lookup-by-umm-c-keyword-with-long-name context keyword-scheme umm-c-keyword identity))
  ([context keyword-scheme umm-c-keyword transform-fn]
   (try
     (let [umm-c-keyword (csk-extras/transform-keys csk/->kebab-case umm-c-keyword)
           umm-c-keyword (transform-fn umm-c-keyword)
           skip-long-name? (skip-long-name-validation? (get umm-c-keyword :long-name))
           ;; If long-name should be skipped (nil, empty, or "Not provided"), remove it before normalization
           keyword-for-comparison (if skip-long-name? (dissoc umm-c-keyword :long-name) umm-c-keyword)
           comparison-map (normalize-for-lookup keyword-for-comparison (kms-scheme->fields-for-umm-c-lookup keyword-scheme))
           umm-c-cache (hash-cache/context->cache context kms-umm-c-cache-key)
           [tm value] (util/time-execution (hash-cache/get-value umm-c-cache kms-umm-c-cache-key keyword-scheme))
           _ (rl-util/log-redis-read-complete (str "lookup-by-umm-c-keyword-" keyword-scheme) kms-umm-c-cache-key tm)
           ;; When skipping long-name, remove it from KMS index keys to enable matching on remaining fields
           value-for-lookup (if skip-long-name? (remove-long-name-from-kms-index value) value)]
       (get value-for-lookup comparison-map))
     (catch Exception e
       (if (clojure.string/includes? (ex-message e) "Carmine connection error")
         (error (str "lookup-by-umm-c-keyword-" keyword-scheme " found redis carmine exception. Will return nil result.") e)
         (throw e))))))

(defn lookup-by-umm-c-keyword-platforms
  "Takes a keyword as represented in the UMM concepts as a map and returns the KMS keyword map
  as its stored in the cache. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context keyword-scheme umm-c-keyword]
  ;; CMR-3696 This is needed to compare the keyword category, which is mapped
  ;; to the UMM Platform Type field.  This will avoid complications with facets.
  (lookup-by-umm-c-keyword-with-long-name
   context
   keyword-scheme
   umm-c-keyword
   #(set/rename-keys % {:type :category})))

(defn lookup-by-umm-c-keyword-instruments
  "Takes a keyword as represented in the UMM concepts as a map and returns the KMS keyword map
  as its stored in the cache. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context keyword-scheme umm-c-keyword]
  (lookup-by-umm-c-keyword-with-long-name context keyword-scheme umm-c-keyword))

(defn lookup-by-umm-c-keyword-projects
  "Takes a keyword as represented in the UMM concepts as a map and returns the KMS keyword map
  as its stored in the cache. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context keyword-scheme umm-c-keyword]
  (lookup-by-umm-c-keyword-with-long-name context keyword-scheme umm-c-keyword))

(defn lookup-by-umm-c-keyword
  "Takes a keyword as represented in UMM concepts as a map and returns the KMS keyword as it exists
   in the cache. Returns nil if a keyword is not found. Comparison is made case insensitively."
  [context keyword-scheme umm-c-keyword]
  (try
    (when-not (:ignore-kms-keywords context)
      (case keyword-scheme
        :platforms (lookup-by-umm-c-keyword-platforms context keyword-scheme umm-c-keyword)
        :instruments (lookup-by-umm-c-keyword-instruments context keyword-scheme umm-c-keyword)
        :projects (lookup-by-umm-c-keyword-projects context keyword-scheme umm-c-keyword)
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
            ;; Build either a 3 or 2 item map depending on if we have Quantities
            measurements-to-find (if (seq MeasurementQuantities)
                           (map (fn [quantity]
                                  {:context-medium MeasurementContextMedium
                                   :object MeasurementObject
                                   :quantity (:Value quantity)})
                                MeasurementQuantities)
                           [{:context-medium MeasurementContextMedium
                             :object MeasurementObject}])]
        ;; Remove will always return something when dealing with a nil keyword cache which we do not
        ;; what if there is no cache at all. When we have a cache, then do the removes.
        (when measurement-index
          (seq (remove #(get measurement-index %) measurements-to-find)))))
    (catch Exception e
      (if (string/includes? (ex-message e) "Carmine connection error")
        (error "lookup-by-measurement found redis carmine exception. Will return nil result." e)
        (throw e)))))
