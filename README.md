After compiling this program, create a remap rule file, and use the following command syntax to run the program:

java -cp <bin folder> Main <input world path> <output world path> <remap rule file path>

The remap rule file is a text file with a list of IDs to change. It must look like this example:

<pre>
# ingots
item 20264:64 30067:0 #copper to IC2
item 20264:65 30067:1 #tin to IC2
item 20264:66 30067:6 #silver to IC2
item 20264:67 30067:5 #lead to IC2
item 20264:68 21256:28 #ferrous to GregTech nickel
item 20264:69 21256:27 #shiny to GregTech platinum
item 20264:70 21256:21 #electrum to GregTech
item 20264:71 21256:29 #invar to GregTech

# copper ore to IC2
block 1901:0 1:0
item 1901:0 3000:0

# tin ore to IC2
block 1901:1 1:0
item 1901:1 3001:0

# silver ore to Metallurgy
block 1901:2 1:0
item 1901:2 902:1

# lead ore to IC2
block 1901:3 1:0
item 1901:3 3003:0

# ferrous ore to GregTech nickel ore
block 1901:4 1:0
item 1901:4 3507:15
</pre>

Comments start with '#'. Blank lines are ignored.<br>
Each line has "block" or "item", then the old ID, then the new ID.<br>
If you don't specify metadata for the old ID (e.g "1901" instead of "1901:1") then it will match any metadata.<br>
If you don't specify metadata for the new ID, the metadata will not be changed.<br>

Important notes:

* You can remap blocks to air, but you cannot remap items to air. If you want to
  take away items from players, remap them to a useless block (e.g. dirt) instead.

* It is possible but unlikely that when remapping items, this tool will remap some piece
  of tile entity data that looks like an item, but isn't. This is because the tool
  does not and cannot (because of mods) know where items are stored, so it must guess.

* Remapping a block does not automatically remap the corresponding item.

* The number of threads used is hardcoded in a constant in Main.java. The default is 6.

* On very large worlds (servers) this tool can take upwards of 15 minutes. This does
  not depend on the amount of IDs changed - most of the time is reading and writing the
  world files. If you have a large world you should remap many IDs at once rather than
  one ID at a time.