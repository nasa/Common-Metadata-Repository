(ns cmr.common.xml.simple-xpath

  "Simple XPath is an XPath implementation that works against XML parsed with
  clojure.data.xml and against Clojure records.

  It has a limited support for the XPath specification. These are examples of
  XPaths that are supported. See the tests for a full set of example XPaths.

  * From root: /catalog/books/author
  * Within current context: author/name
  * Subselect by attribute equality: /catalog/books[@id='bk101']/author
  * Subselect by element equality: /catalog/books[price='5.95']/title
  * Subselect by child index: /catalog/books[1]/author
  * Subselect by range of child indexes /catalog/books[2..4]/author.
    Openended ranges like [2..] are supported as well. (Note this is not a
    standard XPath feature.) Indexes are 1 based arrays. Both start and end
    indexes are inclusive.

  Note that there will be undefined behavior if an unsupported XPath is used.

  Note that simple XPath does not support namespaces in XPaths. It ignores any
  XPath tag name prefixes i.e. /xsl:foo/xsl:bar is equivalent to /foo/bar.

  ## Using with XML:

  1. Create an XPath context with some XML using `create-xpath-context-for-xml`
  2. Parse the XPath using `parse-xpath`
  3. Evaluate the XPath against the XPath context using `evaluate`.

  ## Using with Clojure Data:

  1. Create an XPath context with some Clojure data using
     `create-xpath-context-for-data`
  2. Parse the XPath using `parse-xpath`
  3. Evaluate the XPath against the XPath context using `evaluate`.

  ### How XPaths against Clojure Data work

  An XPath like /catalog/books in XML would look for a root element called
  catalog with one or more subelements called books. When using with Clojure
  data element tags are treated like looking for keys in a set of nested maps.
  The first tag within data xpaths are ignored because it assumes the first
  tag is the name of the data. For example evaluating /catalog/books would
  work with `{:books [...]}` which assumes that the map is the catalog. The
  xpath would evaluate to the vector the `:books` key refers to.

  ## XPath Contexts

  XPath contexts are used as the input to evaluation and also are the output
  of evaluation. They contain three pieces of information: a type (data or
  xml), the root of the data, and the context. The type indicates whether the
  xpath context is used for Clojure data or parsed XML. The root of the data
  is kept so that XPaths against the root can be evaluated (/catalog/books).
  The context is used so that higher level XPaths may specify a location
  within the data to evaluate lower level XPaths against.

  The `:context` key always contains the result of an XPath evaluation."
  (:require
   [clojure.data.xml :as x]
   [clojure.string :as str]
   [cmr.common.util :as u])
  (:import
   (clojure.data.xml Element)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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

(defn- create-range-selector
  "Creates a selector that selects a range of selectors of a set of elements."
  [selector-str]
  (let [[_ start-str end-str] (re-matches #"(\d+)\.\.(\d*)" selector-str)
        ;; Start index will be 0 based inclusive
        start-index (dec (Long/parseLong start-str))
        ;; The end index is 1 based inclusive. We can treat the same number as 0 based exclusive.
        end-index (when-not (str/blank? end-str) (Long/parseLong end-str))]
    {:type :range-selector
     :start-index start-index
     :end-index end-index}))

(defn- parse-xpath-attrib-name
  "Returns a namespaced keyword like :foo/bar from an xpath namespaced
  attribute selector like \"foo:bar\"."
  [attrib-name]
  (let [[_ _ namespace-part name-part] (re-find #"((.*):)?(.*)" attrib-name)]
    (keyword namespace-part name-part)))

;; Needed for create the element value selector
(declare parse-xpath)

(defn- create-attrib-val-equality-selector
  "Creates a selector that selects elements with an attribute with a given
  value."
  [selector-str]
  (let [[_ xpath value] (re-matches #"(.+)='(.+)'" selector-str)
        parsed-xpath (parse-xpath xpath)]
    (when (not= (:source parsed-xpath) :from-context)
      (throw
        (Exception.
          (str "Nested XPath selectors can not be from the root. XPath: "
               xpath))))
    {:type :attrib-value-selector
     :selectors (:selectors parsed-xpath)
     :value value}))

(defn- create-attrib-val-substring-selector
  "Creates a selector that selects elements with an attribute with a given
  value that matches by substring."
  [selector-str]
  (let [[_ xpath value] (re-matches #"contains\((.+), *'(.+)'\)" selector-str)
        parsed-xpath (parse-xpath xpath)]
    (when (not= (:source parsed-xpath) :from-context)
      (throw
        (Exception.
          (str "Nested XPath selectors can not be from the root. XPath: "
               xpath))))
    {:type :attrib-value-selector
     :selectors (:selectors parsed-xpath)
     :value value
     :sub-str? true}))

(defn- create-elem-val-substring-selector
  "Creates a selector that selects elements which have a child element with a
  value that includes a substring."
  [selector-str]
  (let [[_ xpath element-value] (re-matches #"contains\((.+), *'(.+)'\)" selector-str)
        parsed-xpath (parse-xpath xpath)]
    (when (not= (:source parsed-xpath) :from-context)
      (throw
        (Exception.
          (str "Nested XPath selectors can not be from the root. XPath: "
               xpath))))
    {:type :element-value-selector
     :selectors (:selectors parsed-xpath)
     :element-value element-value
     :sub-str? true}))

(defn- create-elem-val-equality-selector
  "Creates a selector that selects elements which have a child element with a
  given value.

  Example: foo/bar[charlie/name='alpha']

  charlie/name='alpha' is the selector in that case. charlie/name is an xpath
  and alpha is the value that it will match on."
  [selector-str]
  (let [[_ xpath element-value] (re-matches #"(.+)='(.+)'" selector-str)
        parsed-xpath (parse-xpath xpath)]
    (when (not= (:source parsed-xpath) :from-context)
      (throw
        (Exception.
          (str "Nested XPath selectors can not be from the root. XPath: "
               xpath))))
    {:type :element-value-selector
     :selectors (:selectors parsed-xpath)
     :element-value element-value}))

(defn- create-elem-val-inequality-selector
  "Creates a selector that selects elements which have a child element with a
  value not equal to the given value.

  Example: foo/bar[charlie/name!='alpha']

  charlie/name!='alpha' is the selector in that case. charlie/name is an xpath
  and alpha is the value that it will match on."
  [selector-str]
  (let [[_ xpath element-value] (re-matches #"(.+)!='(.+)'" selector-str)
        parsed-xpath (parse-xpath xpath)]
    (when (not= (:source parsed-xpath) :from-context)
      (throw
        (Exception.
          (str "Nested XPath selectors can not be from the root. XPath: "
               xpath))))
    {:type :element-value-selector
     :selectors (:selectors parsed-xpath)
     :element-value element-value
     :not-equal? true}))

(defn- create-tag-name-selector
  "Creates a selector that selects elements with a specific tag name."
  [tag-name]
  ;; This removes namespaces from XPath tag names. Simple XPath does not
  ;; support them and simply ignores them. This is because it uses
  ;; clojure.data.xml which does not preserve namespaces after parsing :(
  (let [tag-name (keyword (last (str/split (str tag-name) #":")))]
    {:type :tag-selector
     :tag-name (keyword tag-name)}))

(defn- create-attribute-name-selector
  "Creates a selector that selects an attribute value with a specific name.
  This is usually the last part of an xpath since you can't go any further
  down."
  [attrib-name]
  ;; This changes namespaces in XPath attribute names. clojure.data.xml will
  ;; represent an attribute in a specific namespace like so :namespace
  ;; /attribute-name
  (let [attrib-name (keyword (str/replace (name attrib-name) #":" "/"))]
    {:type :attrib-selector
     :attrib-name (keyword attrib-name)}))

(def child-of-selector
  "A selector that selects the children of a set of elements."
  {:type :child-of})

(def current-context-selector
  "A selector that selects the current context."
  {:type :current-context})

(defn- parse-element-sub-selector
  "Parses an element selector that is within the square brackets of an xpath
  into a selector."
  [selector-str]
  (cond
    (re-matches #"\d+" selector-str)
    (create-nth-selector selector-str)

    (re-matches #"\d+\.\.\d*" selector-str)
    (create-range-selector selector-str)

    (re-matches #".*@.+='.+'" selector-str)
    (create-attrib-val-equality-selector selector-str)

    (re-matches #".+!='.+'" selector-str)
    (create-elem-val-inequality-selector selector-str)

    (re-matches #".+='.+'" selector-str)
    (create-elem-val-equality-selector selector-str)

    (re-matches #".*contains\(.*@.+" selector-str)
    (create-attrib-val-substring-selector selector-str)

    (re-matches #".*contains\(.+" selector-str)
    (create-elem-val-substring-selector selector-str)

    :else
    (throw
      (IllegalArgumentException.
        (str "Unrecognized selector string form in xpath: " selector-str)))))

(defn- parse-xpath-element
  "Parses an element of an XPath and returns a set of selectors from that
  element."
  [xpath-elem]
  (cond
    (= "/" xpath-elem)
    [child-of-selector]

    (= "." xpath-elem)
    [current-context-selector]

    :else
    (if-let [[_ attrib-name] (re-matches #"@(.+)" xpath-elem)]
      [(create-attribute-name-selector attrib-name)]
      (if-let [[_ tag-name element-selector-str] (re-matches
                                                   #"([^\[]+)(?:\[(.+)\])?"
                                                   xpath-elem)]
        (let [tag-name-selector (create-tag-name-selector (keyword tag-name))]
          (if element-selector-str
            [tag-name-selector
             (parse-element-sub-selector element-selector-str)]
            [tag-name-selector]))
        (throw
          (Exception.
            (str "XPath element was not recognized: " xpath-elem)))))))

(defn- join-split-selectors
  "When we split an XPath by / that inadvertently splits subselectors that
  contain XPaths. This function searches for subselectors that were split by
  finding non-closed subselectors and joining them back together."
  [parts]
  (loop [new-parts []
         parts parts
         joining-parts nil]
    (if-let [^String next-part (first parts)]
      (cond
        joining-parts
        ;; We're joining together parts of an XPath
        (if (.contains next-part "]")
          ;; We've reached the end
          (recur (conj new-parts (str/join (conj joining-parts next-part)))
                 (rest parts)
                 nil)
          ;; We've found another part to join together
          (recur new-parts (rest parts) (conj joining-parts next-part)))

        ;; Does an XPath contain an open brace but not a closing brace
        (and (.contains next-part "[")
             (not (.contains next-part "]")))
        ;; Look for the closing brace so we can join it back together
        (recur new-parts (rest parts) [next-part])

        :else
        (recur (conj new-parts next-part) (rest parts) joining-parts))
      (concat new-parts joining-parts))))

(defn- split-xpath->parsed-xpath
  "Takes an XPath split on slashes (/) and returns parsed xpath."
  [parts]
  (let [parts (join-split-selectors parts)
        [source parts] (if (= "/" (first parts))
                         [:from-root parts]
                         ;; We add an initial / here because an xpath like
                         ;; "books" is inherently within the top element.
                         [:from-context (cons "/" parts)])
        selectors (u/mapcatv parse-xpath-element parts)

        ;; This is a special case for when the last selector is an attribute
        ;; value. We remove the child of selector preceeding it since
        ;; attributes aren't in the children.
        last-selector (last selectors)
        selectors (if (= :attrib-selector (:type last-selector))
                    (if (> (count selectors) 2)
                      (conj (subvec selectors 0 (- (count selectors) 2))
                            last-selector)
                      [last-selector])
                    selectors)]
    {:source source
     :selectors selectors}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; XML processors

(defmulti process-xml-selector
  "Processes an XPath selector against a set of XML elements"
  (fn [elements selector]
    (:type selector)))

(defn- process-selectors
  "Applies multiple selectors to a set of elements"
  [source selectors processor-fn]
  (reduce processor-fn source selectors))

(defmethod process-xml-selector :child-of
  [elements _]
  (persistent!
    (reduce (fn [v element]
              (reduce conj! v (:content element)))
            (transient [])
            elements)))

(defmethod process-xml-selector :current-context
  [elements _]
  elements)

(defmethod process-xml-selector :tag-selector
  [elements {:keys [tag-name]}]
  (filterv #(= tag-name (:tag %)) elements))

;; Select the values of the attributes given by attrib-name
(defmethod process-xml-selector :attrib-selector
  [elements {:keys [attrib-name]}]
  (persistent!
    (reduce (fn [attrib-values element]
              (if-let [attrib-value (get-in element [:attrs attrib-name])]
                (conj! attrib-values attrib-value)
                attrib-values))
            (transient [])
            elements)))

;; Select the elements which have attributes with a attribute name and value
;; given
(defmethod process-xml-selector :attrib-value-selector
  [elements {:keys [selectors value sub-str?]}]
  (filterv (fn [element]
             (when-let [selected-value (first
                                         (process-selectors
                                           [element]
                                           selectors
                                           process-xml-selector))]
               (if sub-str?
                 (str/includes? selected-value value)
                 (= selected-value value))))
           elements))

(defmethod process-xml-selector :element-value-selector
  [elements {:keys [selectors element-value not-equal? sub-str?]}]
  (filterv (fn [element]
             (some (fn [selected-element]
                     (cond
                       not-equal? (not= (-> selected-element :content first) element-value) 
                       sub-str? (str/includes? (-> selected-element :content first) element-value)
                       :else (= (-> selected-element :content first) element-value)))
                   (process-selectors
                     [element] selectors process-xml-selector)))
           elements))

(defmethod process-xml-selector :nth-selector
  [elements {:keys [index]}]
  (if (seq elements)
    [(nth elements index)]
    []))

(defmethod process-xml-selector :range-selector
  [elements {:keys [start-index end-index]}]
  (if (seq elements)
    (let [elements-vec (vec elements)
          size (count elements-vec)]
      (if (< start-index size)
        (if end-index
          (subvec elements-vec start-index (min end-index size))
          (subvec elements-vec start-index))
        ;; It's past the end of the index
        []))
    []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data processors

(defn- as-vector
  "Returns data as a vector if it's not one already"
  [data]
  (cond
    (nil? data) []
    (vector? data) data
    (sequential? data) (vec data)
    :else [data]))

(defmulti process-data-selector
  "Processes an XPath selector against clojure data."
  (fn [elements selector]
    (:type selector)))

(defmethod process-data-selector :child-of
  [data _]
  data)

(defmethod process-data-selector :current-context
  [data _]
  data)

(defmethod process-data-selector :tag-selector
  [data {:keys [tag-name]}]
  (u/mapcatv #(-> % tag-name as-vector) (as-vector data)))

(defmethod process-data-selector :attrib-selector
  [data {:keys [attrib-name]}]
  (u/mapcatv #(-> % attrib-name as-vector) (as-vector data)))

(defmethod process-data-selector :attrib-value-selector
  [data {:keys [selectors value]}]
  (filterv (fn [d]
             (when-let [result (first (process-selectors
                                        [d] selectors process-data-selector))]
               (= (str result) value)))
           (as-vector data)))

(defmethod process-data-selector :element-value-selector
  [data {:keys [selectors element-value not-equal?]}]
  (filterv (fn [d]
             (some (fn [result]
                     (if not-equal?
                       (not= (str result) element-value)
                       (= (str result) element-value)))
                   (process-selectors [d] selectors process-data-selector)))
           (as-vector data)))

(defmethod process-data-selector :nth-selector
  [data {:keys [index]}]
  (if (seq data)
    [(nth (as-vector data) index)]
    []))

(defn- xpath-context?
  [x]
  (and (map? x)
       (:type x)
       (:root x)
       (:context x)))

(defmethod process-data-selector :range-selector
  [data {:keys [start-index end-index]}]
  (if (seq data)
    (let [data-vec (as-vector data)
          size (count data-vec)]
      (if (< start-index size)
        (if end-index
          (subvec data-vec start-index (min end-index size))
          (subvec data-vec start-index))
        ;; It's past the end of the index
        []))
    []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API

(defn create-xpath-context-for-xml
  "Creates an XPath context for evaluating XPaths from an XML string."
  [xml-str]
  (let [xml-root {:tag :root :attrs {} :content [(x/parse-str xml-str)]}]
    {:type :xml
     :root xml-root
     :context [xml-root]}))

(defn- element-context
  "Returns an XPath context for a single clojure.data.xml.Element record."
  [element]
  {:type :xml
   :context [element]})

(defn create-xpath-context-for-data
  "Creates an XPath context for evaluating XPaths from Clojure data"
  [data]
  {:type :data
   :root data
   :context [data]})

(defn context
  "Returns x as an XPath context (or itself if it is already an XPath
  context)."
  [x]
  (cond
    (xpath-context? x) x
    (string? x) (create-xpath-context-for-xml x)
    (instance? clojure.data.xml.Element x) (element-context x)
    :else (create-xpath-context-for-data x)))

(defn parse-xpath
  "Parses an XPath into a data structure that allows it to be evaluated."
  [xpath]
  (let [parsed-xpath (if (= xpath "/")
                       ;; A special case for returning the root element
                       (split-xpath->parsed-xpath ["/"])
                       ;; Normal case
                       (->> (str/split xpath #"/")
                            (interpose "/")
                            (remove #(= "" %))
                            split-xpath->parsed-xpath))]
    (assoc parsed-xpath :original-xpath xpath)))

(defmulti
  ^{:arglists '([xpath-context parsed-xpath])}
  evaluate-internal
  "Evaluates a parsed XPath against the given XPath context."
  (fn [xpath-context parsed-xpath]
    (:type xpath-context)))

(defmethod evaluate-internal :xml
  [xpath-context {:keys [source selectors original-xpath]}]
  (try
    (let [source-elements (cond
                            (= source :from-root)
                            [(:root xpath-context)]
                            (= source :from-context)
                            (:context xpath-context)
                            :else
                              (throw
                                (Exception.
                                  (str "Unexpected source:" (pr-str source)))))]
      (assoc xpath-context
             :context
             (process-selectors
               source-elements selectors process-xml-selector)))
    (catch Exception e
      (throw
        (Exception.
          (str "Error processing xpath: " original-xpath) e)))))

(defmethod evaluate-internal :data
  [xpath-context {:keys [source selectors original-xpath]}]
  (try
    (let [data (cond
                 (= source :from-root)
                 (:root xpath-context)

                 (= source :from-context)
                 (:context xpath-context)

                 :else
                 (throw
                   (Exception.
                     (str "Unexpected source:" (pr-str source)))))]
      (assoc xpath-context
             :context
             (process-selectors data selectors process-data-selector)))
    (catch Exception e
      (throw
        (Exception.
          (str "Error processing xpath: " original-xpath) e)))))

(defn evaluate
  "Returns the XPath context resulting from evaluating an XPath expression
  against XML or Clojure data. The given context may be an XPath context, an
  XML string, or a Clojure data structure. The given xpath-expression may be a
  string or a value as returned by parse-xpath."
  [ctx xpath-expression]
  (let [xpath-expression (if (string? xpath-expression)
                           (parse-xpath xpath-expression)
                           xpath-expression)]
    (evaluate-internal (context ctx) xpath-expression)))

(defn ^String text
  "Returns the text of all nodes selected in an XPath context."
  [context-or-node]
  (cond
    (string? context-or-node)
    context-or-node
    (xpath-context? context-or-node)
    (str/join (map text (:context context-or-node)))
    (seq? context-or-node)
    (str/join (map text context-or-node))
    (:content context-or-node)
    (str/join (map text (:content context-or-node)))))

(defn select*
  "Returns all elements matching the XPath expression."
  [context xpath]
  (seq (:context (evaluate context xpath))))

(defmacro ^Element select
  "Returns all elements matching the XPath expression. Perform work of parsing
  XPath at compile time if a string literal is passed in to improve
  performance at runtime."
  [context xpath]
  (if (string? xpath)
    (let [parsed (parse-xpath xpath)]
      `(select* ~context ~parsed))
    `(select* ~context ~xpath)))
