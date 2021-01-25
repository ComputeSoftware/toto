(ns toto.core
  (:require
    [reagent.core :as r]
    [reagent.dom :as rd]
    [toto.views :as views]
    [toto.views.vega]))

(defn ^:no-doc live-view
  ;; should handle sharing data with nodes that need it?
  [doc]
  ;; prewalk spec, rendering special hiccup tags like :vega and :vega-lite, and potentially other composites,
  ;; rendering using the components above. Leave regular hiccup unchanged).
  ;; TODO finish writing; already hooked in below so will break now
  (let [live-view-kset (set (keys (methods views/live-view*)))]
    (clojure.walk/prewalk
      (fn [x]
        (if (and (coll? x) (live-view-kset (first x)))
          (views/live-view* {:type (first x)
                             :args (rest x)})
          x))
      doc)))

(comment
  ;; This is still a work in progress
  (defn ^:private render-leaflet-vega [dom-node]
    ;(.map leaflet dom-node)
    (let [m (.map leaflet "map")
          _ (.setView m (clj->js [51.505 -0.09]) 4)
          tile (.tileLayer leaflet
                 "https://maps.wikimedia.org/osm-intl/{z}/{x}/{y}.png"
                 (clj->js {:attribution "&copy; <a href=\"http://osm.org/copyright\">OpenStreetMap</a> contributors"}))

          _ (.addTo tile m)
          marker (.marker leaflet (clj->js [40.7128 -74.0059]))]
      ;(js/console.log (clj->js [40.7128 -74.0059]))
      (.addTo marker m)))
  ;(.bindPopup marker "a red-headed rhino")))

  ;; This is still a work in progress
  (defn ^:private leaflet-vega
    "WIP/Alpha wrapper around leaflet-vega"
    []
    (r/create-class
      {:component-did-mount  (fn [this]
                               (render-leaflet-vega (rd/dom-node this)))
       :component-did-update (fn [this [_]]
                               (render-leaflet-vega (rd/dom-node this)))
       :reagent-render       (fn []
                               [:div#map])})))


