# Changelog

## 0.1.191

Fluid pipes now carry their full rate over any distance.

### Fixed

- **Fluid pipes carry their full rate at any length.** Each pipe used to pass on only half the
  difference to the next one, so the flow faded along the line: three ordinary pipes carried
  16 mB per tick and thirty barely half a millibucket. Fluid now flows to the consumer in full: the
  ordinary pipe carries 50 mB per tick (1 bucket per second) and the Advanced Fluid Pipe 100, however
  long the line. A line runs at its thinnest pipe, and a full tank nearby no longer cuts a farther
  one off.
