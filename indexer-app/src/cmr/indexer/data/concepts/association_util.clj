(ns cmr.indexer.data.concepts.association-util
  "Contains functions to parse and convert generic associations to a map structure 
  for each generic concept type."
  (:require
   [clojure.set :as s]
   [cmr.common.concepts :as cc]
   [cmr.common.util :as util]))

(defn- update-association-for-concept
  "Remove the concept info related to for-concept-id, convert the concept info that's associated
  with the for-concept-id into standard concept-id revision-id."
  [association for-concept-id]
  (let [source-id (or (:source-concept-identifier association)
                      (:service-concept-id association)
                      (:tool-concept-id association)
                      (:variable-concept-id association))
        ;;There are no service-revision-id tool-revision-id and variable-revision-id.
        ;;because the service/tool/variable collection associations don't allow revision id
        ;;for serivces, tools and variables.
        source-revision-id (:source-revision-id association)
        assoc-id (:associated-concept-id association)
        assoc-revision-id (:associated-revision-id association)
        concept-id (if (= for-concept-id source-id)
                     assoc-id
                     source-id)
        revision-id (if (= for-concept-id source-id)
                     assoc-revision-id
                     source-revision-id)
        revision-id (if (string? revision-id)
                      (read-string revision-id)
                      revision-id)]
    (-> association
        (dissoc :source-concept-identifier :source-revision-id
                :associated-concept-id :associated-revision-id
                :service-concept-id :tool-concept-id :variable-concept-id)
        (assoc :concept-id concept-id :revision-id revision-id)
        (util/remove-nil-keys))))

(defn assoc-list->assoc-struct
  "Takes an association list, including both the generic and service/tool/variable associations,
  remove the concept info related to concept-id in each association, then creates a plural concept
  named list of like association concept ids. example:
  Takes [{:source-concept-identifier \"C1200000021-PROV1\"
          :source-revision-id 1
          :associated-concept-id \"DQS1200000061-PROV1\"
          :associated-revision-id 1
          :data {:hello \"ok\"}}
         {:source-concept-identifier \"C1200000021-PROV1\"
          :associated-concept-id \"OO1200000014-PROV1\"}
         {:service-concept-id \"S1200000022-PROV1\"
          :associated-concept-id \"C1200000021-PROV1\"}
         {:tool-concept-id \"TL1200000023-PROV1\"
          :associated-concept-id \"C1200000021-PROV1\"}
         {:variable-concept-id \"VL1200000024-PROV1\"
          :associated-concept-id \"C1200000021-PROV1\"}]
   and converts it to
   {:data-quality-summaries [{:concept-id \"DQS1200000012-PROV1\" :revision-id 1 :data {:hello \"ok\"}}]
    :order-options [{:concept-id \"OO1200000014-PROV1\"}]
    :services [{:concept-id \"S1200000022-PROV1\"}]
    :tools [{:concept-id \"TL1200000023-PROV1\"}]
    :variables [{:concept-id \"V1200000024-PROV1\"}] }"
  [assoc-list concept-id]
  (->> assoc-list
       (map #(update-association-for-concept % concept-id))
       ;;group the associations into pluralized concept types.
       (group-by #(cc/pluralize-concept-type (cc/concept-id->type (:concept-id %))))))

(defn associations->gzip-base64-str
  "Returns the gziped base64 string for the given variable, service, tool and generic associations,
  or the combinations."
  [all-assocs concept-id]
  (when all-assocs
    (->> concept-id
         (assoc-list->assoc-struct all-assocs)
         (util/remove-map-keys empty?)
         pr-str
         util/string->gzip-base64)))
