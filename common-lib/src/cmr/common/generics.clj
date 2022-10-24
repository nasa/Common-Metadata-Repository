(ns cmr.common.generics
  "Defines utilities for new generic document pipeline. Most functions will deal
   with either returning generic config files, or lists of approved generics."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.config :as cfg]
   [cmr.common.log :as log :refer [error, info]]
   [cmr.schema-validation.json-schema :as js-validater]
   [inflections.core :as inf]))

(defn approved-generic?
  "Check to see if a requested generic is on the approved list.
   Parameters:
   * schema: schema keyword like :grid
   * version: string like 0.0.1
   Returns: true if schema and version are supported, nil otherwise"
  [schema version]
  (when (and schema version)
    (some #(= version %) (schema (cfg/approved-pipeline-documents)))))

(defn latest-approved-documents
  "Return a map of all the configured approved generics and the latest version
   string for each one.
   Return {:doc-type \"1.2.3\"}"
  []
  (reduce (fn [data item]
            (assoc data (first item) (last (second item))))
          {}
          (cfg/approved-pipeline-documents)))

(defn latest-approved-document-types
  "Return a list of configured approved generic keywords
   Returns: (:grid :dataqualitysummary ...)"
  []
  (keys (latest-approved-documents)))

(defn read-schema-file
  "Return the specific schema given the schema keyword name and version number.
   Throw an error if the file can't be read.
   Parameters:
   * file-name: [metadata | index | schema]
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [file-name generic-keyword generic-version]
  (try
    (-> "schemas/%s/v%s/%s.json"
        (format (name generic-keyword) generic-version (name file-name))
        (io/resource)
        (slurp))
    (catch Exception e
      (error
       (format (str "The %s.json file for schema [%s] version [%s] cannot be found. "
                    " - [%s] - "
                    "Please make sure that it exists. %s")
               (name file-name)
               (name generic-keyword)
               generic-version
               (format "schemas/%s/v%s/%s.json" (name generic-keyword) generic-version (name file-name))
               (.getMessage e)))
      (println "read-schema-file failed"))))

(defn read-schema-index
  "Return the schema index configuration file given the schema name and version
   number. Throw an error if the file can't be read.
   Parameters:
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [generic-keyword generic-version]
  (read-schema-file "index" generic-keyword generic-version))

(defn read-schema-specification
  "Return the schema specification file given the schema name and version number.
   Throw an error if the file can't be read.
   Parameters:
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [generic-keyword generic-version]
  (read-schema-file "schema" generic-keyword generic-version))

(defn read-schema-example
  "Return the schema example metadata file given the schema name and version
   number. Throw an error if the file can't be read.
   Parameters:
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [generic-keyword generic-version]
  (read-schema-file "metadata" generic-keyword generic-version))

(defn validate-index-against-schema
  "Validate a document, returns an array of errors if there are problems
   Parameters:
   * raw-json, json as a string to validate
   Returns: list of errors or nil"
  [raw-json]
  (let [schema-file (read-schema-file :schema :index "0.0.1")
        schema-obj (js-validater/json-string->json-schema schema-file)]
    (js-validater/validate-json schema-obj raw-json)))

(defn approved-generic-concept-prefixes
  "Return the active list of approved generic content types with the defined
   prefix in the :SubConceptType field found in the index.json file. If field is
   not defined, then X is used.
   Parameters: none, based off approved-documents?
   Return: {doc-type \"concept-prefix\"}"
  []
  (reduce (fn [data item]
            (let [generic-keyword (first item)
                  index-raw (read-schema-index generic-keyword (second item))
                  parse-errors (validate-index-against-schema index-raw)]
              (when-not (some? parse-errors)
                (assoc data
                       generic-keyword
                       (get (json/parse-string index-raw true) :SubConceptType "X")))))
          {}
          (latest-approved-documents)))

(defn read-generic-doc-file
  "Return the specific schema's documentation files given the schema keyword name and version number.
   if the file cannot be read, return an empty string which will have no impact on the API document.
   Parameters:
   * file-name: [ingest | search]
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [file-name generic-keyword generic-version]
  (try
    (-> "schemas/%s/v%s/%s.md"
        (format (name generic-keyword) generic-version (name file-name))
        (io/resource)
        (slurp))
    (catch Exception e (info (format "generic %s was skipped" generic-keyword))
           (str ""))))

(defn all-generic-docs
  "Parse over all of the generic documents and return their combined markdown as a string
  Parameters:
  * file-name: [ingest | search]
  Returns: string"
  [file-name]
  (string/join (seq (for [[k,v] (latest-approved-documents)]
                      (read-generic-doc-file file-name k (str v))))))

;; (def search-depth-map (hash-map 3 0 4 4 5 8))
;; (def ingest-depth-map (hash-map 3 8))

;; (defn search-depth-map
;;   "Returns the search mapped value"
;;   [num-pounds]
;;   (- (* 4 num-pounds) 12))

;; (defn ingest-depth-map
;;   "Returns the search mapped value"
;;   [num-pounds]
;;   (- (* 8 num-pounds) 16))

;;This assumes that we continue to use the prefix notation for markdown
;;
(defn get-toc-headers-from-markdown
  "From the markdown file string of either the ingest or search,
  dyanamically create the table of content
  Parameters:
   * generic-markdown: markdown file for a generic's ingest.md or search.md
   Returns: string"
  [generic-markdown]
  (re-seq #"\#+\s+<a\s.*" generic-markdown))

(defn fill-in-generic-name
  "Return the html for the table of contents given a generic concept
   Parameters:
   * table-of-contents-template: [ingest-table-of-contents-template | search-table-of-contents-template]
   * generic-type: grid, dataqualitysummary etc
   Returns: string"
  [table-of-contents-template generic-type]
  (-> table-of-contents-template
      (string/replace #"%generic%" generic-type)
      (string/replace #"%uppercase-generic%" (string/capitalize generic-type))
      (string/replace #"%plural-generic%" (inf/plural generic-type))
      (string/replace #"%uppercase-plural-generic%" (inf/plural (string/capitalize generic-type)))))

(defn build-markdown-toc
  "Get the markdown file for a generic based on the api string being read in
   Depth is a measure of how many spaces we need from the start of the line for the
   markdown
   Parameters:
   * depth: integer a count of the number of spaces
   * content: string, the content for the toc
   * link: string, the link to the location in the document"
  [depth content link]
  (str (apply str (repeat depth " ")) (format "* [%s](#%s)\n" link content)))

(defn get-toc-data
  "Parses out the toc information from the api.md to build the markdown for the toc
   Parameters:
   * table-of-content-headers: string list, the headers in the document
   * doc-type: string [ingest | search]"
  ([table-of-content-headers doc-type options]
   (let [depth (options (count (re-seq #"#" table-of-content-headers)))
         link (peek (re-find #"=\"(.*)\">" table-of-content-headers))
         content (peek (re-find #">\s+([^<]*)$" table-of-content-headers))]
     (build-markdown-toc depth link content))))

(defn retrieve-provider-info
  "This parses out the provider info line"
  [generic-markdown]
  (if-let [provider-info (re-find #"<!--(.*?\n.*?\n)-->" generic-markdown)] (peek provider-info) ""))

(defn format-generic-toc
  "Return the specific schema's documentation files given the schema keyword name and version number.
   if the file cannot be read, return an empty string which will have no impact on the API document.
   Parameters:
   * file-name: [ingest | search]
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [file-name generic-keyword generic-version options]
  (let [generic-markdown (read-generic-doc-file file-name generic-keyword generic-version)]
   (if (not= generic-markdown "")
     (-> generic-markdown
             get-toc-headers-from-markdown
             (as-> xs (map #(get-toc-data % (name file-name) options) xs))
             (string/join)
             (as-> xs (str (retrieve-provider-info generic-markdown) xs)))
     (str ""))))

(defn all-generic-docs-toc
  "Parse over all of the generic documents and return their combined markdown as a string
  Parameters:
  * file-name: [ingest | search]
  Returns: string"
  [file-name options]
  (string/join (seq (for [[k,v] (latest-approved-documents)] (format-generic-toc file-name k (str v) options)))))

(defn format-toc-into-doc
  "To fit generic docs into the toc a few html tags must be removed, this block will
   exist in one and only one bullet item
   Parameters: the complete markdown for ingest or search toc
   * generic-doc-toc: the full markdown data for the table of contents
   for either ingest or search
   Returns: string"
  [generic-doc-toc]
  (-> generic-doc-toc
      (string/replace #"\s*<\/li>\n?<\/ul>$" "")
      (string/replace-first #"<ul>\s*\n?\s*<li>" "")))
