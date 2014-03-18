(ns qu.generate.static
  (:require [clojure.java.io :as io]
            [me.raynes.fs :as fs]))

(defn -main
  [& args]
  (let [resource-dir (fs/file (io/resource "static/"))
        out-dir (if-let [path (first args)]
                  (fs/file path)
                  (fs/file "resources/static"))]

    (when (fs/exists? out-dir)
      (println "Output dir exists. Delete first. Cancelling.")
      (System/exit 1))

    (fs/copy-dir resource-dir out-dir)
    (println "Qu static files copied into" (str out-dir) ".")))
