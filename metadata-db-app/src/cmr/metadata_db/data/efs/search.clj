(ns cmr.metadata-db.data.efs.search
  "Provides implementations of the cmr.metadata-db.data.concepts/ConceptStore protocol methods
  for retrieving concepts using parameters"
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.concepts :as cc]
   [cmr.common.util :as util]
   [cmr.metadata-db.data.concepts :as c])
  (:import
   (cmr.efs.connection EfsStore)))

(def association-concept-type->generic-association
  "Mapping of various association concept types to columns needed for migration to CMR_ASSOCIATIONS table."
  {:service-association {:association_type "SERVICE-COLLECTION" :kebab-key-mapping {:service-concept-id :source-concept-identifier}}
   :tag-association {:association_type "TAG-COLLECTION" :kebab-key-mapping {:tag-key :source-concept-identifier}}
   :tool-association {:association_type "TOOL-COLLECTION" :kebab-key-mapping {:tool-concept-id :source-concept-identifier}}
   :variable-association {:association_type "VARIABLE-COLLECTION" :kebab-key-mapping {:variable-concept-id :source-concept-identifier}}})

(def common-columns
  "A set of common columns for all concept types."
  #{:native_id :concept_id :revision_date :metadata :deleted :revision_id :format :transaction_id})

(def concept-type->columns
  "A map of concept type to the columns for that type in the database."
  (merge
   {:granule (into common-columns
                   [:provider_id :parent_collection_id :delete_time :granule_ur])
    :collection (into common-columns
                      [:provider_id :entry_title :entry_id :short_name :version_id :delete_time
                       :user_id])
    :tag (into common-columns [:user_id])
    :tag-association (into common-columns
                           [:associated_concept_id :associated_revision_id
                            :source_concept_identifier :user_id])
    :access-group (into common-columns [:provider_id :user_id])
    :service (into common-columns [:provider_id :service_name :user_id])
    :tool (into common-columns [:provider_id :tool_name :user_id])
    :acl (into common-columns [:provider_id :user_id :acl_identity])
    :humanizer (into common-columns [:user_id])
    :subscription (into common-columns
                        [:provider_id :subscription_name :subscriber_id
                         :collection_concept_id :user_id
                         :normalized_query :subscription_type])
    :variable (into common-columns [:provider_id :variable_name :measurement :user_id :fingerprint])
    :variable-association (into common-columns
                                [:associated_concept_id :associated_revision_id
                                 :source_concept_identifier :user_id])
    :service-association (into common-columns
                               [:associated_concept_id :associated_revision_id
                                :source_concept_identifier :user_id])
    :tool-association (into common-columns
                            [:associated_concept_id :associated_revision_id
                             :source_concept_identifier :user_id])
    :generic-association (into common-columns
                               [:associated_concept_id :associated_revision_id
                                :source_concept_identifier :source_revision_id :user_id])}
   (zipmap (cc/get-generic-concept-types-array)
           (repeat (into common-columns [:provider_id :document_name :schema :user_id :created_at])))))

(def single-table-with-providers-concept-type?
  "The set of concept types that are stored in a single table with a provider column. These concept
   types must include the provider id as part of the sql params"
  #{:access-group :variable :service :tool :subscription})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn find-concepts
  [db providers params]
  {:pre [(coll? providers)]}
  ())

(defn find-concepts-in-batches
  ([db provider params batch-size]
   (find-concepts-in-batches db provider params batch-size 0))
  ([db provider params batch-size requested-start-index]
   ()))

(defn find-concepts-in-batches-with-stmt
  ([db provider params stmt batch-size]
   (find-concepts-in-batches-with-stmt db provider params stmt batch-size 0))
  ([db provider params stmt batch-size requested-start-index]
   ()))

(defn find-latest-concepts
  [db provider params]
  {:pre [(:concept-type params)]}
  ;; First we find all revisions of the concepts that have at least one revision that matches the
  ;; search parameters. Then we find the latest revisions of those concepts and match with the
  ;; search parameters again in memory to find what we are looking for.
  ())

(defn find-associations
  "Find all associations in the database table that are part of a concept id
  that is passed in through params. The parameters look like the following:
  {:associated-concept-id \"C1200000013-PROV1\" :source-concept-identifier \"C1200000013-PROV1\"}"
  [db params]
  ())

(defn find-latest-associations
  "Find the latest associations in the database table that are part of a concept id
  that is passed in through params. The parameters look like the following:
  {:associated-concept-id \"C1200000013-PROV1\" :source-concept-identifier \"C1200000013-PROV1\"}"
  [db params]
  ())

(def behaviour
  {:find-concepts find-concepts
   :find-concepts-in-batches find-concepts-in-batches
   :find-concepts-in-batches-with-stmt find-concepts-in-batches-with-stmt
   :find-latest-concepts find-latest-concepts
   :find-associations find-associations
   :find-latest-associations find-latest-associations})

(extend EfsStore
  c/ConceptSearch
  behaviour)
