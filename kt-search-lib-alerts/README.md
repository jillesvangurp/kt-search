This module implements a very basic rule engine for alerting. This may move into a separate library at some point and is in no way essential to kt-search users.

Current status is that this is a work in progress. I'm vibe coding a quick solution to the problem that one of my clusters needs a simple alerting solution and I'd like to use kt-search for this. Vibe coding here means getting something decent up and running without a huge time investment. And this seems the kind of project that just involves a lot of mindless wheel reinvention. So highly suitable to do like this.

My goals:

- flexible rule definition using the query DSL to match documents
- if any rules match documents, they trigger a notification
- rules live in elasticsearch and are managed through the index repository
- use a flexible plugin based notification mechanism for delivery
- initial plugin will be send grid based
- deliver as a multiplatform library so people can write their own native/jvm/whatever program or script with rules that can be version controlled

This might evolve a bit as I work my way to getting this up and running. Timeboxing my effort.