[![Build Status](https://github.com/centic9/cover-the-world/actions/workflows/gradle-build.yml/badge.svg)](https://github.com/centic9/cover-the-world/actions)
[![Release](https://img.shields.io/github/release/centic9/cover-the-world.svg)](https://github.com/centic9/cover-the-world/releases)
[![GitHub release](https://img.shields.io/github/release/centic9/cover-the-world.svg?label=changelog)](https://github.com/centic9/cover-the-world/releases/latest)
[![Tag](https://img.shields.io/github/tag/centic9/cover-the-world.svg)](https://github.com/centic9/cover-the-world/tags)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.dstadler/cover-the-world/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/org.dstadler/cover-the-world) 
[![Maven Central](https://img.shields.io/maven-central/v/org.dstadler/cover-the-world.svg)](https://maven-badges.herokuapp.com/maven-central/org.dstadler/cover-the-world)

This project can be used to read tracks from GPX files and produce a web-page which
displays covered "tiles" on a world map with some advanced features not found on the
usual "tile hunting" websites.

## Introduction

The sport of physically visiting as many tiles (i.e. square areas) as possible on the 
map is often known as "tile-hunting".

This project can be used both as library in other projects and directly as a set of 
applications which process data so that you can visualize your covered "tiles" on a web page.

The flow of data is roughly as follows
`GPX files -> list of covered tiles (txt) -> GeoJSON file for map-display (js) -> HTML page`

It should be fairly easy to rebuild the map with data from your own tracks. Also adding more tracks over
time is supported by visualizing "new" tiles and also allowing to do the CPU-intensive tasks of
producing static images only for areas touched recently.

The processing and the map should scale up to large numbers of visited tiles

## Definitions

* `square`: a 1km x 1km square on the map
* `tile`: a square on the map with roughly a mile side-length, based on zoom level 14 of the map at https://www.openstreetmap.org/ and other derived mapping sites
* `largest square`: The largest area of tiles/squares that can be filled with a square
* `largest rectangle`: The largest area of tiles/squares that can be filled with a rectangle
* `largest cluster`: The largest area of tiles/squares that can be filled with a rectangle
* `new squares/tiles`: Each time the application runs and processes new GPX tracks, it allows to visualize which tiles
  have been newly covered
* `adjacent tiles`: For planning upcoming routes, it is useful if you see a grid on the map
  for tiles which are not covered yet. The code produces so-called "adjacent tiles" which 
  show a border around tiles "near" already covered ones, thus allowing you to see where 
  exactly you need to go when you are out hunting some more tiles 

## How does it look like?

You can directly open the provided file [leaflet-map.html](leaflet-map.html) locally in a web-browser to see how this looks like.
(It won't work on Github directly because it would need files to be served with proper content type).

A sample image with the two sample GPX tracks looks as follows 

<img src="https://raw.githubusercontent.com/centic9/cover-the-world/main/Cover%20the%20World.png" width="50%" alt="World map with some covered tiles"/>

You can also host the resulting files on a web-server to make your covered tiles available online.

### Features of the web page

On the left side:
* Shows OSM zoom level
* Allows to zoom in/out
* Loading indicator when data is still loading
* Allows to toggle "largest cluster"
* Allows to toggle "largest rectangle"
* Allows to toggle "largest square"
* Allows to toggle "new" squares/tiles
* Allows to display covered "squares", i.e. 1km x 1km areas
* Allows to display covered "tiles", i.e. aprox. 1mile x 1mile areas

On the right side:
* Menu button which allows to switch to different maps and enable/disable various overlays
* GPS toggle: if used on a device which has GPS signal, this enables a marker for your current position
* Search: Allows to use the "nomination" service of OSM to search for locations 

## Prepare with your own data

Either put GPX files into the directory `gpx` or replace the directory `gpx` with a symlink to your GPX files
or adjust the code to look elsewhere. 

## Fetching GPX tracks from Garmin Connect

A popular tool for fetching GPX tracks from Garmin Connect is [garmin-connect-export](https://github.com/pe-st/garmin-connect-export)

A sample commandline to fetch all GPX files from an account is as follows: 
`python3 gcexport.py --directory ~/.garmin --unzip --format gpx --username "${GARMIN_EMAIL}" --password "${GARMIN_PASS}" --count "all"`

## Run it

Run the application `org.dstadler.ctw.CoverTheWorld`, e.g. via Gradle:

`./gradlew updateFiles`

### Advanced uses

In order to prepare additional "static" tiles, you can run additional applications:
* `org.dstadler.ctw.tiles.CreateTileOverlaysFromTiles`: Create static overlay tiles (transparent PNGs with covered area in red)
* `org.dstadler.ctw.tiles.CreateTileOverlaysFromUTMRef`: Create static overlay squares (transparent PNGs with covered area in red)
* `org.dstadler.ctw.tiles.CreateAdjacentTileOverlaysFromTiles`: Create static overlay images for adjacent tiles
  (transparent PNGs with area with red border)
* `org.dstadler.ctw.tiles.CreateStaticTiles`: Create PNGs with OSM map image and the covered area combined into one image. 
  This requires a running OSM tile-server to provide map tiles, which is currently not described here, see
  [openstreetmap-tile-server](https://github.com/Overv/openstreetmap-tile-server) for a docker-based way to spin up a 
  tile-server which can render some parts of the world.

These PNGs can be useful to get the map of covered tiles displayed in other applications, 
at least the following are known to work:

* GpsPrune: Can use the static overlay tiles as 2nd map
* OsmAnd: Can use the static overlay tiles as 2nd map
* Komoot: It is possible to replace the map in the Komoot web application with the combined PNGs produced via `CreateStaticTiles`, 
  however this requires some more advanced scripting via e.g. TamperMonkey which is currently not (yet) described here

## Using it as a library

The code can also be included as a library in your own application, e.g. this might be useful if 
you want to automate things in some more advanced way.

### Add it to your project as Gradle dependency

    implementation 'org.dstadler:cover-the-world:1.+'

## Caveats

### Some computations are limited to UTM zone 33 for 1km x 1km squares

Currently, computing the largest squares is limited to a single zone of
the UTM coordinate system. By default zone "33" is used. You can adjust 
this in `org.dstadler.ctw.utils.Constants.ZONE` and re-run to produce the 
proper largest squares if you are located in another UTM zone.

This limitation does not apply for the 1mile x 1mile tiles.

The limitation is needed because the algorithms for computing distances 
would become much more complicated.

Would be cool to get rid of this limitation, let me know via issues/PRs if you have ideas
how to do a whole-world computation efficiently.

## Change it

### Grab it

    git clone git://github.com/centic9/cover-the-world

### Build it and run tests

	cd cover-the-world
	./gradlew check jacocoTestReport

### Release it

    ./gradlew --console=plain release && ./gradlew closeAndReleaseRepository
    
* This should automatically release the new version on MavenCentral
* Afterwards go to the [Github releases page](https://github.com/centic9/cover-the-world/releases) and add release-notes

## Support this project

If you find this library useful and would like to support it, you can [Sponsor the author](https://github.com/sponsors/centic9)

## Licensing

* cover-the-world is licensed under the [BSD 2-Clause License].

[BSD 2-Clause License]: https://www.opensource.org/licenses/bsd-license.php

* Currently includes a compiled version of [jCoords](https://github.com/xni06/JCoord/)
