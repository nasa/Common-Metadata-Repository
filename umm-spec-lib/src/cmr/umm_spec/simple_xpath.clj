(ns cmr.umm-spec.simple-xpath
  "TODO"
  (:require [clojure.string :as str]
            [clojure.data.xml :as x]
            [cmr.common.util :as u]))

(defn- create-nth-matcher
  "TODO"
  [^String selector-str]
  {:post [(>= (:index %) 0)]}
  {:type :nth-matcher
   :index (dec (Long/parseLong selector-str))})

(defn- create-attrib-val-equality-matcher
  "TODO"
  [selector-str]
  (let [[_ attrib-name attrib-val] (re-matches #"@(.+)='(.+)'" selector-str)
        attrib-name (keyword attrib-name)]
    {:type :attrib-value-matcher
     :attrib-name attrib-name
     :attrib-val attrib-val}))

;; Needed for create the element value selector
(declare parse-xpath)

(defn- create-elem-val-equality-matcher
  "TODO"
  [selector-str]
  (let [[_ xpath element-value] (re-matches #"(.+)='(.+)'" selector-str)
        parsed-xpath (parse-xpath xpath)]
    (when (not= (:source parsed-xpath) :from-context)
      (throw (Exception. (str "Nested XPath selectors can not be from the root. XPath: " xpath))))
    {:type :element-value-matcher
     :selectors (:selectors parsed-xpath)
     :element-value element-value}))

(defn- create-tag-name-matcher
  "TODO"
  [tag-name]
  {:type :tag-matcher
   :tag-name (keyword tag-name)})

(def child-of-selector
  "TODO"
  {:type :child-of})

(defn- parse-element-selector
  "TODO"
  [selector-str]
  (cond
    (re-matches #"\d+" selector-str)
    (create-nth-matcher selector-str)

    (re-matches #"@.+='.+'" selector-str)
    (create-attrib-val-equality-matcher selector-str)

    (re-matches #".+='.+'" selector-str)
    (create-elem-val-equality-matcher selector-str)

    :else
    (throw (Exception. (str "Unrecognized selector string form in xpath:" selector-str)))))

(defn- parse-xpath-element
  "TODO"
  [xpath-elem]
  (if (= "/" xpath-elem)
    [child-of-selector]
    (if-let [[_ tag-name element-selector-str] (re-matches #"([^\[]+)(?:\[(.+)\])?" xpath-elem)]
      (let [tag-name-selector (create-tag-name-matcher (keyword tag-name))]
        (if element-selector-str
          [tag-name-selector (parse-element-selector element-selector-str)]
          [tag-name-selector]))
      (throw (Exception. (str "XPath element was not recognized:" xpath-elem))))))

(defn- split-xpath->selectors
  "TODO"
  [parts]
  (let [[source parts] (if (= "/" (first parts))
                         [:from-root parts]
                         ;; We add an initial / here so because an xpath like "books" is inherently within the
                         ;; top element
                         [:from-context (cons "/" parts)])
        selectors (u/mapcatv parse-xpath-element parts)]
    {:source source
     :selectors selectors}))

(defn- wrap-element
  "Wraps the element in a root element so that it can have an XPath evaluated against it."
  [elem]
  {:tag :root :attrs {} :content [elem]})

(defmulti process-xml-selector
  "TODO"
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

(defmethod process-xml-selector :tag-matcher
  [elements {:keys [tag-name]}]
  (filterv #(= tag-name (:tag %)) elements))

(defmethod process-xml-selector :attrib-value-matcher
  [elements {:keys [attrib-name attrib-val]}]
  (filterv (fn [{:keys [attrs]}]
             (= attrib-val (get attrs attrib-name)))
           elements))

(defmethod process-xml-selector :element-value-matcher
  [elements {:keys [selectors element-value]}]
  (filterv (fn [element]
             (when-let [selected-element (first (process-selectors
                                                  [element] selectors process-xml-selector))]
               (= (-> selected-element :content first) element-value)))
           elements))

(defmethod process-xml-selector :nth-matcher
  [elements {:keys [index]}]
  [(nth elements index)])

(defn as-vector
  "Returns data as a vector if it's not one already"
  [data]
  (cond
    (nil? data) []
    (vector? data) data
    :else [data]))

(defmulti process-data-selector
  "TODO"
  (fn [elements selector]
    (:type selector)))

(defmethod process-data-selector :child-of
  [data _]
  data)

(defmethod process-data-selector :tag-matcher
  [data {:keys [tag-name]}]
  (u/mapcatv #(-> % tag-name as-vector) (as-vector data)))

(defmethod process-data-selector :attrib-value-matcher
  [data {:keys [attrib-name attrib-val]}]
  (filterv #(= attrib-val (get % attrib-name))
           (as-vector data)))

(defmethod process-data-selector :element-value-matcher
  [data {:keys [selectors element-value]}]
  (filterv (fn [d]
             (when-let [result (first (process-selectors
                                        [d] selectors process-data-selector))]
               (= (str result) element-value)))
           (as-vector data)))

(defmethod process-data-selector :nth-matcher
  [data {:keys [index]}]
  [(nth (as-vector data) index)])

(defn parse-xpath
  "TODO"
  [xpath]
  ;; TODO reject xpaths with // at the beginning
  (if (= xpath "/")
    ;; A special case
    (split-xpath->selectors ["/"])
    (->> (str/split xpath #"/")
         (interpose "/")
         (remove #(= "" %))
         split-xpath->selectors)))

(defn parse-xml
  "Parses the XML to be ready for XPath evaluation"
  [xml-str]
  (let [xml-root (wrap-element (x/parse-str xml-str))]
    {:type :xml
     :root xml-root
     :context [xml-root]}))

(defn wrap-data-for-xpath
  "TODO
  come up with a new name for this"
  [data]
  ;; TODO fix this later if this is correct
  (let [data-root data #_{:root data}]
    {:type :data
     :root data-root
     :context [data-root]}))

(defmulti evaluate
  "TODO"
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
