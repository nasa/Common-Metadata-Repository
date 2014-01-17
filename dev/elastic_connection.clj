(ns elastic-connection
  (:require [cmr.common.lifecycle :as lifecycle]
            [clojure.string :as s]
            ; [clojurewerkz.elastisch.rest  :as es]
            ; [clojurewerkz.elastisch.rest.index :as esi]
            ; [clojurewerkz.elastisch.rest.document :as esd]
            ; [clojurewerkz.elastisch.rest.response :as esrsp]

            [clojurewerkz.elastisch.rest.bulk :as bulk]

            [clojurewerkz.elastisch.native  :as es]
            [clojurewerkz.elastisch.native.index :as esi]
            [clojurewerkz.elastisch.native.document :as esd]
            [clojurewerkz.elastisch.native.response :as esrsp]

            [clojurewerkz.elastisch.query :as q]
            ))


(def string-value-index "string-vals")
(def string-value-type "single-string")

(def spatial-index "spatial")
(def spatial-type "item")


(def indexes {string-value-index ;; the index name
              {:mappings
               {string-value-type ;; a type name
                {:properties {:value  ;; a field within the type
                              {:type "string"
                               :index "not_analyzed"
                               :store "true"}}}}}
              spatial-index
              {:mappings
               {spatial-type
                ;; We have to store the the values to get them back in the same order we stored them in
                ;; TODO disable _source
                {:properties {:ords {:type "double" :store "true"}}}}}})

(defn- create-indexes
  "Creates the configured indexes"
  []
  (doseq [[index-name {:keys [mappings]}] indexes]
    (if (esi/exists? index-name)
      (do
        (println "Updating index" index-name)
        (doseq [[type properties] mappings]
          (esi/update-mapping index-name type :mapping properties)))
      (do
        (println "Creating index" index-name )
        (esi/create index-name :settings {"index" {"number_of_shards" 1}} :mappings mappings)))))

(defn- extract-sources-from-response
  "Extracts the source documents from the query response."
  [resp]
  (->> resp :hits :hits (map :_source)))

(defn- script-filter
  "Creates an elastic search filter that searches using a native script with the given params"
  [script-name params]
  {:script {:script script-name
            :params params
            :lang "native"}})

(defn all-items
  "Retrieves all items from the given index and index type."
  [index-name indexed-type]
  (extract-sources-from-response
    (esd/search index-name indexed-type :size 2000)))

(defn delete-all
  "Deletes all items in the specified index."
  [index-name indexed-type]
  (esd/delete-by-query index-name indexed-type (q/match-all)))

;; String functions

(defn index-string
  "Indexes a string value into the string value index."
  [id & values]
  (esd/put string-value-index string-value-type (str id) {:value values}))

(defn index-bulk-strings
  "TODO"
  [docs]
  (bulk/bulk-with-index
    string-value-index
    (bulk/bulk-index docs)))

(defn search-string-term
  "Searches the string documents using normal string term matching."
  [search-value]
  (extract-sources-from-response
    (esd/search string-value-index [string-value-type]
                :filter {:term {:value search-value}})))


(defn search-string-script
  "Searches the string documents using the string match script."
  [search-value]
  (extract-sources-from-response
    (esd/search string-value-index [string-value-type]
                :filter (script-filter "string_match"
                                       {:field "value" :search-string2 search-value}))))

;; Spatial functions.
;; ords are just a list of double values (lon1, lat1, lon2, lat2, ...)

(defn index-spatial
  [id ords]
  (esd/put spatial-index spatial-type (str id) {:ords ords}))

(defn search-spatial-script
  "Searches the spatial records using the ordinates."
  [ords]
  (extract-sources-from-response
    (esd/search spatial-index [spatial-type]
                :filter (script-filter "spatial"
                                       {:ords (s/join "," ords)}))))


(comment
  (do
    (index-string 1 "foo")
    (index-string 2 "bar")
    (index-string 3 "foo" "moo" "mah")
    (index-string 4 "chew" "chomp" "cheap"))

  (do
    (def random (java.util.Random.))

    ;define characters list to use to generate string
    (def chars
      (map char (concat (range 48 58) (range 66 92) (range 97 123))))

    ;generates 1 random character
    (defn random-char []
      (nth chars (.nextInt random (count chars))))

    ; generates random string of length characters
    (defn random-string [length]
      (apply str (take length (repeatedly random-char)))))

  (random-string 10)

  (time (doseq [n (range 0 10000)]
          (index-string n (random-string 10))))

  (time
    (->> (map
           (fn [n]
             {:id n
              :_type string-value-type
              :value (random-string 10)})
           (range 0 10000))
         index-bulk-strings
         :took))



  (time (search-string-term "foo"))
  (time (search-string-script "foo"))

  )

(defrecord ElasticConnection
  [
   connection
  ]

  lifecycle/Lifecycle

  (start
    [this system]
    (let [node (get-in system [:elastic-server :node])
          client-connection (es/connect-to-local-node! node)
          ; client-connection (es/connect! "http://localhost:9234")
          new-this (assoc this :connection client-connection)]
      (create-indexes)
      new-this))

  (stop
    [this system]
   this))

(defn create-connection []
  (->ElasticConnection nil))