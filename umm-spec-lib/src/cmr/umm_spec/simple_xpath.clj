(ns cmr.umm-spec.simple-xpath
  "Simple XPath is an XPath implementation that works against XML parsed with clojure.data.xml
  and against clojure records.

  It has a limited support for the XPath specification. These are examples of XPaths that are
  supported. See the tests for a full set of example XPaths.

  * From root: /catalog/books/author
  * Withing current context: author/name
  * Subselect by attribute equality: /catalog/books[@id='bk101']/author
  * Subselect by element equality: /catalog/books[price='5.95']/title
  * Subselect by child index: /catalog/books[1]/author

  Note that there will be undefined behavior if an unsupported XPath is used.

  ## Using with XML:

  1. Create an XPath context with some XML using `create-xpath-context-for-xml`
  2. Parse the XPath using `parse-xpath`
  3. Evaluate the XPath against the XPath context using `evaluate`.

  ## Using with Clojure Data:

  1. Create an XPath context with some Clojure data using `create-xpath-context-for-data`
  2. Parse the XPath using `parse-xpath`
  3. Evaluate the XPath against the XPath context using `evaluate`.

  ### How XPaths against Clojure Data work

  An XPath like /catalog/books in XML would look for a root element called catalog with one or more
  subelements called books. When using with Clojure data element tags are treated like looking for
  keys in a set of nested maps. The first tag within data xpaths are ignored because it assumes the
  first tag is the name of the data. For example evaluating /catalog/books would work with
  `{:books [...]}` which assumes that the map is the catalog. The xpath would evaluate to the vector
  the `:books` key refers to.

  ## XPath Contexts

  XPath contexts are used as the input to evaluation and also are the output of evaluation. They
  contain three pieces of information: a type (data or xml), the root of the data, and the context.
  The type indicates whether the xpath context is used for clojure data or parsed XML. The root of
  the data is kept so that XPaths against the root can be evaluated (/catalog/books). The context
  is used so that higher level XPaths may specify a location within the data to evaluate lower level
  XPaths against.

  The `:context` key always contains the result of an XPath evaluation."
  (:require [clojure.string :as str]
            [clojure.data.xml :as x]
            [cmr.common.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Selector creation functions
;; A selector is one part of an XPath.
;; foo/bar[1] would consist of the following selectors
;;
;; * elements with tag "foo"
;; * children of those elements
;; * elements with tag "bar"
;; * The first of those elements

(defn- create-nth-selector
  "Creates a selector that selects the nth item of a set of elements."
  [^String selector-str]
  {:post [(>= (:index %) 0)]}
  {:type :nth-selector
   :index (dec (Long/parseLong selector-str))})

(defn- create-attrib-val-equality-selector
  "Creates a selector that selects elements with an attribute with a given value."
  [selector-str]
  (let [[_ attrib-name attrib-val] (re-matches #"@(.+)='(.+)'" selector-str)
        attrib-name (keyword attrib-name)]
    {:type :attrib-value-selector
     :attrib-name attrib-name
     :attrib-val attrib-val}))

;; Needed for create the element value selector
(declare parse-xpath)

(defn- create-elem-val-equality-selector
  "Creates a selector that selects elements which have a child element with a given value.
  Example: foo/bar[charlie/name='alpha']
  charlie/name='alpha' is the selector in that case. charlie/name is an xpath and alpha is the value
  that it will match on."
  [selector-str]
  (let [[_ xpath element-value] (re-matches #"(.+)='(.+)'" selector-str)
        parsed-xpath (parse-xpath xpath)]
    (when (not= (:source parsed-xpath) :from-context)
      (throw (Exception. (str "Nested XPath selectors can not be from the root. XPath: " xpath))))
    {:type :element-value-selector
     :selectors (:selectors parsed-xpath)
     :element-value element-value}))

(defn- create-tag-name-selector
  "Creates a selector that selects elements with a specific tag name."
  [tag-name]
  {:type :tag-selector
   :tag-name (keyword tag-name)})

(def child-of-selector
  "A selector that selects the children of a set of elements."
  {:type :child-of})

(defn- parse-element-sub-selector
  "Parses an element selector that is within the square brackets of an xpath into a selector."
  [selector-str]
  (cond
    (re-matches #"\d+" selector-str)
    (create-nth-selector selector-str)

    (re-matches #"@.+='.+'" selector-str)
    (create-attrib-val-equality-selector selector-str)

    (re-matches #".+='.+'" selector-str)
    (create-elem-val-equality-selector selector-str)

    :else
    (throw (Exception. (str "Unrecognized selector string form in xpath:" selector-str)))))

(defn- parse-xpath-element
  "Parses an element of an XPath and returns a set of selectors from that element."
  [xpath-elem]
  (if (= "/" xpath-elem)
    [child-of-selector]
    (if-let [[_ tag-name element-selector-str] (re-matches #"([^\[]+)(?:\[(.+)\])?" xpath-elem)]
      (let [tag-name-selector (create-tag-name-selector (keyword tag-name))]
        (if element-selector-str
          [tag-name-selector (parse-element-sub-selector element-selector-str)]
          [tag-name-selector]))
      (throw (Exception. (str "XPath element was not recognized:" xpath-elem))))))

(defn- split-xpath->parsed-xpath
  "Takes an XPath split on slashes (/) and returns parsed xpath."
  [parts]
  (let [[source parts] (if (= "/" (first parts))
                         [:from-root parts]
                         ;; We add an initial / here so because an xpath like "books" is inherently within the
                         ;; top element
                         [:from-context (cons "/" parts)])
        selectors (u/mapcatv parse-xpath-element parts)]
    {:source source
     :selectors selectors}))


(defmulti process-xml-selector
  "Processes an XPath selector against a set of XML elements"
  (fn [elements selector]
    (:type selector)))

(defn- process-selectors
  "Applies multiple selectors to a set of elements"
  [source selectors processor-fn]
  (reduce (fn [input selector]
            (processor-fn input selector))
          source
          selectors))

(defmethod process-xml-selector :child-of
  [elements _]
  (persistent!
    (reduce (fn [v element]
              (reduce #(conj! %1 %2) v (:content element)))
            (transient [])
            elements)))

(defmethod process-xml-selector :tag-selector
  [elements {:keys [tag-name]}]
  (filterv #(= tag-name (:tag %)) elements))

(defmethod process-xml-selector :attrib-value-selector
  [elements {:keys [attrib-name attrib-val]}]
  (filterv (fn [{:keys [attrs]}]
             (= attrib-val (get attrs attrib-name)))
           elements))

(defmethod process-xml-selector :element-value-selector
  [elements {:keys [selectors element-value]}]
  (filterv (fn [element]
             (when-let [selected-element (first (process-selectors
                                                  [element] selectors process-xml-selector))]
               (= (-> selected-element :content first) element-value)))
           elements))

(defmethod process-xml-selector :nth-selector
  [elements {:keys [index]}]
  [(nth elements index)])

(defn- as-vector
  "Returns data as a vector if it's not one already"
  [data]
  (cond
    (nil? data) []
    (vector? data) data
    :else [data]))

(defmulti process-data-selector
  "Processes an XPath selector against clojure data."
  (fn [elements selector]
    (:type selector)))

(defmethod process-data-selector :child-of
  [data _]
  data)

(defmethod process-data-selector :tag-selector
  [data {:keys [tag-name]}]
  (u/mapcatv #(-> % tag-name as-vector) (as-vector data)))

(defmethod process-data-selector :attrib-value-selector
  [data {:keys [attrib-name attrib-val]}]
  (filterv #(= attrib-val (get % attrib-name))
           (as-vector data)))

(defmethod process-data-selector :element-value-selector
  [data {:keys [selectors element-value]}]
  (filterv (fn [d]
             (when-let [result (first (process-selectors
                                        [d] selectors process-data-selector))]
               (= (str result) element-value)))
           (as-vector data)))

(defmethod process-data-selector :nth-selector
  [data {:keys [index]}]
  [(nth (as-vector data) index)])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API

(defn create-xpath-context-for-xml
  "Creates an XPath context for evaluating XPaths from an XML string."
  [xml-str]
  (let [xml-root {:tag :root :attrs {} :content [(x/parse-str xml-str)]}]
    {:type :xml
     :root xml-root
     :context [xml-root]}))

(defn create-xpath-context-for-data
  "Creates an XPath context for evaluating XPaths from Clojure data"
  [data]
  {:type :data
   :root data
   :context [data]})

(defn parse-xpath
  "Parses an XPath into a data structure that allows it to be evaluated."
  [xpath]
  (if (= xpath "/")
    ;; A special case
    (split-xpath->parsed-xpath ["/"])
    (->> (str/split xpath #"/")
         (interpose "/")
         (remove #(= "" %))
         split-xpath->parsed-xpath)))

(defmulti evaluate
  "Evaluates a parsed XPath against the given XPath context."
  (fn [xpath-context parsed-xpath]
    (:type xpath-context)))

(defmethod evaluate :xml
  [xpath-context {:keys [source selectors]}]
  (let [source-elements (cond
                          (= source :from-root) [(:root xpath-context)]
                          (= source :from-context) (:context xpath-context)
                          :else (throw (Exception. (str "Unexpected source:" (pr-str source)))))]
    (assoc xpath-context
           :context (process-selectors source-elements selectors process-xml-selector))))

(defmethod evaluate :data
  [xpath-context {:keys [source selectors]}]
  (let [[data selectors] (cond
                           (= source :from-root)
                           ;; We drop the first two since it's always child of then the root element
                           ;; name. In the case of data these two both just refer to the name of the
                           ;; root element but there's not really a holder for that data.
                           [(:root xpath-context) (drop 2 selectors)]

                           (= source :from-context)
                           [(:context xpath-context) selectors]

                           :else
                           (throw (Exception. (str "Unexpected source:" (pr-str source)))))]
    (assoc xpath-context
           :context (process-selectors data selectors process-data-selector))))


(comment
  (:context (evaluate cmr.umm-spec.test.simple-xpath/sample-data-structure
              (parse-xpath "/catalog")))
  (:context (evaluate cmr.umm-spec.test.simple-xpath/sample-data-structure
              (parse-xpath "/catalog/books")))
  (:context (evaluate cmr.umm-spec.test.simple-xpath/sample-data-structure
              (parse-xpath "/catalog/books/genre")))
  (:context (evaluate cmr.umm-spec.test.simple-xpath/sample-data-structure
              (parse-xpath "/catalog/books[@id='bk101']/genre")))
  (:context (evaluate cmr.umm-spec.test.simple-xpath/sample-data-structure
              (parse-xpath "/catalog/books[price='5.95']/title")))

  (defn try-xpaths
    [& xpaths]
    (->> xpaths
         (map parse-xpath)
         (reduce #(evaluate %1 %2) cmr.umm-spec.test.simple-xpath/sample-xml)
         :context))

  (try-xpaths "/catalog/book[@id='bk101']/author")
  (try-xpaths "/")
  (try-xpaths "/catalog" "book" "author")
  (try-xpaths "/catalog/book[2]/author")
  (try-xpaths "/catalog/book[2]/price")
  (try-xpaths "/catalog/book[price='5.95']/title")


  (evaluate cmr.umm-spec.test.simple-xpath/sample-xml
            (parse-xpath "/catalog/book[price='5.95']/@id"))



  (parse-xpath "/catalog/book[price='5.95']/@id")




  )
