# Workflow

The workflow for using Pink will depend very much on what kind of musical projects you are trying to build.  The following serves as a basic guide to try to explain what abstractions and parts of Pink you are likely to use depending on what you are trying to accomplish.  In general, users will:

* Write [Unit Generators](ugen.md) when they are writing low-level digital signal processing code. DSP-code may include things like oscillators, filters, and envelope generators.
* Use Unit Generators to assemble signal processing graphs.  In common practice, the total processing graph is consistent of sub-graphs, some of which is stable and always running (i.e. mixers with effects), while others are dynamically added and removed (i.e. processing per-note of an instrument). You can use the same Unit Generators to build either stable or dynamic sub-graphs.  At a high-level, you can think of assembling Unit Generators as ways to build your own effects modules and instruments.
* Use Control Functions to write non-signal-processing code that needs to run in sync with the engine. This is useful for things like clocks that will trigger other application code, or tying in your own event generation code (i.e. generative music) into the engine.
* Use Events for delayed actions.  This is useful for organizing and composing notes that will proceed at a given time (i.e. pre-composed music), as well as in realtime applications for [temporal recursion](http://extempore.moso.com.au/temporal_recursion.html). 

(Further use case driven examples to follow below.)
