(ns toto.core
  (:require
    ["vega-embed" :as vegaEmbed]
    ["leaflet-vega" :as leafletVega]
    ["leaflet" :as leaflet]
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [reagent.core :as r]
    [reagent.dom :as rd]))

(defn ^:no-doc embed-vega
  ([elem doc] (embed-vega elem doc {}))
  ([elem doc opts]
   (when doc
     (let [doc (clj->js doc)
           opts (merge {:renderer :canvas
                        ;; Have to think about how we want the defaults here to behave
                        :mode     "vega-lite"}
                       opts)]
       (-> (vegaEmbed elem doc (clj->js opts))
           (.catch (fn [err]
                     (js/console.log err))))))))

;; WIP; TODO Finish figuring this out; A little thornier than I thought, because data can come in so many
;; different shapes; Should clojure.spec this out:
;; * url
;; * named data
;; * vega vs lite
;; * data nested in layers
;; * other?
(defn ^:no-doc update-vega
  ([elem old-doc new-doc old-opts new-opts]
   (case
     ;; Only rerender from scratch if the viz specification has actually changed, or if always rerender is
     ;; specified
     (or (:always-rerender new-opts)
         (not= (dissoc old-doc :data) (dissoc new-doc :data))
         (not= old-opts new-opts))
     (embed-vega new-doc new-opts)
     ;; Otherwise, just update the data component
     ;; TODO This is the hard part to figure out
     ;(= ())
     ;()
     ;; Otherwise, do nothing
     :else
     nil)))

(defn vega
  "Reagent component that renders vega"
  ([doc] (vega doc {}))
  ([doc opts]
   ;; Is this the right way to do this? So vega component behaves abstractly like a vega-lite potentially?
   (let [opts (merge {:mode "vega"} opts)]
     (r/create-class
       {:component-did-mount  (fn [this]
                                (embed-vega (rd/dom-node this) doc opts))
        :component-did-update (fn [this old-argv old-state snapshot]
                                (let [[_ new-doc new-opts] (r/argv this)]
                                  (embed-vega (rd/dom-node this) new-doc new-opts)))
        :reagent-render       (fn [doc] [:div])}))))

(defn vega-lite
  "Reagent component that renders vega-lite."
  ([doc] (vega-lite doc {}))
  ([doc opts]
   ;; Which way should the merge go?
   (vega doc (merge opts {:mode "vega-lite"}))))


(def ^:private live-viewers-state
  (r/atom {:vega      vega
           :vega-lite vega-lite}))

(defn register-live-view
  [key component]
  (swap! live-viewers-state assoc key component))

(defn register-live-views
  [& {:as live-views}]
  (swap! live-viewers-state merge live-views))

(register-live-views
  :vega vega
  :vega-lite vega-lite)

(defn ^:no-doc live-view
  ;; should handle sharing data with nodes that need it?
  [doc]
  ;; prewalk spec, rendering special hiccup tags like :vega and :vega-lite, and potentially other composites,
  ;; rendering using the components above. Leave regular hiccup unchanged).
  ;; TODO finish writing; already hooked in below so will break now
  (let [live-viewers @live-viewers-state
        live-viewer-keys (set (keys live-viewers))]
    (clojure.walk/prewalk
      (fn [x] (if (and (coll? x) (live-viewer-keys (first x)))
                (into
                  [(get live-viewers (first x))]
                  (rest x))
                x))
      doc)))

;; TODO Rename this to live-view; But need to make sure to edit in the repl tooling application code as well,
;; since that's what actually uses this
(def ^:no-doc view-spec live-view)
;; should handle sharing data with nodes that need it?


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


