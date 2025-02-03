Pull requests are very welcome. Also, filing issues, doing documentation, or providing general feedback, etc. is much appreciated.

I try to be responsive processing PRs and critical issues. Otherwise, I have to balance working on this with other stuff so I may not respond right away. If this blocks you, reach out on twitter or via email jilles AT jillesvangurp.com so we can resolve things.

Where possible, please stick with conventions visible in the code for naming things, formatting things, etc. Bear in mind that this is a kotlin multiplatform project and that code needs to work (ideally) on both Elasticsearch and Opensearch. Features specific to either fork need to be clearly marked as such using the annotations. 

Also, please consider updating the manual so it covers your new feature. 

As this is a multiplatform library, please be careful adding dependencies; especially platform specific ones.

Before starting on any big pull requests, please file an issue and/or ping me on @jillesvangurp on twitter or several other platforms where I go by the same handle. This is so we can avoid conflicts/disappointment and coordinate changes. Especially for API changes, big refactorings, etc.
