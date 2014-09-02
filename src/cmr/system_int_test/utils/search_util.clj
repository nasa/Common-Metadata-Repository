(ns ^{:doc "provides search related utilities."}
  cmr.system-int-test.utils.search-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as json]
            [cmr.common.concepts :as cs]
            [cmr.common.mime-types :as mime-types]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.common.util :as util]
            [camel-snake-kebab :as csk]
            [clojure.set :as set]
            [clojure.walk]
            [ring.util.codec :as codec]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.dif.collection :as dif-c]
            [cmr.umm.iso-mends.collection :as iso-mends-c]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.system-int-test.data2.atom-json :as dj]
            [cmr.system-int-test.data2.provider-holdings :as ph]
            [cmr.system-int-test.data2.aql :as aql]
            [cmr.system-int-test.data2.aql-additional-attribute]))

(defn csv->tuples
  "Convert a comma-separated-value string into a set of tuples to be use with find-refs."
  [csv]
  (let [[type name min-value max-value] (str/split csv #"," -1)
        tuples [["attribute[][name]" name]
                ["attribute[][type]" type]]]
    (cond
      (and (not (empty? max-value)) (not (empty? min-value)))
      (into tuples [["attribute[][minValue]" min-value]
                    ["attribute[][maxValue]" max-value]])
      (not (empty? max-value))
      (conj tuples ["attribute[][maxValue]" max-value])

      max-value ;; max-value is empty but not nil
      (conj tuples ["attribute[][minValue]" min-value])

      :else ; min-value is really value
      (conj tuples ["attribute[][value]" min-value]))))

(defn params->snake_case
  "Converts search parameters to snake_case"
  [params]
  (->> params
       (util/map-keys
         (fn [k]
           (let [k (if (keyword? k) (name k) k)]
             (-> k
                 csk/->snake_case
                 (str/replace "_[" "[")
                 (str/replace "_]" "]")))))
       clojure.walk/keywordize-keys))

(deftest params->snake_case-test
  (is (= {(keyword "archive_center[]") ["SEDAC AC" "Larc" "Sedac AC"],
          (keyword "options[archive_center][and]") "false"
          :foo_bar "chew"}
         (params->snake_case
           {"archive-center[]" ["SEDAC AC" "Larc" "Sedac AC"],
            "options[archive-center][and]" "false"
            :foo-bar "chew"}))))

(defmacro get-search-failure-data
  "Executes a search and returns error data that was caught.
  Tests should verify the results this returns."
  [& body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (let [{{status# :status body# :body} :object} (ex-data e#)
             errors# (:errors (json/decode body# true))]
         {:status status# :errors errors#}))))

(def mime-type->extension
  {"application/json" "json"
   "application/xml" "xml"
   "application/echo10+xml" "echo10"
   "application/iso+xml" "iso"
   "application/iso-smap+xml" "iso_smap"
   "application/iso-mends+xml" "iso_mends"
   "application/iso19115+xml" "iso19115"
   "application/dif+xml" "dif"
   "text/csv" "csv"
   "application/atom+xml" "atom"})

(defn find-concepts-in-format
  "Returns the concepts in the format given."
  ([format concept-type params]
   (find-concepts-in-format format concept-type params {}))
  ([format concept-type params options]
   ;; no-snake-kebab needed for legacy psa which use camel case minValue/maxValue

   (let [{:keys [format-as-ext? snake-kebab?]
          :or {:format-as-ext? false
               :snake-kebab? true}} options
         headers (get options :headers {})
         params (if snake-kebab?
                  (params->snake_case (util/map-keys csk/->snake_case_keyword params))
                  params)
         [url accept] (if format-as-ext?
                        [(str (url/search-url concept-type) "." (mime-type->extension format))]
                        [(url/search-url concept-type) format])
         response (client/get url {:accept accept
                                   :headers headers
                                   :query-params params
                                   :connection-manager (url/conn-mgr)})]
     (is (= 200 (:status response)))
     response)))

(defn find-grans-csv
  "Returns the response of granule search in csv format"
  ([concept-type params]
   (find-grans-csv concept-type params {}))
  ([concept-type params options]
   (get-search-failure-data
     (find-concepts-in-format "text/csv" concept-type params options))))

(defn find-concepts-atom
  "Returns the response of granule search in atom format"
  ([concept-type params]
   (find-concepts-atom concept-type params {}))
  ([concept-type params options]
   (let [response (get-search-failure-data
                    (find-concepts-in-format "application/atom+xml" concept-type params options))
         {:keys [status body]} response]
     (if (= status 200)
       {:status status
        :results (da/parse-atom-result concept-type body)}
       response))))

(defn find-concepts-json
  "Returns the response of granule search in json format"
  ([concept-type params]
   (find-concepts-json concept-type params {}))
  ([concept-type params options]
   (let [response (get-search-failure-data
                    (find-concepts-in-format "application/json" concept-type params options))
         {:keys [status body]} response]
     (if (= status 200)
       {:status status
        :results (dj/parse-json-result concept-type body)}
       response))))

(defn- parse-facets-xml
  "Converts an xml element into a sequence of facet maps containing field and value counts"
  [facets-elem]
  (when facets-elem
    (when-let [facet-elems (cx/elements-at-path facets-elem [:facet])]
      (for [facet-elem facet-elems]
        (let [field (get-in facet-elem [:attrs :field])
              value-elems (cx/elements-at-path facet-elem [:value])]
          {:field field
           :value-counts (for [value-elem value-elems]
                           [(first (:content value-elem))
                            (Long. ^String (get-in value-elem [:attrs :count]))])})))))

(defn find-metadata
  "Returns the response of concept search in a specific metadata XML format."
  ([concept-type format-key params]
   (find-metadata concept-type format-key params {}))
  ([concept-type format-key params options]
   (get-search-failure-data
     (let [format-mime-type (mime-types/format->mime-type format-key)
           response (find-concepts-in-format format-mime-type concept-type params options)
           body (:body response)
           parsed (x/parse-str body)
           metadatas (for [match (drop 1 (str/split body #"(?ms)<result "))]
                       (second (re-matches #"(?ms)[^>]*>(.*)</result>.*" match)))
           items (map (fn [result metadata]
                        (let [{{:keys [concept-id collection-concept-id revision-id granule-count has-granules]} :attrs} result]
                          (util/remove-nil-keys
                            {:concept-id concept-id
                             :revision-id (Long. ^String revision-id)
                             :format format-key
                             :collection-concept-id collection-concept-id
                             :granule-count (when granule-count (Long. ^String granule-count))
                             :has-granules (when has-granules (= has-granules "true"))
                             :metadata (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" metadata)})))
                      (cx/elements-at-path parsed [:result])
                      metadatas)
           facets (parse-facets-xml (cx/element-at-path parsed [:facets]))]
       (util/remove-nil-keys {:items items
                              :facets facets})))))

(defn find-refs-json
  "Finds references using the JSON format. This will eventually go away as the json response format
  should be similar to the ATOM XML format."
  ([concept-type params]
   (find-refs-json concept-type params {}))
  ([concept-type params options]
   (get-search-failure-data
     (-> (find-concepts-in-format "application/json" concept-type params options)
         :body
         (json/decode true)
         (set/rename-keys {:references :refs})))))


(defn- parse-reference-response
  [response]
  (let [parsed (-> response :body x/parse-str)
        hits (cx/long-at-path parsed [:hits])
        took (cx/long-at-path parsed [:took])
        refs (map (fn [ref-elem]
                    (util/remove-nil-keys
                      {:id (cx/string-at-path ref-elem [:id])
                       :name (cx/string-at-path ref-elem [:name])
                       :revision-id (cx/long-at-path ref-elem [:revision-id])
                       :location (cx/string-at-path ref-elem [:location])
                       :granule-count (cx/long-at-path ref-elem [:granule-count])
                       :has-granules (cx/bool-at-path ref-elem [:has-granules])
                       :score (cx/double-at-path ref-elem [:score])}))
                  (cx/elements-at-path parsed [:references :reference]))
        facets (parse-facets-xml
                 (cx/element-at-path parsed [:facets]))]
    (util/remove-nil-keys
      {:refs refs
       :hits hits
       :took took
       :facets facets})))

(defn find-refs
  "Returns the references that are found by searching with the input params"
  ([concept-type params]
   (find-refs concept-type params {}))
  ([concept-type params options]
   (get-search-failure-data
     (parse-reference-response
       (find-concepts-in-format "application/xml" concept-type params options)))))

(defn find-refs-with-post
  "Returns the references that are found by searching through POST request with the input params"
  [concept-type params]
  (get-search-failure-data
    (let [response (client/post (url/search-url concept-type)
                                {:accept "application/xml"
                                 :content-type "application/x-www-form-urlencoded"
                                 :body (codec/form-encode params)
                                 :throw-exceptions false
                                 :connection-manager (url/conn-mgr)})]
      (parse-reference-response response))))

(defn find-refs-with-aql-string
  ([aql]
   (find-refs-with-aql-string aql {}))
  ([aql options]
   (get-search-failure-data
     (let [response (client/post (url/aql-url)
                                 (merge {:accept "application/xml"
                                         :content-type "application/xml"
                                         :body aql
                                         :query-params {:page-size 100}
                                         :connection-manager (url/conn-mgr)}
                                        options))]
       (parse-reference-response response)))))

(defn find-refs-with-aql
  "Returns the references that are found by searching through POST request with aql for the given conditions"
  ([concept-type conditions]
   (find-refs-with-aql concept-type conditions {}))
  ([concept-type conditions data-center-condition]
   (find-refs-with-aql concept-type conditions data-center-condition {}))
  ([concept-type conditions data-center-condition options]
   (find-refs-with-aql-string (aql/generate-aql concept-type data-center-condition conditions) options)))

(defn get-concept-by-concept-id
  "Returns the concept metadata by searching metadata-db using the given cmr concept id"
  ([concept-id]
   (get-concept-by-concept-id concept-id {}))
  ([concept-id options]
   (let [format-as-ext? (:format-as-ext? options)
         concept-type (cs/concept-prefix->concept-type (subs concept-id 0 1))
         format-mime-type (or (:accept options) "application/echo10+xml")
         url (url/retrieve-concept-url concept-type concept-id)
         url (if format-as-ext?
               (str url "." (mime-type->extension format-mime-type))
               url)]
     (client/get url (merge {:accept (when-not format-as-ext? format-mime-type)
                             :throw-exceptions false
                             :connection-manager (url/conn-mgr)}
                            options)))))

(defn provider-holdings-in-format
  "Returns the provider holdings."
  ([format-key]
   (provider-holdings-in-format format-key {} {}))
  ([format-key params]
   (provider-holdings-in-format format-key params {}))
  ([format-key params options]
   (let [format-mime-type (mime-types/format->mime-type format-key)
         {:keys [format-as-ext?]
          :or {:format-as-ext? false}} options
         params (params->snake_case (util/map-keys csk/->snake_case_keyword params))
         [url accept] (if format-as-ext?
                        [(str (url/provider-holdings-url) "." (mime-type->extension format-mime-type))]
                        [(url/provider-holdings-url) format-mime-type])
         response (client/get url {:accept accept
                                   :query-params params
                                   :connection-manager (url/conn-mgr)})
         {:keys [status body]} response]
     (if (= status 200)
       {:status status
        :results (ph/parse-provider-holdings format-key body)}
       response))))
