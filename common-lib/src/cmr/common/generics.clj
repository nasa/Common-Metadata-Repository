(ns cmr.common.generics
  "Defines utilities for new generic document pipeline. Most functions will deal
   with either returning generic config files, or lists of approved generics."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.config :as cfg]
   [cmr.common.log :as log :refer (error)]
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
;;TODO: Make sure comments have two semicolons on them but, the comment below is likely going to go away
;;HTML templates for generic table of content items
;; (def ingest-table-of-contents-template (slurp (io/resource "ingest-table-of-contents-template.txt")))
;; (def search-table-of-contents-template (slurp (io/resource "search-table-of-contents-template.txt")))

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
    (catch Exception e (str ""))))

(defn all-generic-docs
  "Parse over all of the generic documents and return their combined markdown as a string
  Parameters:
  * file-name: [ingest | search]
  Returns: string"
  [file-name]
  (string/join (seq (for [[k,v] (latest-approved-documents)] (read-generic-doc-file file-name k (str v))))))

(defn get-list-of-generics-with-documenation
  "Retrieve the names of the generics that have documentation files. Re-seq returns both the full match and the group, so parse out all of the odd numbered indicies
   so that only the group matches remain
   Parameters:
   * generic-markdown: a slurped markdown file
   Returns: string"
  [generic-markdown]
  (take-nth 2 (rest (flatten (re-seq #"### <a name=\"create-update-([a-z]+)" generic-markdown)))))

;; (defn get-list-of-generics-with-documenation2
;;   "Retrieve the names of the generics that have documentation files. Re-seq returns both the full match and the group, so parse out all of the odd numbered indicies
;;    so that only the group matches remain
;;    Parameters:
;;    * generic-markdown: a slurped markdown file
;;    Returns: string"
;;   [generic-markdown]
;;   (take-nth 2 (rest (flatten (re-seq #"href=\"#.*-(.*)\"" generic-markdown)))))

(defn fill-in-generic-name
  "Return the html for the table of contents given a generic concept
   Parameters:
   * table-of-contents-template: [ingest-table-of-contents-template | search-table-of-contents-template]
   Returns: string"
  [table-of-contents-template generic-type]
  (-> table-of-contents-template
      (string/replace #"%generic%" generic-type)
      (string/replace  #"%uc-generic%" (string/capitalize generic-type))
      (string/replace  #"%plural-generic%" (inf/plural generic-type))
      (string/replace  #"%uc-plural-generic%" (inf/plural (string/capitalize generic-type)))))

;; (defn all-generic-table-of-contentsdfasdf
;;   "Parse over all of the generic documenass and return their combined html for the table of contents as a string
;;    we use ingest to retrieve the list of generics with documenation for both search and ingest as it would be expected both documents would be in the schema dir
;;    Parameters:
;;    * table-of-contents-template: [ingest-table-of-contents-template | search-table-of-contents-template]
;;    Returns: string"
;;   [table-of-contents-template]
;;   (string/join (mapv #(table-of-contents-html table-of-contents-template %) (get-list-of-generics-with-documenation (all-generic-docs "ingest")))))

(defn get-table-contents-headers-from-markdown
  "From the markdown file string of either the ingest or search, dyanamically create the table of content"
  [generic-markdown]
  (re-seq #"\#+\s+<a\s.*" generic-markdown))

;; (defn get-html-from-table-of-contents-headers
;;   "From the list of headers, retrieve the desired html for the table of contents"
;;   [list-of-headers]
;;   (let [mylist list-of-headers]
;;   ;(conj list-of-headers "hi"))
;;     (str "</ul></li></ul><li>grids<ul><li>/providers/&lt;provider-id&gt;%grids%&lt;native-id&gt;<ul>" (string/join (map #(str % "</a></li>")
;;                        (map #(string/replace % #"</a>" "") (map #(string/replace % #"#\s+<a\s+name=\"" "<li><a href=\"#") mylist)))))))
      ;; ;Replace the tag with HTML tag
      ;; (string/replace #"#\s+<a\s+name=" "<li><a href=")
      ;; (str "</a></li>")
      ;; (string/replace #"\\" ""))
;; (defn usingas
;;   "Process the markdown into useable html"
;;   [table-contents-headers]
;;   (-> table-contents-headers
;;   (string/join table-contents-headers)
;;   (as-> xs (string/replace xs #"#\s+<a\s+name=\"" "<li><a href=\"#"))
;;   (as-> xs (string/replace xs #"</a>" ""))))
; Make the map be (M + 4 where M starts at 3)
(def depth-map (hash-map 3 0 4 4 5 8))
(def ingest-depth-map (hash-map 3 8))

;For each of the depth in the map get the output
(defn build-markdown-toc
  "Depth is a measure of how many spaces we need
   Get the markdown file for a generic based on the api string being read in"
  [depth content link]
  (str (apply str (repeat depth " ")) (format "* [%s](#%s)\n" link content)))

(defn get-table-of-contents-data
  "Parses out the toc information to build the markdown"
  ([table-of-content-headers doc-type]
   (def doc doc-type)
   (let [depth (if (= doc-type "ingest") (ingest-depth-map (count (re-seq #"#" table-of-content-headers))) (depth-map (count (re-seq #"#" table-of-content-headers))))
         link (peek (re-find #"=\"(.*)\">" table-of-content-headers))
         content (peek (re-find #">\s+([^<]*)$" table-of-content-headers))]
     (build-markdown-toc depth link content))))

(defn addSalt-to-table-of-contents-while-reading-gen-file
  "Return the specific schema's documentation files given the schema keyword name and version number.
   if the file cannot be read, return an empty string which will have no impact on the API document.
   Parameters:
   * file-name: [ingest | search]
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string
   TODO: If filename is ingest use one template, if filename is search use a different template"
  [file-name generic-keyword generic-version]
  (try
    (def stuff file-name)
    (-> "schemas/%s/v%s/%s.md"
        (format (name generic-keyword) generic-version (name file-name))
        (io/resource)
        (slurp)
        (get-table-contents-headers-from-markdown)
        (as-> xs (map #(get-table-of-contents-data % (name file-name)) xs))
        ;;Returns the depth
        ;(as-> xs (map #(depth-map (count (re-seq #"#" %))) xs))
        ;;Returns the link
        ;(as-> xs (map #(peek (re-find #"=\"(.*)\">" %)) xs))
        ;;Returns the content
        ;(as-> xs (map #(peek (re-find #">\s+([^<]*)$" %)) xs))
        ;(as-> xs (map #(str % "</a><ul>") xs))
        (string/join)
        (as-> xs (if (= file-name "ingest") (str (fill-in-generic-name "* %uc-plural-generic%\n    * /providers/\\<provider-id>/%plural-generic%/\\<native-id>\n" (name generic-keyword)) xs) (str "" xs)))
        ;If we are ingest then we need to add in the other part
        ;; ingest (as-> xs (str "</ul></li></ul><li>%uc-plural-generic%<ul><li>/providers/&lt;provider-id&gt;%plural-generic%&lt;native-id&gt;<ul>" xs))
        ;(as-> xs (str "</ul></li>" xs))
        ;(string/replace #"</a><ul>$" "</a></li>")
        ;; (table-of-contents-html (name generic-keyword))
        )
    (catch Exception e (str ""))))



(defn all-generic-docs-toc
  "Parse over all of the generic documents and return their combined markdown as a string
  Parameters:
  * file-name: [ingest | search]
  Returns: string"
  [file-name]
  (seq (for [[k,v] (latest-approved-documents)] (addSalt-to-table-of-contents-while-reading-gen-file file-name k (str v)))))


(defn merge-all-generic-docs
  "Combines the generic documents into a single string"
  [header-list]
  (-> header-list
  ;; (as-> xs (map #(string/replace % #"#+\s+<a\s+name=\"" "<li><a href=\"#") xs))
  ;; (as-> xs (map #(string/replace % #"</a>\s" "") xs))
  ;(as-> xs (map #(str % "</a></li>") xs))
  ;(as-> xs (map #(str % "</a></li>") xs))
  (as-> xs (string/join xs))))

(defn retrieve-html-table-content
"gets the new combined Markdown"
[gen-doc]
 (merge-all-generic-docs (all-generic-docs-toc gen-doc)))

;If it is ingest then we'll have to do something vs the other I think most


;We need take the string and apppend the table of contents cover

; for each of these items:

; </ul></li></ul> <li>%uc-plural-generic%<ul><li>/providers/&lt;provider-id&gt;%plural-generic%&lt;native-id&gt;<ul>
;<li><a href="#%ITEM_NAME%>%ITEM_SUMMARY%</a></li>
; find out what is in the name tag the value vetween the \ characters
;

;So here we would replace all of the strings with the other strings?

;bad
;"</ul></li></ul><li>%uc-plural-generic%<ul><li>/providers/&lt;provider-id&gt;%plural-generic%&lt;native-id&gt;<ul><li><a href=\"#create-update-grid\"></a> Create / Update a Grid</a></li><li><a href=\"#delete-grid\"></a> Delete a Grid</a></li>"


;good
;</ul></li></ul> <li>%uc-plural-generic%<ul><li>/providers/&lt;provider-id&gt;%plural-generic%&lt;native-id&gt;<ul><li><a href="#create-update-%generic%">PUT - Create or update a %generic%.</a></li><li><a href="#delete-%generic%">DELETE - Delete a %uc-generic%.</a></li>
