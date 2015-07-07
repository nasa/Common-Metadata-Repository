(ns cmr.umm-spec.record-generator
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.string :as str]))


(def umm-cmn-schema (io/resource "umm-cmn-json-schema.json"))

(def umm-c-schema (io/resource "umm-c-json-schema.json"))

(defn- load-schema
  [schema-resource]
  (json/decode (slurp schema-resource) true))

(defn wrap-line
  "Wraps lines so they are at most line-size characters long. Returns a list of lines"
  [line-size text]
  (loop [line nil
         lines []
         [word & words] (str/split text #"\s+")]
    (cond
      ;; No more words means we are finished
      (nil? word)
      (if line
        (conj lines line)
        ;; text was empty
        lines)

      ;; First word of the line
      (nil? line)
      (recur word lines words)

      ;; Word can fit on the current line
      (<= (+ (count line) 1 (count word)) line-size)
      (recur (str line " " word) lines words)

      ;; Word can't fit on the current lin
      :else
      (recur word (conj lines line) words))))


(def sample-text
  "In olden times when wishing still helped one, there lived a king whose daughters were all beautiful, but the youngest was so beautiful that the sun itself, which has seen so much, was astonished whenever it shone in her face. Close by the king's castle lay a great dark forest, and under an old lime-tree in the forest was a well, and when the day was very warm, the king's child went out into the forest and sat down by the side of the cool fountain, and when she was bored she took a golden ball, and threw it up on high and caught it, and this ball was her favorite")

(def MAX_LINE_SIZE 100)

(defn- generate-comment
  [indent-size text]
  (let [indent+comment (str (str/join (repeat indent-size " ")) ";; ")
        max-comment-line-size (- MAX_LINE_SIZE (count indent+comment))]
    (str indent+comment
         (str/join (str "\n" indent+comment) (wrap-line max-comment-line-size text)))))

(defn- generate-doc-string
  [text]
  (let [indent "  "
        max-doc-line-size (- MAX_LINE_SIZE (count indent))]
    (str indent "\""
         (->> (str/replace text "\"" "\\\"")
              (wrap-line max-doc-line-size)
              (str/join (str "\n" indent)))
         "\"")))

(comment
  (do
    (println)
    (println (generate-comment 4 text)))
  )

(defn generate-record-field
  [{:keys [field-name description]}]

  ;; TODO we could add additional documentation here on the type

  (let [description-str (when description (generate-comment 3 description))
        field-str (str "   " field-name)]
    (if description-str
      (str description-str "\n" field-str)
      field-str)))


(defn generate-record
  [{:keys [record-name fields description]}]
  (str/join
    "\n"
    (concat
      (when description
        [(generate-comment 0 description)])
      [(str "(defrecord " record-name)
       "  ["]
      [(str/join "\n\n" (map generate-record-field fields))]
      ["  ])"
       (str "(record-pretty-printer/enable-record-pretty-printing " record-name ")")])))

(defn definition->record
  "Converts a JSON Schema definition into a record description if it's appropriate to have a record
  for it. Returns nil otherwise."
  [type-name type-def]
  (when (= "object" (:type type-def))
    {:record-name (name type-name)
     :description (:description type-def)
     :fields (for [[property-name prop-def] (:properties type-def)]
               {:field-name (name property-name)
                :description (:description prop-def)})}))

(defn generate-clojure-records
  [schema]
  (let [definitions (:definitions schema)
        definitions (if (:title schema)
                      ;; The schema itself can define a top level object
                      (cons [(keyword (:title schema)) (dissoc schema :definitions)]
                            definitions)
                      definitions)
        records-strings (->> definitions
                             (map #(apply definition->record %))
                             (remove nil?)
                             (map generate-record))]
    (str/join "\n\n" records-strings)))

(defn generate-ns-declaration
  [{:keys [the-ns description]}]
  (format "(ns %s\n %s\n (:require [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))"
          (name the-ns)
          (generate-doc-string description)))

(defn generate-clojure-records-file
  [{:keys [the-ns] :as ns-def} schema]
  (let [file-name (str "src/"
                       (-> the-ns
                           name
                           (str/replace "." "/")
                           (str/replace "-" "_"))
                       ".clj")
        file-contents (str (generate-ns-declaration ns-def)
                           "\n\n"
                           (generate-clojure-records schema))]

    (.. (io/file file-name) getParentFile mkdirs)
    (spit file-name file-contents)))

(comment


  ;; TODOs
  ;; - generate records with fields in the same order as they are defined in the file.


  (do
    (generate-clojure-records-file {:the-ns 'cmr.umm-spec.models.common
                                    :description "Defines UMM Common clojure records."}
                                   (load-schema umm-cmn-schema))

    (generate-clojure-records-file {:the-ns 'cmr.umm-spec.models.collection
                                    :description "Defines UMM-C clojure records."}
                                   (load-schema umm-c-schema)))
  (-> umm-c-schema
      load-schema
      (dissoc :definitions
              :properties))
  (dissoc (load-schema umm-c-schema) :definitions)

  (let [schema (load-schema umm-c-schema)]
    (->> schema
         :definitions
         ;; The schema itself can define a top level object
         (cons [(:title schema) schema])

         (drop 1)
         ))

  (do
    (println)
    (println (generate-clojure-records (load-schema mini-schema))))

  (do
    (println)
    (println (generate-clojure-records (load-schema umm-cmn-schema))))

  (do
    (println)
    (println (generate-clojure-records (load-schema umm-c-schema))))




  )


