(ns cmr.indexer.data.concepts.track
  "Contains functions for converting track passes into elastic documents")

(defn- tile->indexed-tiles
  "Converts a pass track tile into tiles for indexing.
  Track pass tile can be a Left, Right or Full tile, we index Full tile as both Left and
  Right tiles, so we can find it by searching with either Left or Right tile.
  e.g. 2F will be indexed as [2F 2L 2R]"
  [tile]
  (if-let [[_ tile-num] (re-matches #"(\d+)F" tile)]
    [(str tile-num "F") (str tile-num "L") (str tile-num "R")]
    [tile]))

(defn- pass->elastic-doc
  "Converts a track pass into the portion going in an elastic document"
  [track-pass]
  (let [{:keys [pass tiles]} track-pass]
    {:pass pass
     :tiles (mapcat tile->indexed-tiles tiles)}))

(defn passes->elastic-docs
  "Converts the track into elastic document of track passes"
  [track]
  (when-let [passes (:passes track)]
    (map pass->elastic-doc passes)))
