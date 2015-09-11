(ns cmr.common-app.services.kms-fetcher
  "Provides functions to easily fetch keywords from the GCMD Keyword Management Service (KMS). It
  will use a cache in order to minimize calls to the GCMD KMS and improve performance. The job
  defined in this namespace should be used to keep the KMS keywords fresh. KMS keywords will be
  cached using a fallback cache with Cubby as the backup store. See the documentation for
  cmr.common.cache.fallback-cache for more details. As a result of persisting the keywords in Cubby,
  the CMR will still be able to lookup KMS keywords even when the GCMD KMS is unavailable. CMR will
  use the last keyword values which were retrieved from the GCMD KMS before it became unavailable.

  The KMS keywords are all cached under a single :kms key. The structure looks like the following:
  {:kms {:platforms [\"sn-1\" {:category \"C\" :series-entity \"S\"
                               :short-name \"SN-1\" :long-name \"LN\"}
                     \"sn-2\" {...}
                    ]}
         :providers [...]}"
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.jobs :refer [def-stateful-job]]
            [cmr.transmit.kms :as kms]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.cache :as cache]
            [cmr.common.cache.fallback-cache :as fallback-cache]
            [cmr.common-app.cache.cubby-cache :as cubby-cache]
            [cmr.common-app.cache.consistent-cache :as consistent-cache]
            [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
            [clojure.string :as str]
            [cmr.common.util :as util]))

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
  (stl-cache/create-single-thread-lookup-cache
    (fallback-cache/create-fallback-cache
      (consistent-cache/create-consistent-cache)
      (cubby-cache/create-cubby-cache))))

(defn- fetch-gcmd-keywords-map
  "Calls GCMD KMS endpoints to retrieve the keywords. Response is a map structured in the same way
  as used in the KMS cache."
  [context]
  (into {}
        (for [keyword-scheme (keys kms/keyword-scheme->field-names)]
          [keyword-scheme (kms/get-keywords-for-keyword-scheme
                            context keyword-scheme)])))

(defn refresh-kms-cache
  "Refreshes the KMS keywords stored in the cache. This should be called from a background job on a
  timer to keep the cache fresh. This will throw an exception if there is a problem fetching the
  keywords from KMS. The caller is responsible for catching and logging the exception."
  [context]
  (let [cache (cache/context->cache context kms-cache-key)]
    (cache/set-value cache kms-cache-key (fetch-gcmd-keywords-map context))))

(defn get-gcmd-keywords-map
  "Retrieves the GCMD keywords map from the cache."
  [context]
  (let [cache (cache/context->cache context kms-cache-key)]
    (cache/get-value cache kms-cache-key (partial fetch-gcmd-keywords-map context))))

(defn get-full-hierarchy-for-short-name
  "Returns the full hierarchy for a given short name. If the provided short-name cannot be found,
  nil will be returned."
  [gcmd-keywords-map keyword-scheme short-name]
  {:pre (some? (keyword-scheme kms/keyword-scheme->field-names))}
  (get-in gcmd-keywords-map [keyword-scheme (str/lower-case short-name)]))

(defn get-full-hierarchy-for-keyword
  "Returns the full hierarchy for a given keyword. All of the fields within the keyword need
  to match one of the keywords, otherwise nil is returned. We may want to update this check to
  be case insensitive in the future."
  [gcmd-keywords-map keyword-scheme keyword-map fields-to-compare]
  {:pre (some? (keyword-scheme kms/keyword-scheme->field-names))}
  (let [keyword-map (util/remove-nil-keys keyword-map)
        keyword-values (vals (keyword-scheme gcmd-keywords-map))]
    (first (filter (fn [gcmd-keyword]
                     (= (select-keys keyword-map fields-to-compare)
                        (select-keys gcmd-keyword fields-to-compare)))
                   keyword-values))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Job for refreshing the KMS keywords cache. Only one node needs to refresh the cache because
;; we use a consistent cache which uses cubby to coordinate any changes to the cache.

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


