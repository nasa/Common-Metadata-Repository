(ns cmr.indexer.data.concepts.association-util
  "Contains functions to parse and convert generic associations to a map structure 
  for each generic concept type."
  (:require
   [clojure.set :as s]
   [cmr.common.concepts :as cc]))

(defn allow-group-by-concept-type
  "Creates a temporary structure so that we can group the concept ids by concept type."
  [association concept-id]
  (let [asn (cond
             (or (= concept-id (:source-concept-identifier association))
                 (= concept-id (:service-concept-id association))
                 (= concept-id (:tool-concept-id association))
                 (= concept-id (:variable-concept-id association)))
              (-> association
                  (dissoc :source-concept-identifier :source-revision-id
                          :service-concept-id :tool-concept-id :variable-concept-id)
                  (s/rename-keys {:associated-concept-id :concept-id
                                  :associated-revision-id :revision-id}))

              (= concept-id (:associated-concept-id association))
              (-> association
                  (dissoc :associated-concept-id :associated-revision-id)
                  (s/rename-keys {:source-concept-identifier :concept-id
                                  :service-concept-id :concept-id
                                  :tool-concept-id :concept-id
                                  :variable-concept-id :concept-id
                                  :source-revision-id :revision-id})))]
    (when asn
      (assoc asn :concept-type (cc/concept-id->type (:concept-id asn))))))

(defn build-single-concept-association-list
  "Creates a map of a pluralized concept key with the a group of like concept, concept ids.
  The return is used for a concepts association list."
  [concept-key associations]
  {(cc/pluralize-concept-type concept-key) (mapv #(dissoc % :concept-type) associations)})

(defn generic-assocs->concept-named-assoc-list
  "Builds a consolidated map for all of the generic associations where the actual
  concept-type name gets used.  This map will be used in the concepts association list."
  [concept-associations-group]
  (let [concept-keys (keys concept-associations-group)]
    (into {} (map #(build-single-concept-association-list % (get concept-associations-group %)) concept-keys))))

(defn generic-assoc-list->assoc-struct
  "Takes an association structure list, and adds concept-types to each association
  structure to group the associations by concept type.
  Then creates a plural concept named list of like association concept ids. example:
  Takes [{:source-concept-identifier \"C1200000021-PROV1\", :associated-concept-id 
  \"DQS1200000061-PROV1\", :data {:hello \"ok\"}},
  {:source-concept-identifier \"C1200000021-PROV1\", :associated-concept-id \"OO1200000014-PROV1\"}]
   and converts it to 
   {:dataqualitysummaries [{:concept-id \"DQS1200000012-PROV1\" :data {:hello \"ok\"}}]
    :orderoptions [{:concept-id \"OO1200000014-PROV1\"}]}"
  [generic-assoc-list concept-id]
  (generic-assocs->concept-named-assoc-list
   (group-by :concept-type (map #(allow-group-by-concept-type % concept-id) generic-assoc-list))))
