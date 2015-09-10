(ns cmr.search.api.keyword
  "Defines the HTTP URL routes for the keyword endpoint in the search application.

  The keyword endpoint is used to retrieve the keywords for each of the controlled vocabulary
  fields. The controlled vocabulary is cached within CMR, but the actual source is the GCMD KMS
  system. Users of this endpoint are interested in knowing what the CMR considers as the current
  controlled vocabulary, since it is the cached CMR values that will eventually be enforced on CMR
  ingest.

  The keywords are returned in a hierarchical format. The response format is such that the caller
  does not need to know the hierarchy, but it can be inferred from the results. Keywords are not
  guaranteed to have values for every subfield in the hierarchy, so the response will indicate
  the next subfield below the current field in the hierarchy which has a value. It is possible for
  the keywords to have multiple potential subfields below it for different keywords with the same
  value for the current field in the hierarchy. When this occurs the subfields property will
  include each of the subfields.

  See the unit tests in cmr.search.test.api.keyword for detailed examples of the responses.

  Commonly used parameters in this namespace are:
  keyword-scheme     The type of keyword. For example :platforms or :instruments.
  keyword-hierarchy  An ordered array of subfields for the given keyword scheme. For example,
  [:category :series-entity :short-name :long-name] for the :platforms keyword scheme.
  keyword-map        A single GCMD keyword with each of its subfields as a key in a map. If the
                     keyword does not have a value for a subfield, that key will not be present in
                     the map."

  (:require [compojure.core :refer :all]
            [cheshire.core :as json]
            [camel-snake-kebab.core :as csk]
            [cmr.common-app.services.kms-fetcher :as kf]
            [cmr.common.mime-types :as mt]
            [cmr.common.util :as util]
            [cmr.common.services.errors :as errors]
            [cmr.search.services.query-execution.facets-results-feature :as frf]
            [clojure.string :as str]
            [clojure.set :as set]))

(def cmr-to-gcmd-keyword-scheme-aliases
  "Map of all keyword schemes which are referred to with a different name within CMR and GCMD."
  {:archive-centers :providers})

(defn- translate-keyword-scheme-to-gcmd
  "Translates a keyword scheme into a known keyword scheme for GCMD."
  [keyword-scheme]
  (or (keyword-scheme cmr-to-gcmd-keyword-scheme-aliases)
      keyword-scheme))

(defn- translate-keyword-scheme-to-cmr
  "Translates a keyword scheme into a known keyword scheme for CMR."
  [keyword-scheme]
  (or (keyword-scheme (set/map-invert cmr-to-gcmd-keyword-scheme-aliases))
      keyword-scheme))

(defn- validate-keyword-scheme
  "Throws a service error if the provided keyword-scheme is invalid."
  [keyword-scheme]
  (let [valid-keywords (concat (keys frf/nested-fields-mappings)
                               (vals cmr-to-gcmd-keyword-scheme-aliases))]
    (when-not (contains? (set valid-keywords) keyword-scheme)
      (errors/throw-service-error
        :bad-request
        (format "The keyword scheme [%s] is not supported. Valid schemes are: %s, and %s."
                (name keyword-scheme)
                (str/join
                  ", " (map #(csk/->snake_case (name %)) (rest valid-keywords)))
                (-> valid-keywords first name csk/->snake_case))))))

(defn- is-leaf?
  "Determines if we are at the leaf point within the hierarchy for the provided keyword. The
  remaining-hierarchy parameter contains the fields that the caller is checking for a non-nil
  value. A non-nil value indicates we are not yet at the last field in the hierarchy, and therefore
  we are not at the leaf point."
  [remaining-hierarchy keyword-map]
  (empty? (filter #(% keyword-map) remaining-hierarchy)))

(defn- get-leaf-values-to-uuids
  "Returns a map of values for the given field to the UUID for that value. The value will only be
  included in the map if the provided subfield is at the leaf level for a keyword."
  [keyword-hierarchy field keywords]
  (into {}
        (for [keyword-map keywords
              :when (is-leaf? (rest keyword-hierarchy) keyword-map)]
          [(field keyword-map) (:uuid keyword-map)])))

(defn- get-subfields-for-keyword
  "Figure out the set of all subfields which are directly below the current field for the provided
  keywords. It is possible the next field in the hierarchy is nil for a keyword, but further down
  the chain there is a non-nil field."
  [keyword-hierarchy keywords field value]
  (loop [remaining-fields keyword-hierarchy
         matching-keywords (filter #(= value (field %)) keywords)
         all-subfields nil]
    (let [next-field (first remaining-fields)
          keywords-below-with-next-field (when next-field (keep next-field matching-keywords))
          keywords-with-nil-next-field (filter #(nil? (next-field %)) matching-keywords)
          all-subfields (if (seq keywords-below-with-next-field)
                          (conj all-subfields next-field)
                          all-subfields)]
      (if (seq (rest remaining-fields))
        (recur (rest remaining-fields) keywords-with-nil-next-field all-subfields)
        all-subfields))))

(defn- get-hierarchy-from-field
  "Returns all of the fields in the hierarchy starting from the provided field and including all
  fields after."
  [^java.util.List keyword-hierarchy field]
  (let [field-index (.indexOf keyword-hierarchy field)]
    (filter #(<= field-index (.indexOf keyword-hierarchy %)) keyword-hierarchy)))

(defn- filter-keywords-with-non-nil-values
  "Removes any keywords which have non-nil values for any subfields between the start of the keyword
  hierarchy and the provided subfield-name.

  For example (filter-keywords [:a :b :c :d] :d keywords) would filter out any keywords which have
  a non-nil value for :b or :c."
  [keyword-hierarchy subfield-name keywords]
  {:pre (contains? keyword-hierarchy subfield-name)}
  (loop [filtered-keywords keywords
         keyword-hierarchy keyword-hierarchy]
    (let [current-field (first keyword-hierarchy)]
      (if (= current-field subfield-name)
        filtered-keywords
        (recur (filter #(nil? (current-field %)) keywords)
               (rest keyword-hierarchy))))))


(comment

  (declare node-type)

  (def branch-node-type
    {:value s/Str
     :subfields [s/Keyword]
     (s/optional :uuid) s/Str
     s/Keyword [node-type]})

  (def leaf-node-type
    {:value s/Str
     :uuid s/Str})

  (def root-node
    s/Keyword [node-type])

  (def node-type
    (s/OR branch-node-type leaf-node-type))

  (def expected-result
    {:a [{:value "A1"
          :subfields ["c"]
          :c [{:value "C1"
               :uuid "a1c1"
               :subfields ["d"]
               :d [{:value "D1"
                    :uuid "a1c1d1"}]}]}
         {:value "A2"
          :uuid "a2"
          :subfields ["d"]
          :d [{:value "D1"
               :uuid "a2d1"}]}
         {:value "A3"
          :subfields ["b" "c" "d"]
          :b [{:value "B1"
               :subfields ["c"]
               :c [{:value "C1"
                    :subfields ["d"]
                    :d [{:value "D1"
                         :uuid "a3b1c1d1"}]}]}]
          :c [{:value "C2"
               :uuid "a3c2"}]
          :d [{:value "D2"
               :uuid "a3d2"}]}]})

  {:a "A1" :c "C1" :uuid "a1c1"}

  {:a [{:value "A1"
        :subfields ["b"]
        :b [{:value nil
             :subfields ["c"]
             :c [{:value "C1"
                  :uuid "a1c1"}]}]}]}

  (def example-keywords
    [{:a "A1" :c "C1" :uuid "a1c1"}
     {:a "A1" :c "C1" :d "D1" :uuid "a1c1d1"}
     {:a "A2" :uuid "a2"}
     {:a "A2" :d "D1" :uuid "a2d1"}
     {:a "A3" :b "B1" :c "C1" :d "D1" :uuid "a3b1c1d1"}

     {:a "A3" :c "C2" :uuid "a3c2"}
     {:a "A3" :d "D2" :uuid "a3d2"}])

  (def keyword-hierarchy-example [:a :b :c :d])

  (def expected-merged
    {:a [{:value "A3"
          :b [{:value nil
               :c [{:value nil
                    :d [{:value "D2"
                         :uuid "a3d2"
                         }]}
                   {:value "C2"
                    :uuid "a3c2"
                    :d [{:value nil}]
                    }]}
              {:value "B1"
               :c [{:value "C1"
                    :d [{:value "D1"
                         :uuid "a3b1c1d1"
                         }]}
                   ]}]}
         {:value "A2"
          ; :uuid "a2"
          :b [{:value nil
               :c [{:value nil
                    :d [{:value "D1"
                         :uuid "a2d1"
                         }
                        {:value nil}]}]}]}
         {:value "A1"
          :b [{:value nil
               :c [{:value "C1"
                    :uuid "a1c1"
                    :d [{:value "D1"
                         :uuid "a1c1d1"
                         }
                        {:value nil}]}]}]}]})


  (->> example-keywords
       (map #(keyword->hierarchy % [:a :b :c :d]))
       (reduce merge-hierarchical-maps)
       (= expected-merged)
       )

  (map #(keyword->hierarchy % [:a :b :c :d]) example-keywords)

  )

;; Three steps
;; Flat to hierarchical map
;; Merge together all of the keyword maps
;; Remove fields that should not be there

(defn keyword->hierarchy
  [keyword-map keyword-hierarchy]
  (let [[current-subfield & remaining-subfields] keyword-hierarchy]
    {current-subfield [(merge
                        (when (seq remaining-subfields)
                          (keyword->hierarchy keyword-map remaining-subfields))
                        {:value (current-subfield keyword-map)})]}))

(defn merge-hierarchical-maps
  [hm1 hm2]
  ;; Merge the two maps without their values
  (let [merged-map (merge-with
                     (fn [existing-values new-values]
                       ;; Find the values in common by using map-b and then merge those maps.
                       ;; The merge-with here recursively calls into the same function
                       (->> (merge-with merge-hierarchical-maps
                                        (map-by :value existing-values)
                                        (map-by :value new-values))
                            vals
                            vec))
                     (dissoc hm1 :value :uuid)
                     (dissoc hm2 :value :uuid))]
    ;; Add value back in if it was there originally
    (if (contains? hm1 :value)
      (assoc merged-map :value (:value hm1))
      merged-map)))

(comment

  (def example-keywords
    [{:a "A1" :c "C1" :uuid "a1c1"}
     {:a "A1" :c "C1" :d "D1" :uuid "a1c1d1"}
     {:a "A2" :uuid "a2"}
     {:a "A2" :d "D1" :uuid "a2d1"}
     {:a "A3" :b "B1" :c "C1" :d "D1" :uuid "a3b1c1d1"}
     {:a "A3" :c "C2" :uuid "a3c2"}
     {:a "A3" :d "D2" :uuid "a3d2"}])

  (def hm1
    (keyword->hierarchy (first example-keywords) [:a :b :c :d]))

  (def hm2
    (keyword->hierarchy (second example-keywords) [:a :b :c :d]))

  (def hm3
    (keyword->hierarchy (nth example-keywords 2) [:a :b :c :d]))


  (def hm1
    {:a [{:value "A1",
          :b [{:value nil,
               :c [{:value "C1",
                    :d [{:value nil}]}]}]}]})

  (def hm2
    {:a [{:value "A1",
          :b [{:value nil,
               :c [{:value "C1",
                    :d [{:value "D1"}]}]}]}]})

  (def hm1-2-combined
    {:a [{:value "A1",
          :b [{:value nil,
               :c [{:value "C1",
                    :d [{:value "D1"}
                        {:value nil}]}]}]}]})

  (def hm3
    {:a [{:value "A2",
          :b [{:value nil,
               :c [{:value nil,
                    :d [{:value nil}]}]}]}]})

  ; map -> :keyword vector
  ;vector -> maps

  (defn map-by
    "Like group-by but assumes that all the keys returned by f will be unique per item"
    [f things]
    (into {} (for [thing things] [(f thing) thing])))



  (defn merge-hierarchical-maps
    [hm1 hm2]
    ;; Merge the two maps without their values
    (let [merged-map (merge-with
                       (fn [existing-values new-values]
                         ;; Find the values in common by using map-b and then merge those maps.
                         ;; The merge-with here recursively calls into the same function
                         (->> (merge-with merge-hierarchical-maps
                                          (map-by :value existing-values)
                                          (map-by :value new-values))
                              vals
                              vec))
                       (dissoc hm1 :value)
                       (dissoc hm2 :value))]
      ;; Add value back in if it was there originally
      (if (contains? hm1 :value)
        (assoc merged-map :value (:value hm1))
        merged-map)))

  (= hm1-2-combined (value-map-merger-cleaner hm1 hm2))

  )

(defn create-branch-nodes
  [subfield remaining-subfields keywords]
  (let [keywords-by-subfield-value (group-by subfield keywords)]
    (if-let [[next-subfield & remaining-subfields] (seq remaining-subfields)]
      {subfield (for [[subfield-value subfield-keywords] keywords-by-subfield-value
                      ;; TODO handle child-branch-nodes is empty
                      :let [child-branch-nodes (create-branch-nodes
                                                 next-subfield remaining-subfields subfield-keywords)]]
                  ;; TODO value may be nil
                  (if (seq child-branch-nodes)
                    (merge child-branch-nodes
                           {:value subfield-value
                            :subfields (keys child-branch-nodes) ;; TODO
                            ; :uuid nil ;; TODO
                            ;; TODO subfields
                            })
                    {:value subfield-value
                     ; :uuid TODO
                     }))}

      ;; TODO handle leaf node
      {subfield [{:value "TODO leaf"
                  :uuid "TODO UUID"}]})))

(defn create-branch-nodes2
  [subfield remaining-subfields keywords]
  (reduce (fn [return-map keyword-map]
            )
          {}
          keywords))

(defn- parse-hierarchical-keywords
  "Returns keywords in a hierarchical map based on the provided keyword hierarchy and a list of
  keyword maps."
  [keyword-hierarchy keywords]
  (into {}
        (when-let [field (first keyword-hierarchy)]
          (when-let [unique-values (seq (distinct (keep field keywords)))]
            (let [values-to-uuids (get-leaf-values-to-uuids keyword-hierarchy field keywords)]
              {(csk/->snake_case field)
               (for [value unique-values
                     :let [uuid (get values-to-uuids value)
                           all-subfield-names (get-subfields-for-keyword (rest keyword-hierarchy)
                                                                         keywords field value)
                           subfield-maps (util/remove-nil-keys
                                           (into {}
                                                 (reverse
                                                   (for [subfield-name all-subfield-names]
                                                     (parse-hierarchical-keywords
                                                       (get-hierarchy-from-field
                                                         keyword-hierarchy subfield-name)
                                                       (filter-keywords-with-non-nil-values
                                                         (rest keyword-hierarchy)
                                                         subfield-name
                                                         (filter #(= value (field %))
                                                                 keywords)))))))]]
                 (util/remove-nil-keys
                   (util/map-keys->snake_case
                     (merge subfield-maps
                            {:subfields (seq (map #(name (csk/->snake_case %))
                                                  (keys subfield-maps)))
                             :uuid uuid
                             :value value}))))})))))

(defn- get-hierarchical-keywords
  "Returns hierarchical keywords for the provided keyword scheme. Returns a 400 error if the
  keyword scheme is invalid."
  [context keyword-scheme]
  (let [orig-keyword-scheme (csk/->kebab-case-keyword keyword-scheme)]
        (validate-keyword-scheme orig-keyword-scheme)
        (let [cmr-keyword-scheme (translate-keyword-scheme-to-cmr orig-keyword-scheme)
              gcmd-keyword-scheme (translate-keyword-scheme-to-gcmd orig-keyword-scheme)
              keywords (vals (gcmd-keyword-scheme (kf/get-gcmd-keywords-map context)))
              hierarchical-keywords (parse-hierarchical-keywords
                                      (cmr-keyword-scheme frf/nested-fields-mappings) keywords)]
          {:staus 200
           :headers {"Content-Type" (mt/format->mime-type :json)}
           :body (json/generate-string hierarchical-keywords)})))

(def keyword-api-routes
  (context "/keywords" []
    ;; Return a list of keywords for the given scheme
    (GET "/:keyword-scheme" {{:keys [keyword-scheme]} :params
                             request-context :request-context}
      (get-hierarchical-keywords request-context keyword-scheme))))


