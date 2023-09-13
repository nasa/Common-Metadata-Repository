(ns cmr.metadata-db.test.test-utils)

(defn remove-spaces-and-new-lines
  [input-str]
  (clojure.string/replace (clojure.string/replace input-str #"\n" "") #" " "")
  )
