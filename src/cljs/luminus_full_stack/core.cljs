(ns luminus-full-stack.core)

(defn ^:dev/after-load mount-components []
  (let [content (js/document.getElementById "app")]
    (while (.hasChildNodes content)
      (.removeChild content (.-lastChild content)))
    (.appendChild content (js/document.createTextNode "Welcome to luminus-full-stack"))))

(defn init! []
  (mount-components))
