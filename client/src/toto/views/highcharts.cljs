(ns toto.views.highcharts
  (:require
    [reagent.core :as reagent]
    [cljs-bean.core :as bean]
    ["highcharts" :as highcharts]
    ["highcharts/modules/histogram-bellcurve" :as histogram]
    ["highcharts-react-official" :as highcharts-react]
    ["highcharts/modules/no-data-to-display" :as no-data-plugin]
    [toto.views :as views]))

(no-data-plugin highcharts)

(highcharts/setOptions
  (bean/->js
    {:lang        {:thousandsSep ","
                   :noData       "No data."}
     :plotOptions {:pie {:allowPointSelect true
                         :showInLegend     true}}
     :credits     {:enabled false}}))

(def highcharts-react-impl (reagent/adapt-react-class highcharts-react/default))

(defn Chart
  [{:keys [options containerProps]}]
  ;; https://github.com/highcharts/highcharts-react#options-details
  [highcharts-react-impl {:highcharts     highcharts
                          :options        options
                          :containerProps containerProps}])

(defmethod views/live-view* :highcharts
  [{:keys [args]}]
  (into [Chart] args))