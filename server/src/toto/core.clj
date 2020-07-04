(ns toto.core
  (:require
    [taoensso.timbre :as log]
    [toto.server :as server]
    [toto.impl.view :as impl.view]))

(defn init!
  [{::keys [port]}]
  (server/start!
    (cond-> {}
      port (assoc ::server/port port))))

(defn ^:deprecated start-server!
  "DEPRECATED. Use toto.core/init! instead."
  [& [port]]
  (init! {::port port}))

(defn view!
  "View the given doc in a web browser. Docs for which map? is true are treated as single Vega-Lite/Vega visualizations.
  All other values are treated as hiccup, and are therefore expected to be a vector or other iterable.
  This hiccup may contain Vega-Lite/Vega visualizations embedded like `[:vega-lite doc]` or `[:vega doc]`.
  You may also specify `:host` and `:port`, for server settings, and a `:mode` option, defaulting to `:vega-lite`, with `:vega` the alternate option.
  (Though I will note that Vega-Embed often catches when you pass a vega doc to a vega-lite component, and does the right thing with it.
  However, this is not guaranteed behavior, so best not to depend on it)"
  ([doc] (view! doc {}))
  ([doc {:keys [host port mode] :as opts}]
   (assert (server/web-server-started?) "Oz is not started yet!")
   (try
     (let [hiccup-doc (impl.view/prep-for-live-view doc opts)]
       ;; if we have a map, just try to pass it through as a vega form
       (server/send-all! [::view-doc hiccup-doc]))
     (catch Exception e
       (log/error "error sending plot to server:" e)
       (.printStackTrace e)))))