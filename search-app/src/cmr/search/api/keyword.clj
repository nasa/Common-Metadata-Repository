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
    [:category :topic :term :variable-level-1 :variable-level-2 :variable-level-3])

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


  {:category (parse-hierarchical-keywords keyword-hierarchy keywords)}

  (def keywords
   (vals (kms/get-keywords-for-keyword-scheme
           {:system (cmr.indexer.system/create-system)} :science-keywords)))
  )

(defn- parse-hierarchical-keywords
  "Returns keywords in a hierarchical fashion based on the provided keyword hierarchy and keywords."
  [keyword-hierarchy keywords]
  (when-let [field (first keyword-hierarchy)]
    (let [next-field (second keyword-hierarchy)]
      (let [values-for-field (keep (fn [k-word] (when (and (nil? (next-field k-word))
                                                           (field k-word))
                                                  {:value (field k-word)
                                                   :uuid  (:uuid k-word)})) keywords)]
        (for [value values-for-field
              :let [subfields (parse-hierarchical-keywords
                                (rest keyword-hierarchy)
                                (filter #(= (:value value) (field %)) keywords))]]
          (if (seq subfields)
            (assoc value
                   :subfields [(name next-field)]
                   next-field subfields)
            value))))))

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


