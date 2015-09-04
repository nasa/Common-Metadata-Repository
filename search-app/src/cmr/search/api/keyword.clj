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

;; Sample output

(comment
  ["0dd83b2a-e83f-4a0c-a1ff-2fbdbbcce62d"
   {:uuid "0dd83b2a-e83f-4a0c-a1ff-2fbdbbcce62d",
    :variable-level-1 "MOBILE GEOGRAPHIC INFORMATION SYSTEMS",
    :term "GEOGRAPHIC INFORMATION SYSTEMS",
    :topic "DATA ANALYSIS AND VISUALIZATION",
    :category "EARTH SCIENCE SERVICES"}]

  {:category [{:value "EARTH SCIENCE SERVICES"
               :uuid "894f9116-ae3c-40b6-981d-5113de961710"
               :subfields ["topic"]
               :topic [{:value "DATA ANALYSIS AND VISUALIZATION"
                        :uuid "41adc080-c182-4753-9666-435f8b1c913f"
                        :subfields ["term"]
                        :term [{:value "GEOGRAPHIC INFORMATION SYSTEMS"
                                :uuid "794e3c3b-791f-44de-9ff3-358d8ed74733"
                                :subfields ["variable-level-1"]
                                :variable-level-1 [{:value "MOBILE GEOGRAPHIC INFORMATION SYSTEMS"
                                                    :uuid "0dd83b2a-e83f-4a0c-a1ff-2fbdbbcce62d"
                                                    }]}]}]}]}

  (def keyword-hierarchy
    {:science-keywords [:category :topic :term :variable-level-1 :variable-level-2 :variable-level-3]
     :providers [:level-0 :level-1 :level-2 :level-3 :short-name :long-name]
     :platforms [:category :series-entity :short-name :long-name]
     :instruments [:category :class :type :subtype :short-name :long-name]})

  (def keywords
    [{:uuid "0dd83b2a-e83f-4a0c-a1ff-2fbdbbcce62d",
      :variable-level-1 "MOBILE GEOGRAPHIC INFORMATION SYSTEMS",
      :term "GEOGRAPHIC INFORMATION SYSTEMS",
      :topic "DATA ANALYSIS AND VISUALIZATION",
      :category "EARTH SCIENCE SERVICES"}
     {:uuid "894f9116-ae3c-40b6-981d-5113de961710",
      :category "EARTH SCIENCE SERVICES"}
     {:uuid "41adc080-c182-4753-9666-435f8b1c913f",
      :topic "DATA ANALYSIS AND VISUALIZATION",
      :category "EARTH SCIENCE SERVICES"}
     {:uuid "e9f67a66-e9fc-435c-b720-ae32a2c3d8f5",
      :category "EARTH SCIENCE"}])

  {:category (map (fn [k-word] (util/remove-nil-keys {:value (:category k-word)
                                                      :uuid (when-not (:topic k-word)
                                                              (:uuid k-word))})) keywords)}

  (let [all-categories (keep (fn [k-word] (when-not (:topic k-word)
                                            {:value (:category k-word)
                                             :uuid  (:uuid k-word)})) keywords)
        all-topics (for [category all-categories
                         :let [topics (keep (fn [k-word]
                                              (when (and (nil? (:term k-word))
                                                         (:topic k-word)
                                                         (= (:value category) (:category k-word)))
                                                {:value (:topic k-word)
                                                 :uuid (:uuid k-word)})) keywords)]]
                     (if (seq topics)
                       (assoc category
                              :subfields ["topic"]
                              :topic topics)
                       category))]
    {:category all-topics})


  {:category (parse-hierarchical-keywords (:science-keywords keyword-hierarchy) keywords)}
  {:level-0 (parse-hierarchical-keywords (:providers keyword-hierarchy) keywords)}
  {:category (parse-hierarchical-keywords (:platforms keyword-hierarchy) keywords)}
  {:category (parse-hierarchical-keywords (:instruments keyword-hierarchy) keywords)}

  (def keywords
   (vals (kms/get-keywords-for-keyword-scheme
           {:system (cmr.indexer.system/create-system)} :science-keywords)))

  (def keywords
   (vals (kms/get-keywords-for-keyword-scheme
           {:system (cmr.indexer.system/create-system)} :providers)))

  (def test-keywords
    (take 20 keywords))

  (get-subfields-for-keyword [:level-1 :level-2 :level-3 :short-name :long-name] test-keywords
                            :level-0 "U.S. STATE/REGIONAL/LOCAL AGENCIES")
  (def keywords
   (vals (kms/get-keywords-for-keyword-scheme
           {:system (cmr.indexer.system/create-system)} :platforms)))

  (def keywords
   (vals (kms/get-keywords-for-keyword-scheme
           {:system (cmr.indexer.system/create-system)} :instruments)))

  )

(defn- is-leaf?
  "Determines if we are at the leaf point within the hierarchy for the provided keyword."
  [remaining-hierarchy k-word]
  (not (some? (seq (keep #(% k-word) remaining-hierarchy)))))

(comment

  (def test1
    [{:a "A" :b "B" :c "C"}
     {:a "D" :c "D"}
     {:a "A" :d "D"}])

  (get-subfields-for-keyword [:b :c :d] test1 :a "A")

  )
(defn- get-subfields-for-keyword
  "Figure out which subfield is directly below the current keyword. It is possible the next field
  field in the hierarchy is nil, but further down the chain there is a non-nil field."
  [remaining-hierarchy keywords field value]
  (loop [remaining-fields remaining-hierarchy
         matching-keywords (filter #(= value (field %)) keywords)
         all-subfields nil]
          (let [next-field (first remaining-fields)
                keywords-below-with-next-field (keep next-field matching-keywords)
                keywords-with-nil-next-field (filter #(nil? (next-field %)) matching-keywords)
                all-subfields (if (seq keywords-below-with-next-field)
                                (conj all-subfields next-field)
                                all-subfields)]
              (if (seq (rest remaining-fields))
                (recur (rest remaining-fields) keywords-with-nil-next-field all-subfields)
                all-subfields))))

(defn- parse-hierarchical-keywords
  "Returns keywords in a hierarchical fashion based on the provided keyword hierarchy and keywords."
  [keyword-hierarchy keywords]
  (when-let [field (first keyword-hierarchy)]
    (let [next-field (second keyword-hierarchy)

          ;; Find distinct values
          unique-values (distinct (keep field keywords))
          values-to-uuids (into {} (keep (fn [k-word]
                                           (when (is-leaf? (rest keyword-hierarchy) k-word)
                                             [(field k-word) (:uuid k-word)]))
                                         keywords))]
      (for [value unique-values
            :let [subfields (parse-hierarchical-keywords
                              (rest keyword-hierarchy)
                              (filter #(= value (field %)) keywords))
                  uuid (get values-to-uuids value)
                  value-map (util/remove-nil-keys
                              {:value value
                               :uuid uuid})]]

        (if (seq subfields)
          (assoc value-map
                 :subfields [(name next-field)]
                 next-field subfields)
          value-map)))))

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


