(ns cmr.search.api.keyword
  "Defines the HTTP URL routes for the keyword endpoint in the search application."
  (:require [compojure.core :refer :all]
            [cheshire.core :as json]
            [camel-snake-kebab.core :as csk]
            [cmr.common-app.services.kms-fetcher :as kf]
            [cmr.transmit.kms :as kms]
            [cmr.common.mime-types :as mt]
            [cmr.common.util :as util]
            [cmr.common.services.errors :as errors]
            [cmr.search.services.query-execution.facets-results-feature :as frf]))

(defn- validate-keyword-scheme
  "Throws a service error if the provided keyword-scheme is invalid."
  [keyword-scheme]
  (when-not (some? (keyword-scheme kms/keyword-scheme->field-names))
    (errors/throw-service-error
      :bad-request (format "The keyword scheme [%s] is not supported. Valid schemes are: %s"
                           (name keyword-scheme)
                           (pr-str (map name (keys kms/keyword-scheme->field-names)))))))

(defn- is-leaf?
  "Determines if we are at the leaf point within the hierarchy for the provided keyword."
  [remaining-hierarchy k-word]
  (not (some? (seq (keep #(% k-word) remaining-hierarchy)))))

(defn- get-leaf-values-to-uuids
  "Returns a map of values for the given field to the UUID for that value. If the provied subfield
  is not at the leaf level for a keyword, the value will not be included in the map."
  [keyword-hierarchy field keywords]
  (into {} (keep (fn [k-word]
                   (when (is-leaf? (rest keyword-hierarchy) k-word)
                     [(field k-word) (:uuid k-word)]))
                 keywords)))

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
  [keyword-hierarchy field]
  (let [field-index (.indexOf keyword-hierarchy field)]
    (filter #(<= field-index (.indexOf keyword-hierarchy %)) keyword-hierarchy)))


(defn- parse-hierarchical-keywords
  "Returns keywords in a hierarchical fashion based on the provided keyword hierarchy and keywords."
  [keyword-hierarchy keywords]
  (when-let [field (first keyword-hierarchy)]
    (let [unique-values (distinct (keep field keywords))
          values-to-uuids (get-leaf-values-to-uuids keyword-hierarchy field keywords)]
      (util/remove-nil-keys
        {field
         (seq (for [value unique-values
                    :let [uuid (get values-to-uuids value)
                          all-subfield-names (get-subfields-for-keyword (rest keyword-hierarchy)
                                                                        keywords field value)
                          subfield-maps (util/remove-nil-keys
                                          (into {}
                                                (reverse
                                                  (for [subfield-name all-subfield-names]
                                                    (parse-hierarchical-keywords
                                                      (get-hierarchy-from-field keyword-hierarchy
                                                                                subfield-name)
                                                      (filter #(= value (field %)) keywords))))))]]
                (util/remove-nil-keys
                  (merge subfield-maps
                         {:subfields (seq (map name (keys subfield-maps)))
                          :uuid uuid
                          :value value}))))}))))

(def keyword-api-routes
  (context "/keywords" []
    ;; Return a list of keywords for the given scheme
    (GET "/:keyword-scheme" {{:keys [keyword-scheme] :as params} :params
                             request-context :request-context}
      (let [keyword-scheme (csk/->kebab-case-keyword keyword-scheme)]
        (validate-keyword-scheme keyword-scheme)
        (let [keywords (vals (keyword-scheme (kf/get-gcmd-keywords-map request-context)))]
          {:staus 200
           :headers {"Content-Type" (mt/format->mime-type :json)}
           :body (json/generate-string keywords)})))))

;; TODO Move into tests. Add unit tests for is-leaf? and get-subfields-for-keyword. Integration
;; test to test parse-hierarchical-keywords
(comment

  (do
    (def test1
      [{:a "A1" :b "B1" :c "C1"}
       {:a "A2" :c "C2"}
       {:a "A1" :d "D1"}])

    (def test2
      [{:a "A1" :b "B1"}])

    (def test3
      [{:a "A1" :c "C1"}]) )

  (get-subfields-for-keyword [:b :c :d] test1 :a "A")
  {:a (parse-hierarchical-keywords [:a :b :c :d] test1)}

  {:a [{:value "A1"
        :subfields ["b" "d"]
        :b [{:value "B1"
             :subfields ["c"]
             :c [{:value "C1"}]}]
        :d [{:value "D1"}]}
       {:value "A2"
        :subfields ["c"]
        :c [{:value "C2"}]}]}

  (get-subfields-for-keyword [:b :c :d] test1 :a "A1")
  (get-subfields-for-keyword [:b :c :d] test1 :a "A2")

  {:a (parse-hierarchical-keywords [:a :b :c :d] test1)}

  (= (parse-hierarchical-keywords [:a :b :c :d] test1)
     {:a [{:value "A1"
           :subfields ["b" "d"]
           :b [{:value "B1"
                :subfields ["c"]
                :c [{:value "C1"}]}]
           :d [{:value "D1"}]}
          {:value "A2"
           :subfields ["c"]
           :c [{:value "C2"}]}]})

  (def keywords
    (vals (kms/get-keywords-for-keyword-scheme
            {:system (cmr.indexer.system/create-system)} :science-keywords)))

  {:category (parse-hierarchical-keywords (:science-keywords frf/nested-fields-mappings) keywords)}


  )


