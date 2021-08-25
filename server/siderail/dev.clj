(ns dev
  (:require
    [toto.core :as toto]
    [clojure.pprint :as pp]))

;; Let Clojure warn you when it needs to reflect on types, or when it does math
;; on unboxed numbers. In both cases you should add type annotations to prevent
;; degraded performance.
(set! *warn-on-reflection* true)
;(set! *unchecked-math* :warn-on-boxed)


;; Here is some example usage you can play with at the repl
(comment

  ;; Start the plot server
  (toto/init! {::toto/port       10667
               ::toto/index-view {}})



  ;; define a function for generating some dummy data
  (defn play-data [& names]
    (for [n names
          i (range 20)]
      {:time i :item n :quantity (+ (Math/pow (* i (count n)) 0.8) (rand-int (count n)))}))

  ;; Define a simple plot, inlining the data
  (def line-plot
    {:data     {:values (play-data "monkey" "slipper" "broom")}
     :encoding {:x     {:field "time" :type "quantitative"}
                :y     {:field "quantity" :type "quantitative"}
                :color {:field "item" :type "nominal"}}
     :mark     "line"})

  ;; Render the plot to the 
  (toto/view! line-plot)
  (toto/view! [:div [:h1 "yo dawg"]])
  (toto/view! [:div
               [:h1 "What up pepes?"]
               [:vega-lite line-plot]])

  (toto/view! [:div
               [:h1 "What up pepes?"]
               [:highcharts {:options {:series (into []
                                                 (map (fn [[series-name data]]
                                                        {:name series-name
                                                         :data (map :quantity data)}))
                                                 (group-by
                                                   :item
                                                   (play-data "monkey" "slipper" "broom")))
                                       #_#_:chart {:type "line"}}}]])

  ;; We can also try publishing the plot like so (requires auth; see README.md for setup)
  (toto/publish! line-plot)
  ;; Then follow the vega-editor link.

  ;; Build a more intricate plot
  ;; (Note here also that we're doing the Right Thing (TM) and including the field types...)
  (def stacked-bar
    {:data     {:values (play-data "munchkin" "witch" "dog" "lion" "tiger" "bear")}
     :mark     "bar"
     :encoding {:x     {:field "time"
                        :type  "ordinal"}
                :y     {:aggregate "sum"
                        :field     "quantity"
                        :type      "quantitative"}
                :color {:field "item"
                        :type  "nominal"}}})

  (toto/view! stacked-bar)

  :end-examples)




