(ns cmr.indexer.services.autocomplete
  "Provide functions to index concept"
  (:require
   [camel-snake-kebab.core :as camel-snake-kebab]
   [clj-time.core :refer [now]]
   [clojure.string :as string]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.log :as log :refer [debug info warn error]]
   [cmr.common.util :as util :refer [defn-timed]]
   [cmr.indexer.data.concept-parser :as cp]
   [cmr.indexer.data.concepts.collection.collection-util :as collection-util]
   [cmr.indexer.data.concepts.collection.humanizer :as humanizer]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.indexer.data.index-set :as idx-set]
   [cmr.indexer.services.index-service :as service]
   [cmr.transmit.metadata-db :as meta-db]
   [cmr.transmit.search :as search]))

(defconfig autocomplete-suggestion-age-limit
  "Age in hours that we allow autocomplete suggestions to persist to avoid stale data."
  {:type Long
   :default 24})

(defn- keywords->elastic-docs
  "Convert hierarchical keywords to colon-separated elastic docs for indexing.
  The keywords may not be hierarchical all the way to the end - some can be skipped to the last
  keyword and may be nil."
  [index type keywords keyword-hierarchy public-collection? permitted-group-ids modified-date]
  (when (and (map? keywords)
             (pos? (count keywords)))
    (let [k-strings (->> keyword-hierarchy
                         (map #(get keywords %))
                         (util/remove-nil-tail))
          keyword-string (string/join ":" k-strings)
          keyword-value (last k-strings)
          id (-> (string/lower-case keyword-string)
                 (str "_" type)
                 hash)]
        {:_id id
         :type type
         :value keyword-value
         :fields keyword-string
         :_index index
         :contains-public-collections public-collection?
         :permitted-group-ids permitted-group-ids
         :modified modified-date})))

(defn- science-keywords->elastic-docs
  "Convert hierarchical science-keywords to colon-separated elastic docs for indexing.
  Below 'term', variable may not be hierarchical - they can be skipped - and may be nil."
  [index science-keywords public-collection? permitted-group-ids modified-date]
  (let [keyword-hierarchy [:topic
                           :term
                           :variable-level-1
                           :variable-level-2
                           :variable-level-3
                           :detailed-variable]
        type "science_keywords"]
    (keywords->elastic-docs index
                            type
                            science-keywords
                            keyword-hierarchy
                            public-collection?
                            permitted-group-ids
                            modified-date)))

(defn- platform-keywords->elastic-docs
  "Convert hierarchical platform keywords to colon-separated elastic docs for indexing.
  Below 'category', the keywords may not be hierarchical - sub-category can be skipped - and may be
  nil."
  [index platform-keywords public-collection? permitted-group-ids modified-date]
  (let [keyword-hierarchy [:basis :category :sub-category :short-name]
        type "platforms"]
    (keywords->elastic-docs index
                            type
                            platform-keywords
                            keyword-hierarchy
                            public-collection?
                            permitted-group-ids
                            modified-date)))

(defn- suggestion-doc
  "Creates elasticsearch docs from a given humanized map"
  [index permissions key-name value-map]
  (let [values (->> value-map
                    seq
                    (remove #(string/includes? (name (key %)) "-lowercase")))
        sk-matcher (re-matcher #"science-keywords" key-name)
        platform-matcher (re-matcher #"platforms2-humanized" key-name)
        public-collection? (if (some #(= % "guest") permissions)
                             true
                             false)
        permitted-group-ids (->> permissions
                                 (remove #(= "guest" %))
                                 (string/join ",")
                                 not-empty)
        modified-date (str (now))]
    (cond
      (seq (re-find sk-matcher))
      (science-keywords->elastic-docs index
                                      value-map
                                      public-collection?
                                      permitted-group-ids
                                      modified-date)

      (seq (re-find platform-matcher))
      (platform-keywords->elastic-docs index
                                       value-map
                                       public-collection?
                                       permitted-group-ids
                                       modified-date)

      :else
      (map (fn [value]
             (let [v (val value)
                   type (-> key-name
                            camel-snake-kebab/->snake_case_keyword
                            (string/replace #"_humanized|:" ""))
                   id (-> (string/lower-case v)
                          (str "_" type)
                          hash)]
              {:type type
               :_id id
               :value v
               :fields v
               :_index index
               :contains-public-collections public-collection?
               :permitted-group-ids permitted-group-ids
               :modified modified-date}))
           values))))

(defn- get-suggestion-docs
  "Given the humanized fields from a collection, assemble an elastic doc for each
  value available for indexing into elasticsearch"
  [index humanized-fields]
  (let [{:keys [permissions]} humanized-fields
        fields-without-permissions (dissoc humanized-fields :id :permissions)]
    (for [humanized-field fields-without-permissions
          :let [key (key humanized-field)
                key-name (-> key
                             name
                             (string/replace #"(\.humanized(_?2)?|-sn|-id)" ""))
                value-map (as-> humanized-field h
                                (val h)
                                (map util/remove-nil-keys h)
                                (map #(dissoc % :priority) h))
                suggestion-docs (->> value-map
                                     (map #(suggestion-doc index permissions key-name %))
                                     (remove nil?))]]
      suggestion-docs)))

(defn- anti-value?
  "Returns whether or not the term is an anti-value. e.g. \"not applicable\" or \"not provided\".
  This is case-insensitive"
  [term]
  (let [rx (re-pattern #"(none|not (provided|applicable))")]
    (or (string/blank? term)
        (some? (re-find rx (string/lower-case term))))))

(defn anti-value-suggestion?
  "Returns whether an autocomplete suggestion has an anti-value as the :value
  See also [[anti-value?]]"
  [suggestion]
  (let [{:keys [value]} suggestion]
    (anti-value? value)))

(defn- parse-collection
  "Parses collection into concepts. Returns nil on error."
  [context collection]
  (try
    (cp/parse-concept context collection)
    (catch Exception e
      (error (format "An error occurred while parsing collection for autocomplete with concept-id [%s]: %s"
                     (:concept-id collection)
                     (.getMessage e))))))

(defn- get-humanized-collections
  "Get the humanized fields for the passed in parsed-concept and remove the old flat platform
  since we don't support those facets anymore for autocomplete."
  [context collection]
  (dissoc (humanizer/collection-humanizers-elastic context collection) :platform-sn-humanized))

(defn- collections->suggestion-docs
  "Convert collection concept metadata to UMM-C and pull facet fields
  to be indexed as autocomplete suggestion doc"
  [context collections provider-id]
  (let [{:keys [index-names]} (idx-set/get-concept-type-index-names context)
        index (get-in index-names [:autocomplete :autocomplete])
        humanized-fields-fn (partial get-humanized-collections context)
        parsed-concepts (->> collections
                             (remove :deleted)
                             (map #(parse-collection context %))
                             (remove nil?))
        collection-permissions (map (fn [collection]
                                      (let [permissions (collection-util/get-coll-permitted-group-ids context provider-id collection)]
                                        {:id (:concept-id collection)
                                         :permissions permissions}))
                                    collections)
        humanized-fields (map humanized-fields-fn parsed-concepts)
        humanized-fields-with-permissions (map merge collection-permissions humanized-fields)]
    (->> humanized-fields-with-permissions
         (map #(get-suggestion-docs index %))
         flatten
         (remove anti-value-suggestion?))))

(defn-timed reindex-autocomplete-suggestions-for-provider
  "Reindex autocomplete suggestion for a given provider"
  [context provider-id]
  (info "Reindexing autocomplete suggestions for provider" provider-id)
  (let [latest-collection-batches (meta-db/find-in-batches
                                   context
                                   :collection
                                   (service/determine-reindex-batch-size provider-id)
                                   {:provider-id provider-id :latest true})]
    (reduce (fn [num-indexed coll-batch]
              (let [batch (collections->suggestion-docs context coll-batch provider-id)]
                (es/bulk-index-autocomplete-suggestions context batch)
                (+ num-indexed (count coll-batch))))
            0
            latest-collection-batches)))

(defn prune-stale-autocomplete-suggestions
  "Delete any autocomplete suggestions that were modified outside the retention period."
  [context]
  (info (format "Pruning autocomplete suggestions older than %d hours."
                (autocomplete-suggestion-age-limit)))
  (let [{:keys [index-names]} (idx-set/get-concept-type-index-names context)
        index (get-in index-names [:autocomplete :autocomplete])
        concept-mapping-types (idx-set/get-concept-mapping-types context)
        mapping-type (concept-mapping-types :collection)
        document-age (format "now-%dh/h" (autocomplete-suggestion-age-limit))]
    (es/delete-by-query
     context
     index
     mapping-type
     {:range {(service/query-field->elastic-field :modified :suggestion) {:lt document-age}}})))
