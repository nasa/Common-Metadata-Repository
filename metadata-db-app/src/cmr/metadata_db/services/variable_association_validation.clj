(ns cmr.metadata-db.services.variable-association-validation
  "Functions for validating variable association to collection."
  (:require
   [clojure.string :as string]
   [cmr.metadata-db.services.search-service :as mdb-ss]))

(defn- append-error
  "Returns the association with the given error message appended to it."
  [association error-msg]
  (update association :errors concat [error-msg]))

(defn- validate-association-conflict-for-collection 
  "Make sure we don't have both associations with the collection revision-id and without revision-id."
  [context variable-concept-id variable-association]
  (let [vas (->> (mdb-ss/find-concepts context
                                      {:concept-type :variable-association
                                       :variable-concept-id variable-concept-id
                                       :associated-concept-id (:concept-id variable-association)
                                       :exclude-metadata true
                                       :latest true})
                 (filter #(not (:deleted %))))
        coll-revision-ids (map #(get-in % [:extra-fields :associated-revision-id]) vas)
        not-nil-revision-ids (remove nil? coll-revision-ids)]
    (cond
      ;; there is no existing variable association found, no need to validate
      (= 0 (count coll-revision-ids))
      nil

      ;; there are existing variable associations and they are all on collection revisions
      (= (count coll-revision-ids) (count not-nil-revision-ids))
      (when-not (:revision-id variable-association)
        (format (str "There are already variable associations with variable concept id [%s] on "
                     "collection [%s] revision ids [%s], cannot create variable association "
                     "on the same collection without revision id.")
                variable-concept-id (:concept-id variable-association) (string/join ", " coll-revision-ids)))

      ;; there are existing variable associations and they are all on collection without revision
      (= 0 (count not-nil-revision-ids))
      (when-let [revision-id (:revision-id variable-association)]
        (format (str "There are already variable associations with variable concept id [%s] on "
                     "collection [%s] without revision id, cannot create variable association "
                     "on the same collection with revision id [%s].")
                variable-concept-id (:concept-id variable-association) revision-id))

      ;; there are conflicts within the existing variable associations in metadata-db already
      :else
      (format (str "Variable can only be associated with a collection or a collection revision, "
                   "never both at the same time. There are already conflicting variable associations "
                   "in metadata-db with variable concept id [%s] on collection [%s] , "
                   "please delete one of the conflicting variable associations.")
              variable-concept-id (:concept-id variable-association)))))

(defn- validate-association-conflict
  "Validates the association (either on a specific revision or over the whole collection)
  does not conflict with one or more existing associations in Metadata DB. Tag/Variable
  cannot be associated with a collection revision and the same collection without revision
  at the same time. Returns the association with errors appended if applicable."
  [context assoc-var-id association]
  (if-let [error-msg (validate-association-conflict-for-collection
                       context assoc-var-id association)]
    (append-error association error-msg)
    association))

(defn validate-associations
  "Validate if the association has conflict."
  [context assoc-var-id associations]
  (map #(validate-association-conflict context assoc-var-id %) associations))
