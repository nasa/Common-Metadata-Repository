(ns cmr.dynamo.connection
  "Contains functions for interacting with the DynamoDB storage instance"
  (:require [clojure.string :as str]
            [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.common.services.errors :as errors]
            [cmr.common.log :refer [debug error info trace warn]]
            [taoensso.faraday :as far]
            [cmr.dynamo.config :as dynamo-config]))

(defn concept-revision-tuple->key
  [tuple]
  {:concept-id (first tuple) :revision-id (second tuple)})

(defn provider-concept-revision-tuple->key
  [tuple]
  {:concept-id (nth tuple 1) :revision-id (nth tuple 2)})

(defn find-params->dynamo-clause
  ([params]
   (find-params->dynamo-clause params false))
  ([params or?]
   ;; Validate parameter names as a sanity check to prevent sql injection
   (let [valid-param-name #"^[a-zA-Z][a-zA-Z0-9_\-]*$"]
     (when-let [invalid-names (seq (filter #(not (re-matches valid-param-name (name %))) (keys params)))]
       (errors/internal-error! (format "Attempting to search with invalid parameter names [%s]"
                                       (str/join ", " invalid-names)))))
   (let [comparisons (for [[k v] params]
                       (cond
                         (sequential? v) (let [val (seq v)]
                                           `(~k [:in ~val]))
                         (map? v) (let [{:keys [value comparator]} v]
                                    `(~k [(map-comparator ~comparator) ~value]))
                         :else `(= ~k ~v)))]
     (if (> (count comparisons) 1)
       (if or?
         (cons `or comparisons)
         (cons `and comparisons))
       (first comparisons)))))

(def connection-options 
  {:endpoint (dynamo-config/dynamo-url)})

(defn save-concept
  [concept]
  (info "Concept passed to dynamo/save-concept: " concept)
  (far/put-item connection-options (dynamo-config/dynamo-table) concept))

(defn get-concept
  "Gets a concept from DynamoDB"
  ([concept-id]
   (first (far/query connection-options (dynamo-config/dynamo-table) {:concept-id [:eq concept-id]} {:order :desc :limit 1})))
  ([concept-id revision-id]
   (far/get-item connection-options (dynamo-config/dynamo-table) {:concept-id concept-id :revision-id revision-id})))

(defn get-concepts-provided
  "Gets a group of concepts from DynamoDB"
  [concept-id-revision-id-tuples]
  (remove nil? (doall (map (fn [batch] (far/batch-get-item connection-options {(dynamo-config/dynamo-table) {:prim-kvs (vec batch)}})) (partition-all 100 (map concept-revision-tuple->key concept-id-revision-id-tuples))))))

(defn get-concepts
  "Gets a group of concepts from DynamoDB based on search parameters"
  [params]
  ;; (if (:include-all params)
  ;;   (far/scan connection-options {:attr-conds (find-params->dynamo-clause (dissoc params :include-all))})
  ;;   (far/scan connection-options {:attr-conds (find-params->dynamo-clause (dissoc params :include-all)) }))
  (info "Params for searching DynamoDB: " params))

(defn get-concepts-small-table
  "Gets a group of concepts from DynamoDB using provider-id, concept-id, revision-id tuples"
  [params]
  ;; (if (:include-all params)
  ;;   ()
  ;;   ())
  (info "Params for searching DynamoDB small-table: " params))

(defn delete-concept
  "Deletes a concept from DynamoDB"
  [concept-id revision-id]
  (far/delete-item connection-options (dynamo-config/dynamo-table) {:concept-id concept-id :revision-id revision-id}))

(defn delete-concepts-provided
  "Deletes multiple concepts from DynamoDB"
  [concept-id-revision-id-tuples]
  (doall (map (fn [batch] (far/batch-write-item connection-options {(dynamo-config/dynamo-table) {:delete (vec batch)}})) (partition-all 25 (map concept-revision-tuple->key concept-id-revision-id-tuples)))))

(defn delete-concepts
  "Deletes multiple concepts from DynamoDB by search parameters"
  [params]
  (info "Params for deleting from DynamoDB: " params))