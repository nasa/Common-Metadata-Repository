(ns cmr.sizing.util)

(def kb (Math/pow 2 10))
(def mb (Math/pow 2 20))
(def gb (Math/pow 2 30))

(defn kb->bytes
  [value]
  (* kb value))

(defn mb->bytes
  [value]
  (* mb value))

(defn bytes->mb
  [value]
  (/ value mb))

(defn bytes->gb
  [value]
  (/ value gb))
