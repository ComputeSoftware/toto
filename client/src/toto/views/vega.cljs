(ns toto.views.vega
  (:require
    ["vega-embed" :as vegaEmbed]
    ["leaflet-vega" :as leafletVega]
    ["leaflet" :as leaflet]
    [toto.views :as views]
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

(defn Vega
  "Reagent component that renders vega"
  ([doc] (Vega doc {}))
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

(defn VegaLite
  "Reagent component that renders vega-lite."
  ([doc] (VegaLite doc {}))
  ([doc opts]
   ;; Which way should the merge go?
   (Vega doc (merge opts {:mode "vega-lite"}))))

(defmethod views/live-view* :vega [{:keys [args]}] (into [Vega] args))
(defmethod views/live-view* :vega-lite [{:keys [args]}] (into [VegaLite] args))