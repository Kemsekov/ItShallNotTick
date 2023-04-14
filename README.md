# ItShallNotTick
This fork is an extension of original ItShallNotTick.

Extending previous functionality, this modification adds another set of tick-cutting for entities.

For each entity type special optimizer keeps statistic of cpu-usage
over all ticks server process for some period of time.

For example:

zombies: 0.3
items: 0.2
creepers: 0.2
pigs: 0.3

Then if server is overloaded it will skips some ticks of entities proportionally to
their cpu usage. 
So for this example server will skip ticks for zombies and pigs more frequently than for items and creepers.

It averages server load and removes situation where some of frozen or lagging entities loads server so much that it
became unplayable.

When server is asking entity(for example zombie) to tick, for this entity tick to skip 
following requirements must be met:

1) Server tps is lower than tpsThreshold, defined in config.
2) If cpu usage for this current entity is above maxCpuUsagePerEntityType defined in config, then we skip it always.
  It allows to define lower-bound for any entity cpu-usage. So if maxCpuUsagePerEntityType is set to 0.2, it means
   any entity type cannot use more than 20% of cpu.
3) Else we skip current entity with a some random chance that proportional to it's cpu usage, 
  so if cpu usage for zombie is 0.15, we will skip it's ticking with 15% chance.

That's it!

Praise the LORD JESUS CHIRST!
