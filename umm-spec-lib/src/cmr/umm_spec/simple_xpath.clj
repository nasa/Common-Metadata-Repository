(ns cmr.umm-spec.simple-xpath
  "TODO"
  (:require [clojure.string :as str]
            [clojure.data.xml :as x]))

;; Based on https://github.com/candera/clojuredc-xml/blob/master/slides.org

;; TODO unit tests

(defn- matches?
  "TODO"
  [element step]
  (cond
    (keyword? step)
    (= step (:tag element))

    (map? step)
    (every? (fn [[attr-name attr-val]]
              (-> element
                :attrs
                (get attr-name)
                (= attr-val)))
            step)

    (vector? step)
    (every? (fn [substep]
              (matches? element substep))
            step)

    (ifn? step)
    (step element)))


(defn- select
  "TODO"
  [element path]
  (let [[head & more] path
        matches (->> element
                  :content
                  (filter :tag)
                  (filter #(matches? % head)))]
    (if more
      (reduce into [] (mapv #(select % more) matches))
      matches)))

(defn parse-xpath-element
  "TODO"
  [xpath-elem]
  (keyword xpath-elem))

(defn parse-xpath
  "TODO"
  [xpath]
  (let [xpath (str/replace xpath #"^/" "")]
    (mapv parse-xpath-element (str/split xpath #"/"))))

(defn- wrap-element
  "Wraps the element in a root element so that it can have an XPath evaluated against it."
  [elem]
  {:tag :root :attrs {} :content [elem]})

(defn parse-xml
  "Parses the XML to be ready for XPath evaluation"
  [xml-str]
  (wrap-element (x/parse-str xml-str)))

(defn evaluate
  "TODO"
  [element parsed-xpath]
  (select element parsed-xpath))


(comment


  (require '[clojure.data.xml :as x])

  (def parsed {:tag :root
               :attrs {}
               :content [(x/parse-str
                           "<results>
                             <room type='single'>
                               <rate price='234.00' qualifier='aarp' />
                               <rate price='250.00' />
                             </room>
                             <room type='queen'>
                               <rate price='350.0' qualifier='silver' />
                             </room>
                           </results>")]})


  ;; /foo/bar/quux =>
  (select parsed [:foo :bar :quux])
  (evaluate parsed "/foo/bar/quux")

  ;; /results/room[@type='queen']/rate =>
  (select parsed [:results [:room {:type "queen"}] :rate])




  (evaluate parsed "/results/room")



  ;; /results/room/rate/@price =>
  (map #(get-in % [:attrs :price])
       (select parsed [:results :room :rate]))

  (defn get-price
    [element]
    (Double. (-> element :content first :attrs :price)))

  ;; /results/room[rate/@price < 100] =>
  (select parsed [:results [:room #(< (get-price %) 250)]])



  )
