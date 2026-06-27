# motor-clj

Reduced-order traction-motor solver (`:rom-motor`) — air-gap shear sizing + loss-fraction efficiency → torque density, peak power, mass. A kami-emag magnetostatic FEA registers `:emag-fea` on the same contract.

Part of the clean-sheet vehicle-design / CAE stack (purpose-split shared libs).
Zero-dep portable `.cljc`. Run `clojure -M:test`.
