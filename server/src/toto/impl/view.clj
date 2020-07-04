(ns toto.impl.view)

(defn apply-fn-component
  "Takes a hiccup-form where the first argument is a function and applies it with the rest of the entries in form
  If the result is itself a function (form-2 component, in Reagent speak), then returns result."
  [form]
  (let [result (apply (first form) (rest form))]
    (if (fn? result)
      ;; Then a form 2 component, so immediately call inner return fn
      (apply result (rest form))
      ;; otherwise, assume hiccup and return results
      result)))

(defn compiled-form
  "Processes a form according to the given processors map, which maps tag keywords
  to a function for transforming the form."
  [processors [tag & _ :as form]]
  (let [tag (keyword tag)]
    (if-let [processor (get processors tag)]
      (processor form)
      form)))

(defn compile-tags
  [doc
   compilers]
  (clojure.walk/prewalk
    (fn [form]
      (cond
        ;; If we see a function, call it with the args in form
        (and (vector? form) (fn? (first form)))
        (apply-fn-component form)
        ;; apply compilers
        (vector? form)
        (compiled-form compilers form)
        ;; Else, assume hiccup and leave form alone
        :else form))
    doc))

(defn prep-for-live-view
  [doc {:keys [mode]}]
  [:div
   (if (map? doc)
     [(or mode :vega-lite) doc]
     (compile-tags doc {}))])