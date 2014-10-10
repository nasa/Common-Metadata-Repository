(ns ^{:doc "provides search related utilities."}
  cmr.system-int-test.utils.search-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
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
            [cmr.umm.dif.collection]
            [cmr.umm.iso-mends.collection]
            [cmr.umm.iso-smap.collection]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.system-int-test.data2.atom-json :as dj]
            [cmr.system-int-test.data2.kml :as dk]
            [cmr.system-int-test.data2.provider-holdings :as ph]
            [cmr.system-int-test.data2.aql :as aql]
            [cmr.system-int-test.data2.aql-additional-attribute]
            [cmr.system-int-test.data2.facets :as f]))

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

(defn safe-parse-error-xml
  [xml]
  (try
    (cx/strings-at-path (x/parse-str xml) [:error])
    (catch Exception e
      (.printStackTrace e)
      [xml])))

(defmacro get-search-failure-xml-data
  "Executes a search and returns error data that was caught, parsing the body as an xml string.
  Tests should verify the results this returns."
  [& body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (let [{{status# :status body# :body} :object} (ex-data e#)
             errors# (safe-parse-error-xml body#)]
         {:status status# :errors errors#}))))

(defn make-raw-search-query
  "Make a query to search with the given query string."
  [concept-type query]
  (let [url (url/search-url concept-type)]
    (get-search-failure-data

      (client/get (str url query) {:connection-manager (url/conn-mgr)}))))

(defn find-concepts-in-format
  "Returns the concepts in the format given."
  ([format concept-type params]
   (find-concepts-in-format format concept-type params {}))
  ([format concept-type params options]
   ;; no-snake-kebab needed for legacy psa which use camel case minValue/maxValue
   (let [url-extension (get options :url-extension)
         snake-kebab? (get options :snake-kebab? true)
         headers (get options :headers {})
         params (if snake-kebab?
                  (params->snake_case (util/map-keys csk/->snake_case_keyword params))
                  params)
         [url accept] (if url-extension
                        [(str (url/search-url concept-type) "." url-extension)]
                        [(url/search-url concept-type) format])
         response (client/get url {:accept accept
                                   :headers headers
                                   :query-params params
                                   :connection-manager (url/conn-mgr)})]
     (is (= 200 (:status response)))
     response)))

(defn- parse-timeline-interval
  "Parses the timeline response interval component into a more readable and comparable format."
  [[start end num-grans]]
  [(-> start (* 1000) tc/from-long str)
   (-> end (* 1000) tc/from-long str)
   num-grans])

(defn- parse-timeline-response
  "Parses the timeline response into a more readable and comparable format."
  [response]
  (mapv (fn [{:keys [concept-id intervals]}]
          {:concept-id concept-id
           :intervals (mapv parse-timeline-interval intervals)})
        (json/decode response true)))

(defn get-granule-timeline
  "Requests search response as a granule timeline. Parses the granule timeline response."
  ([params]
   (get-granule-timeline params {}))
  ([params options]
   (let [url-extension (get options :url-extension)
         snake-kebab? (get options :snake-kebab? true)
         headers (get options :headers {})
         params (if snake-kebab?
                  (params->snake_case (util/map-keys csk/->snake_case_keyword params))
                  params)
         ;; allow interval to be specified as a keyword
         params (update-in params [:interval] #(some-> % name))
         [url accept] (if url-extension
                        [(str (url/timeline-url) "." url-extension)]
                        [(url/timeline-url) "application/json"])
         response (get-search-failure-data
                    (client/get url {:accept accept
                                     :headers headers
                                     :query-params params
                                     :connection-manager (url/conn-mgr)}))]
     (if (= 200 (:status response))
       {:status (:status response)
        :results (parse-timeline-response (:body response))}
       response))))

(defn find-grans-csv
  "Returns the response of granule search in csv format"
  ([concept-type params]
   (find-grans-csv concept-type params {}))
  ([concept-type params options]
   (get-search-failure-data
     (find-concepts-in-format "text/csv" concept-type params options))))

(defn find-concepts-atom
  "Returns the response of a search in atom format"
  ([concept-type params]
   (find-concepts-atom concept-type params {}))
  ([concept-type params options]
   (let [response (get-search-failure-xml-data
                    (find-concepts-in-format "application/atom+xml" concept-type params options))
         {:keys [status body]} response]
     (if (= status 200)
       {:status status
        :results (da/parse-atom-result concept-type body)}
       response))))

(defn find-concepts-json
  "Returns the response of a search in json format"
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

(defn find-concepts-kml
  "Returns the response of search in KML format"
  ([concept-type params]
   (find-concepts-kml concept-type params {}))
  ([concept-type params options]
   (let [response (get-search-failure-data
                    (find-concepts-in-format "application/vnd.google-earth.kml+xml"
                                             concept-type params options))
         {:keys [status body]} response]
     (if (= status 200)
       {:status status
        :results (dk/parse-kml-results body)}
       response))))

(defn find-metadata
  "Returns the response of concept search in a specific metadata XML format."
  ([concept-type format-key params]
   (find-metadata concept-type format-key params {}))
  ([concept-type format-key params options]
   (get-search-failure-xml-data
     (let [format-mime-type (mime-types/format->mime-type format-key)
           response (find-concepts-in-format format-mime-type concept-type params options)
           body (:body response)
           parsed (x/parse-str body)
           metadatas (for [match (drop 1 (str/split body #"(?ms)<result "))]
                       (second (re-matches #"(?ms)[^>]*>(.*)</result>.*" match)))
           items (map (fn [result metadata]
                        (let [{{:keys [concept-id collection-concept-id revision-id granule-count has-granules
                                       echo_dataset_id echo_granule_id]} :attrs} result]
                          (util/remove-nil-keys
                            {:concept-id concept-id
                             :revision-id (when revision-id (Long. ^String revision-id))
                             :format format-key
                             :collection-concept-id collection-concept-id
                             :echo_dataset_id echo_dataset_id
                             :echo_granule_id echo_granule_id
                             :granule-count (when granule-count (Long. ^String granule-count))
                             :has-granules (when has-granules (= has-granules "true"))
                             :metadata (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" metadata)})))
                      (cx/elements-at-path parsed [:result])
                      metadatas)
           facets (f/parse-facets-xml (cx/element-at-path parsed [:facets]))]
       (util/remove-nil-keys {:items items
                              :facets facets})))))

(defmulti parse-reference-response
  (fn [echo-compatible? response]
    echo-compatible?))

(defmethod parse-reference-response :default
  [_ response]
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
        facets (f/parse-facets-xml
                 (cx/element-at-path parsed [:facets]))]
    (util/remove-nil-keys
      {:refs refs
       :hits hits
       :took took
       :facets facets})))

(defmethod parse-reference-response true
  [_ response]
  (let [parsed (-> response :body x/parse-str)
        references-type (get-in parsed [:attrs :type])
        refs (map (fn [ref-elem]
                    (util/remove-nil-keys
                      {:id (cx/string-at-path ref-elem [:id])
                       :name (cx/string-at-path ref-elem [:name])
                       :location (cx/string-at-path ref-elem [:location])
                       :score (cx/double-at-path ref-elem [:score])}))
                  (cx/elements-at-path parsed [:reference]))]
    (util/remove-nil-keys
      {:refs refs
       :type references-type})))

(defn- parse-echo-facets-response
  "Returns the parsed facets by parsing the given facets according to catalog-rest facets format"
  [response]
  (let [parsed (-> response :body x/parse-str)]
    (f/parse-echo-facets-xml parsed)))

(defn- parse-refs-response
  "Parse the find-refs response based on expected format and retruns the parsed result"
  [concept-type params options]
  (let [;; params is not a map for catalog-rest additional attribute style tests,
        ;; we cannot destructing params as a map for the next two lines.
        echo-compatible (:echo-compatible params)
        include-facets (:include-facets params)
        response (find-concepts-in-format "application/xml" concept-type params options)]
    (if (and echo-compatible include-facets)
      (parse-echo-facets-response response)
      (parse-reference-response echo-compatible response))))

(defn find-refs
  "Returns the references that are found by searching with the input params"
  ([concept-type params]
   (find-refs concept-type params {}))
  ([concept-type params options]
   (get-search-failure-xml-data
     (parse-refs-response concept-type params options))))

(defn find-refs-with-post
  "Returns the references that are found by searching through POST request with the input params"
  [concept-type params]
  (get-search-failure-xml-data
    (let [response (client/post (url/search-url concept-type)
                                {:accept "application/xml"
                                 :content-type "application/x-www-form-urlencoded"
                                 :body (codec/form-encode params)
                                 :throw-exceptions false
                                 :connection-manager (url/conn-mgr)})]
      (parse-reference-response (:echo-compatible params) response))))

(defn find-refs-with-aql-string
  ([aql]
   (find-refs-with-aql-string aql {}))
  ([aql options]
   (get-search-failure-xml-data
     (let [response (client/post (url/aql-url)
                                 (merge {:accept "application/xml"
                                         :content-type "application/xml"
                                         :body aql
                                         :query-params {:page-size 100}
                                         :connection-manager (url/conn-mgr)}
                                        options))]
       (parse-reference-response (get-in options [:query-params :echo_compatible]) response)))))

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
   (let [url-extension (get options :url-extension)
         concept-type (cs/concept-prefix->concept-type (subs concept-id 0 1))
         format-mime-type (or (:accept options) "application/echo10+xml")
         url (url/retrieve-concept-url concept-type concept-id)
         url (if url-extension
               (str url "." url-extension)
               url)]
     (client/get url (merge {:accept (when-not url-extension format-mime-type)
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
         {:keys [url-extension]} options
         params (params->snake_case (util/map-keys csk/->snake_case_keyword params))
         echo-compatible? (if (:echo_compatible params) true false)
         [url accept] (if url-extension
                        [(str (url/provider-holdings-url) "." url-extension)]
                        [(url/provider-holdings-url) format-mime-type])
         response (client/get url {:accept accept
                                   :query-params params
                                   :connection-manager (url/conn-mgr)})
         {:keys [status body headers]} response]
     (if (= status 200)
       {:status status
        :headers headers
        :results (ph/parse-provider-holdings format-key echo-compatible? body)}
       response))))
