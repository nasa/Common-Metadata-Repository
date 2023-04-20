(ns cmr.spatial.points-validation-helpers
  "Defines functions for validating shapes with points."
  (:require [cmr.spatial.math :refer :all]
            [primitive-math]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]
            [cmr.spatial.arc :refer [arc]]
            [cmr.spatial.point :as p]
            [cmr.spatial.vector :as cmr-vector]))
(primitive-math/use-primitive-operators)

(def elasticsearch-rounding-precision
  "The number of decimal points Elasticsearch can store after conversion to and from ordinates.
  See [[cmr.spatial.serialize/multiplication-factor]] for additional information."
  7)

(defn points-in-shape-validation
  "Validates the individual points of a shape"
  [{:keys [points]}]
  (mapcat (fn [[i point]]
            (when-let [errors (v/validate point)]
              (map (partial msg/shape-point-invalid i) errors)))
          (map-indexed vector points)))

(defn points->rounded-point-map
  "Combines together points that round to the same value. Takes a sequence of points and returns a
  map of rounded points to list of index, point pairs."
  [points]
  (reduce (fn [m [i point]]
            (let [rounded (p/round-point elasticsearch-rounding-precision point)]
              (update-in m [rounded] conj [i point])))
          {}
          (map-indexed vector points)))

(defn points->pairs
  "Returns a sequence of pairs where there is an overlap between the entries for each couple.
  Applies a transform if presented with one to each member of the pair.
  Separate from [[clojure.core/partition]]"
  ([points]
   (points->pairs points identity))
  ([points xform]
   (conj (for [idx (range (dec (count points)))]
           (vector (xform (nth points idx))
                   (xform (nth points (inc idx))))))))

(defn points->es-rounded-pairs
  "Returns a sequence of point pairs rounded to ES precision where there is an overlap between
  the entries for each couple."
  [points]
  (points->pairs points (partial p/round-point elasticsearch-rounding-precision)))

(defn duplicate-point-validation
  "Validates that the ring does not contain any duplicate or very close together points."
  [{:keys [points]}]

  ;; Create a map of the rounded points to a list of points that round that same value. If any of the
  ;; rounded points has more than one other point in the list then there are duplicates.
  (let [rounded-point-map (points->rounded-point-map points)
        duplicate-point-lists (->> rounded-point-map
                                   vals
                                   (filter #(> (count %) 1))
                                   ;; reversing lists of duplicate points to put points in indexed order
                                   ;; for more pleasing messages.
                                   (map reverse))]
    (map msg/duplicate-points duplicate-point-lists)))

(defn consecutive-antipodal-points-validation
  "Validates that the ring does not have any consecutive antipodal points"
  [{:keys [points]}]

  (let [indexed-points (map-indexed vector points)
        indexed-point-pairs (points->pairs points)
        antipodal-indexed-point-pairs (filter (fn [[[_ p1] [_ p2]]]
                                                (p/antipodal? p1 p2))
                                              indexed-point-pairs)]
    (map (partial apply msg/consecutive-antipodal-points)
         antipodal-indexed-point-pairs)))

(defn zero-vector-validation
  "Validates points don't overlap after conversion to ES compatible ordinates."
  [{:keys [points]}]
  (when-let [lossy-points (seq (->> points
                                    points->rounded-point-map
                                    (keep-indexed #(when (= (first %2) (second %2)) [%1 %2]))))]
    (msg/loss-of-precision-after-transmit-points lossy-points)))

(comment
  (def my-ring {:points [(p/point -68.43329486147037 45.30043197562824)
                         (p/point -68.43308650035235 45.300438512093194)
                         (p/point -68.43287919100635 45.300454651560045)
                         (p/point -68.43267382105878 45.300480324925815)
                         (p/point -68.43247126983502 45.30051542226642)
                         (p/point -68.43227240459623 45.30055979330757)
                         (p/point -68.4320780768267 45.30061324806764)
                         (p/point -68.43188911858947 45.30067555767103)
                         (p/point -68.43170633896486 45.3007464553275)
                         (p/point -68.43153052058635 45.30082563747465)
                         (p/point -68.43142796651378 45.30087736159398)
                         (p/point -68.42549949093026 45.30399391819323)
                         (p/point -68.42543393333905 45.30402931826716)
                         (p/point -68.42527424410777 45.30412400996674)
                         (p/point -68.42512367224863 45.30422586906003)
                         (p/point -68.42498286252923 45.30433445940849)
                         (p/point -68.42485241792872 45.304449316048995)
                         (p/point -68.42473289705445 45.30456994718469)
                         (p/point -68.4246248117506 45.304695836289554)
                         (p/point -68.42452862490495 45.30482644432053)
                         (p/point -68.42444474846627 45.304961212024594)
                         (p/point -68.424373541679 45.30509956233325)
                         (p/point -68.42431530954384 45.305240902832956)
                         (p/point -68.42427030151032 45.305384628302136)
                         (p/point -68.42423871040681 45.305530123301594)
                         (p/point -68.42422067161345 45.30567676481031)
                         (p/point -68.42421626248051 45.305823924893005)
                         (p/point -68.42422550199528 45.30597097338875)
                         (p/point -68.42424835069924 45.30611728060963)
                         (p/point -68.42428471085483 45.30626222003697)
                         (p/point -68.42433442686243 45.30640517100451)
                         (p/point -68.42439728592493 45.306545521356526)
                         (p/point -68.42447301895731 45.30668267006927)
                         (p/point -68.4245143901733 45.30674800875327)
                         (p/point -68.42657894816146 45.309873931303706)
                         (p/point -68.426578508709 45.309874637378755)
                         (p/point -68.4265073010545 45.310012989039045)
                         (p/point -68.42644906929816 45.3101543306409)
                         (p/point -68.42640406288781 45.310298056957656)
                         (p/point -68.4263724746451 45.31044355254682)
                         (p/point -68.4263544399376 45.310590194384716)
                         (p/point -68.42635003609838 45.31073735453465)
                         (p/point -68.42635928209202 45.31088440283545)
                         (p/point -68.42638213843227 45.31103070959993)
                         (p/point -68.42641850734877 45.311175648311476)
                         (p/point -68.42646823320464 45.31131859830691)
                         (p/point -68.42653110316044 45.31145894743453)
                         (p/point -68.4266068480846 45.31159609467595)
                         (p/point -68.42665107539744 45.31166573537678)
                         (p/point -68.42671645800245 45.31176417723505)
                         (p/point -68.42676052646073 45.31182789455194)
                         (p/point -68.42686099507499 45.31195689224987)
                         (p/point -68.42691957080535 45.31202389449099)
                         (p/point -68.42698450976975 45.31209534588915)
                         (p/point -68.42704944889815 45.312166797248054)
                         (p/point -68.42710308457069 45.31222387992999)
                         (p/point -68.42722655835043 45.31234252067141)
                         (p/point -68.42736076550179 45.312455209226876)
                         (p/point -68.42750513134717 45.312561463009594)
                         (p/point -68.42762805854935 45.31264185775351)
                         (p/point -68.42775690175517 45.31272178232575)
                         (p/point -68.42778788098686 45.31274075152333)
                         (p/point -68.42784956959278 45.312777095301875)
                         (p/point -68.42800406355798 45.312865807052845)
                         (p/point -68.42810516349223 45.31292151158992)
                         (p/point -68.42827613654885 45.31300585026208)
                         (p/point -68.42836601360699 45.31304567597577)
                         (p/point -68.42958611967322 45.3135665311674)
                         (p/point -68.42967466942592 45.313602972419126)
                         (p/point -68.42975776987315 45.31363478456655)
                         (p/point -68.4310566195864 45.31411454146187)
                         (p/point -68.43111135805542 45.31413601680374)
                         (p/point -68.43128408713622 45.314198996261695)
                         (p/point -68.43147510202242 45.314258174295716)
                         (p/point -68.43167119389024 45.31430841072412)
                         (p/point -68.43187152296171 45.31434949040291)
                         (p/point -68.43207523130651 45.31438123740264)
                         (p/point -68.43228144651705 45.314403515762415)
                         (p/point -68.43248928544597 45.31441623007185)
                         (p/point -68.43269785798958 45.31441932588031)
                         (p/point -68.43290627090144 45.31441278992993)
                         (p/point -68.43311363161894 45.31439665021233)
                         (p/point -68.4333190520876 45.31437097584891)
                         (p/point -68.43352165256518 45.31433587679467)
                         (p/point -68.43372056539086 45.31429150336682)
                         (p/point -68.43391493870233 45.31423804560132)
                         (p/point -68.43410394008477 45.31417573243842)
                         (p/point -68.43428676013717 45.31410483074177)
                         (p/point -68.43446261593918 45.31402564415538)
                         (p/point -68.4345415932265 45.313986203185586)
                         (p/point -68.4345442811134 45.31398481719073)
                         (p/point -68.43463344228738 45.31393712580588)
                         (p/point -68.43473819843513 45.31387500401316)
                         (p/point -68.43497049388299 45.3137558686151)
                         (p/point -68.43497052344289 45.31375585345497)
                         (p/point -68.4372667130463 45.31257820188215)
                         (p/point -68.43728950811507 45.312567936336144)
                         (p/point -68.43735750729573 45.31253412744813)
                         (p/point -68.44098280002572 45.310681114422245)
                         (p/point -68.44098503927533 45.3106800339339)
                         (p/point -68.44098779076836 45.31067870478566)
                         (p/point -68.44115590909897 45.310591562809186)
                         (p/point -68.44131558969823 45.31049684870541)
                         (p/point -68.44146614877167 45.31039496809177)
                         (p/point -68.44160694159821 45.310286357274606)
                         (p/point -68.44173736529164 45.31017148137944)
                         (p/point -68.44185686138134 45.3100508323593)
                         (p/point -68.44196491820419 45.30992492688712)
                         (p/point -68.44206107309432 45.30979430414266)
                         (p/point -68.4421449143637 45.30965952350317)
                         (p/point -68.4422160830638 45.30952116214758)
                         (p/point -68.44227427452168 45.30937981258424)
                         (p/point -68.44231923964335 45.30923608011358)
                         (p/point -68.44235078597829 45.309090580235654)
                         (p/point -68.44236877854254 45.308943936014025)
                         (p/point -68.44237314039447 45.308796775407984)
                         (p/point -68.44236385296277 45.30864972858288)
                         (p/point -68.44234095612404 45.308503425212116)
                         (p/point -68.4423045480303 45.30835849178049)
                         (p/point -68.44225478468661 45.30821554890179)
                         (p/point -68.4421918792819 45.308075208661684)
                         (p/point -68.44211610127363 45.30793807199643)
                         (p/point -68.44202777523316 45.30780472612052)
                         (p/point -68.4419272794543 45.30767574201232)
                         (p/point -68.44183071125705 45.30756795413973)
                         (p/point -68.43597704991467 45.30142595484759)
                         (p/point -68.43596138639963 45.30140967185345)
                         (p/point -68.43583791844503 45.30129104063812)
                         (p/point -68.43570372119245 45.301178362115955)
                         (p/point -68.43555936931303 45.30107211875645)
                         (p/point -68.43540548094745 45.30097276547232)
                         (p/point -68.43524271505937 45.30088072767199)
                         (p/point -68.43507176861334 45.300796399438816)
                         (p/point -68.43489337359108 45.30072014184391)
                         (p/point -68.43470829385747 45.30065228140083)
                         (p/point -68.43451732189038 45.30059310866768)
                         (p/point -68.43432127538833 45.30054287700381)
                         (p/point -68.43412099377032 45.300501801485)
                         (p/point -68.43391733458249 45.30047005798304)
                         (p/point -68.4337111698275 45.30044778241304)
                         (p/point -68.43350338223244 45.300435070151515)
                         (p/point -68.43329486147037 45.30043197562824)]})

  (zero-vector-validation my-ring)
  (duplicate-point-validation (update-in my-ring [:points] drop-last))
  )
