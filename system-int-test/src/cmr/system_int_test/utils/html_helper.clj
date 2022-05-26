(ns cmr.system-int-test.utils.html-helper)

(defn find-element-by-type
  "Search a dom tree for all elements of a given type and return them."
  [e-type dom-node]
  (flatten (lazy-cat (when (= e-type (:tag dom-node))
                       [dom-node])
                     (when-let [children (:content dom-node)]
                       (map (partial find-element-by-type e-type)
                            children)))))

(defn find-element-by-id
  "Search a dom for an element with a given ID."
  [id dom-node]
  (let [tree (lazy-cat (when (= id (get-in dom-node [:attrs :id]))
                         [dom-node])
                       (when-let [children (:content dom-node)]
                         (map (partial find-element-by-id id)
                              children)))]
    (->> tree
         flatten
         (remove nil?)
         first)))
