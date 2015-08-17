(ns cmr.common-app.services.kms-fetcher
  "Provides functions to easily fetch keywords from the GCMD Keyword Management Service (KMS). It
  will use a cache in order to minimize calls to the GCMD KMS and improve performance. The job
  defined in this namespace should be used to keep the KMS keywords fresh. KMS keywords will be
  cached using a fallback cache with Cubby as the backup store. See the documentation for
  cmr.common.cache.fallback-cache for more details. As a result of persisting the keywords in Cubby,
  the CMR will still be able to lookup KMS keywords even when the GCMD KMS is unavailable. CMR will
  use the last keyword values which were retrieved from the GCMD KMS before it became unavailable."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.time-keeper :as tk]
            [cmr.common.jobs :refer [def-stateful-job]]
            [cmr.transmit.kms :as kms]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.cache :as cache]
            [cmr.common.cache.fallback-cache :as fallback-cache]
            [cmr.common-app.cache.cubby-cache :as cubby-cache]
            [cmr.common-app.cache.consistent-cache :as consistent-cache]
            [clojure.set :as set]
            [cheshire.core :as json]))

(def keyword-schemes
  "The keyword schemes which we fetch from the GCMD KMS."
  [:providers :platforms :instruments])

(def kms-cache-key
  "The key used to store the KMS cache in the system cache map."
  :kms)

(defn create-kms-cache
  "Used to create the cache that will be used for caching KMS keywords. All applications caching
  KMS keywords should use the same fallback cache to ensure functionality even if GCMD KMS becomes
  unavailable."
  []
  (fallback-cache/create-fallback-cache
    (consistent-cache/create-consistent-cache)
    (cubby-cache/create-cubby-cache)))

(defn refresh-kms-cache
  "Refreshes the KMS keywords stored in the cache. This should be called from a background job on a
  timer to keep the cache fresh. This will throw an exception if there is a problem fetching the
  keywords from KMS. The caller is responsible for catching and logging the exception."
  [context]
  (let [cache (cache/context->cache context kms-cache-key)
        updated-keywords (into {}
                               (for [keyword-scheme keyword-schemes]
                                 [keyword-scheme (kms/get-keywords-for-keyword-scheme
                                                   context keyword-scheme)]))]
    (cache/set-value cache kms-cache-key updated-keywords)))

(defn get-full-hierarchy-for-short-name
  "Returns the full hierarchy for a given short name. If the provided short-name cannot be found,
  nil will be returned."
  [context keyword-scheme short-name]
  (let [cache (cache/context->cache context kms-cache-key)]
    (get-in (cache/get-value cache kms-cache-key) [keyword-scheme short-name])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Job for refreshing the KMS keywords cache. Only one node needs to refresh the cache because
;; we use a consistent cache which uses cubby to coordinate any changes to the cache.

(def-stateful-job RefreshKmsCacheJob
  [_ system]
  (refresh-kms-cache {:system system}))

(defn refresh-kms-cache-job
  [job-key]
  {:job-type RefreshKmsCacheJob
   :job-key job-key
   :interval 7200})

(comment
  (def system {:system (get-in user/system [:apps :indexer])})

  (refresh-kms-cache system)
  (get-in (cache/get-value (cache/context->cache system kms-cache-key) kms-cache-key)
          [:providers "MEDIAS FRANCE"])

  (cache/get-keys (cache/context->cache system kms-cache-key))

  (get-full-hierarchy-for-short-name system :providers "MEDIAS FRANCE")

  )


