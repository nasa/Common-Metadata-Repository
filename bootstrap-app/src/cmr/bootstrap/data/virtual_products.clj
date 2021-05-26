(ns cmr.bootstrap.data.virtual-products
  "Contains functions for bootstrapping virtual products."
  (:require
   [clojure.core.async :as async :refer [go go-loop >! <! <!!]]
   [clojure.string :as str]
   [cmr.bootstrap.data.util :as data]
   [cmr.bootstrap.embedded-system-helper :as helper]
   [cmr.common.config :as config :refer [defconfig]]
   [cmr.common.log :refer :all]
   [cmr.common.mime-types :as mime-types]
   [cmr.common.util :as util]
   [cmr.metadata-db.data.concepts :as mdb-c]
   [cmr.metadata-db.data.providers :as mdb-p]
   [cmr.metadata-db.services.search-service :as search]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.umm.umm-core :as umm]
   [cmr.virtual-product.data.source-to-virtual-mapping :as svm]
   [cmr.virtual-product.services.virtual-product-service :as vps]))

(def channel-name
  "The name (key in system map) for the channel used to coordinate requests."
  :virtual-products-channel)

(defconfig virtual-product-channel-size
  "The buffer size of virtual product channel"
  {:default 10 :type Long})

(defconfig virtual-product-source-granule-batch-size
  "The number of source granules to fetch at one time"
  {:default 500 :type Long})

(defconfig virtual-product-num-threads
  "The number of concurrent threads that should process virtual product creation"
  {:default 20 :type Long})

(defn- get-source-granule-batches
  "Returns a lazy seq of source granule batches in a given parent collection."
  [system {:keys [provider-id concept-id]}]
  (let [db       (helper/get-metadata-db-db system)
        provider (mdb-p/get-provider db provider-id)
        batches  (mdb-c/find-concepts-in-batches
                   db
                   provider
                   {:concept-type :granule
                    :parent-collection-id concept-id}
                   (virtual-product-source-granule-batch-size))]
    ;; Batches are returned in order by id, but items in each batch may be out of order. We sort
    ;; these by revision id so that tombstones are processed after earlier revisions of the virtual
    ;; granule are saved, otherwise we cannot save a tombstone.
    (map #(sort-by :revision-id %) batches)))

(defn- find-concept
  "Returns nil or exactly one concept excluding tombstones by searching metadata-db with the params.
  Throws exception if more than one non-tombstoned concept matches the given params."
  [system params]
  (let [concepts (->> params
                      (search/find-concepts {:system (helper/get-metadata-db system)})
                      (filter #(not (:deleted %))))]
    (condp = (count concepts)
      0 nil
      1 (first concepts)
      (throw (IllegalArgumentException. "Query returned more than one concept.")))))

(defn get-collection
  "Get a collection concept by provider-id and entry-title and merge it with its virtual product
  configuration."
  [system provider-id entry-title]
  (let [coll (find-concept system
                           {:concept-type :collection
                            :latest       true
                            :provider-id  provider-id
                            :entry-title  entry-title})
        config (get svm/source-to-virtual-product-mapping
                    [(svm/provider-alias->provider-id provider-id) entry-title])]
    (when coll (merge coll config))))

(defn derive-virtual-granule
  "Returns virtual granule concept and parsed UMM record to be ingested from a source collection,
  source granule, and destination virtual granule concept."
  [source-collection source-granule virtual-collection]
  (let [provider-id (:provider-id source-collection)
        source-short-name (:short-name source-collection)
        dest-short-name   (-> virtual-collection :extra-fields :short-name)
        orig-umm (umm/parse-concept source-granule)
        new-umm (svm/generate-virtual-granule-umm provider-id
                                                  source-short-name
                                                  orig-umm
                                                  (:extra-fields virtual-collection))
        new-granule-ur (:granule-ur new-umm)
        new-metadata (umm/umm->xml new-umm (mime-types/mime-type->format
                                             (:format source-granule)))
        new-concept (-> source-granule
                        (select-keys [:format :provider-id :concept-type :revision-id :deleted])
                        (assoc :native-id    new-granule-ur
                               :metadata     new-metadata
                               :provider-id  provider-id
                               :extra-fields {:parent-collection-id (:concept-id virtual-collection)
                                              :parent-entry-title
                                              (get-in virtual-collection [:extra-fields :entry-title])
                                              :delete-time          nil
                                              :granule-ur           new-granule-ur}))]
    [new-concept new-umm]))

(defn save-virtual-tombstone
  "Creates a tombstone revision corresponding to the source granule in
  the given virtual collection."
  [system source-collection source-granule virtual-collection]
  (let [virtual-granule-ur (svm/generate-granule-ur (:provider-id source-collection)
                                                    (:short-name source-collection)
                                                    {:short-name (get-in virtual-collection [:extra-fields :short-name])
                                                     :version-id (get-in virtual-collection [:extra-fields :version-id])}
                                                    (:native-id source-granule))
        existing-virtual-granule (find-concept
                                   system
                                   {:concept-type :granule
                                    :latest       true
                                    :provider-id  (:provider-id source-collection)
                                    :native-id    virtual-granule-ur})]
    (if existing-virtual-granule
      (do
        (info "Creating tombstone for" (:concept-id existing-virtual-granule)
              (:revision-id source-granule))
        (data/create-tombstone-and-unindex-concept system
                                                   (:concept-id existing-virtual-granule)
                                                   (:revision-id source-granule)))
      (info "Nothing to do, since no corresponding virtual granules exist for source granule"
            (:concept-id source-granule)))))

(defn- get-source-granule-batch-channel
  "Starts a process that will reads source granule batches and writes them to a channel. The channel
  is returned. Each message on the channel is a tuple of the source collection and a sequence of
  source granules."
  [system provider-id entry-title]
  (let [batch-chan (async/chan (virtual-product-channel-size))]
    (go
      (info (format "get-source-granule-batch-channel starting for provider [%s] entry-title [%s]"
                    provider-id entry-title))
      (try
        (let [source-collection (get-collection system provider-id entry-title)]
          (if source-collection
            (doseq [source-granule-batch (get-source-granule-batches system source-collection)]
              (>! batch-chan [source-collection source-granule-batch]))
            (warn (format "Ignoring non-existent collection for provider-id [%s] entry-title [%s]"
                          provider-id entry-title))))
        (finally
          (async/close! batch-chan)
          (info "get-source-granule-batch-channel completed"))))
    batch-chan))

(defn- process-source-granule
  "Processes a single source granule by creating the equivalent virtual product granules."
  [system log-fn quick-get-collection source-collection source-granule]
  ;; We may need the source UMM to determine if the source granule
  ;; should create any virtual granules, but only if it's not
  ;; deleted, so we will avoid parsing it until then, and then
  ;; only do it once using a delay.
  (let [source-umm' (delay (umm/parse-concept source-granule))]
    (doseq [vc-info (:virtual-collections source-collection)]
      ;; Then we need to flesh out the virtual collection info so that
      ;; we can actually save the new granule.
      (let [virtual-collection (quick-get-collection (:provider-id source-collection) (:entry-title vc-info))]
        (if (:deleted source-granule)
          (save-virtual-tombstone system source-collection source-granule virtual-collection)
          ;; Else, we need to ensure that the source granule
          ;; should result in virtual granules.
          (if (vps/source-granule-matches-virtual-product?
                (svm/provider-alias->provider-id (:provider-id source-collection))
                (:entry-title vc-info)
                @source-umm')
            (let [[v-concept v-umm] (derive-virtual-granule source-collection
                                                            source-granule
                                                            virtual-collection)]
              (log-fn "Generating virtual granule" (:native-id v-concept)
                      "from source granule:"       (:concept-id source-granule)
                      "into virtual collection:"   (:concept-id virtual-collection))
              (data/save-and-index-concept system v-concept v-umm))
            ;; Otherwise just make a note and continue.
            (log-fn "Source granule" (:concept-id source-granule)
                    "should not generate a virtual granule in collection"
                    (:concept-id virtual-collection))))))))

(defn- process-source-granule-batches
  "Starts a series of threads that read concepts in batches off the channel, save, and index them.
  Returns when all concepts have been processed or an error has occured."
  [system batch-chan]
  (let [quick-get-collection (memoize #(get-collection system %1 %2))
        thread-chans
        (for [n (range 0 (virtual-product-num-threads))
              :let [log (fn [& args]
                          (info "process-source-granule-batches" n ":" (str/join " " args)))]]
          (async/thread
            (log "starting")
            (try
              (util/while-let
                [[source-collection source-granule-batch] (<!! batch-chan)]
                (doseq [source-granule source-granule-batch]
                  (log "Processing" (get-in source-granule [:extra-fields :granule-ur]))
                  (process-source-granule
                    system log quick-get-collection source-collection source-granule)))
              (finally
                (async/close! batch-chan)
                (log "completed")))))]
    ;; Force iteration over all thread chans
    (doseq [thread-chan (doall thread-chans)]
      ;; Wait for the thread to close the channel indicating it's done.
      (<!! thread-chan))))

(defn bootstrap-virtual-products
  "Create necessary virtual product concepts from concrete concepts using virtual product app
  internals."
  [system provider-id entry-title]
  (info "Bootstrapping virtual products.")
  (->> entry-title
       (get-source-granule-batch-channel system provider-id)
       (process-source-granule-batches system)))

(defn handle-virtual-product-requests
  "Begin listening for requests on the specified channel in the
  bootstrap system."
  [system]
  (let [channel (get-in system [:core-async-dispatcher :virtual-product-channel])]
    (info "Listening for virtual product bootstrapping messages.")
    (go-loop [{:keys [provider-id entry-title]} (<! channel)]
             (try
               (bootstrap-virtual-products system provider-id entry-title)
               (catch Exception e
                 (error e "Failed to handle virtual product channel message.")))
             (recur (<! channel)))))
