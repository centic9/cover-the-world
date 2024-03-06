[![Build Status](https://github.com/centic9/cover-the-world/actions/workflows/gradle-build.yml/badge.svg)](https://github.com/centic9/cover-the-world/actions)
[![Gradle Status](https://gradleupdate.appspot.com/centic9/cover-the-world/status.svg?branch=master)](https://gradleupdate.appspot.com/centic9/cover-the-world/status)
[![Release](https://img.shields.io/github/release/centic9/cover-the-world.svg)](https://github.com/centic9/cover-the-world/releases)
[![GitHub release](https://img.shields.io/github/release/centic9/cover-the-world.svg?label=changelog)](https://github.com/centic9/cover-the-world/releases/latest)
[![Tag](https://img.shields.io/github/tag/centic9/cover-the-world.svg)](https://github.com/centic9/cover-the-world/tags)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.dstadler/cover-the-world/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/org.dstadler/cover-the-world) 
[![Maven Central](https://img.shields.io/maven-central/v/org.dstadler/cover-the-world.svg)](https://maven-badges.herokuapp.com/maven-central/org.dstadler/cover-the-world)

This is a small library of code-pieces that can be used to read GPX files and produce a web-page which 
displays covered "tiles" on a world map.

## Definitions

* `square`: a 1km x 1km square on the map
* `tile`: a square on the map based on zoom level 14 of the map at https://www.openstreetmap.org/ and other derived mapping sites
* `largest square`: The largest area of tiles/squares that can be filled with a square
* `largest cluster`: The largest area of tiles/squares that can be filled with a rectangle
* `new squares/tiles`: Each time the application runs and processes new GPX tracks, it allows to visualize which tiles 
  have been newly covered

## Prepare

Either put GPX files into the directory `gpx` or replace `gpx` with a symlink to your GPX files
or adjust the code to look elsewhere. 

## Run it

Run the application `org.dstadler.ctw.CoverTheWorld`, e.g. via Gradle:

`./gradlew updateFiles`

## Using it as a library

The code can also be included as a library in your own application, e.g. if you want to automate
things in some way.

### Add it to your project as Gradle dependency

    compile 'org.dstadler:cover-the-world:1.+'

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
