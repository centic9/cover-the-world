[![Build Status](https://github.com/centic9/cover-the-world/actions/workflows/gradle-build.yml/badge.svg)](https://github.com/centic9/cover-the-world/actions)
[![Gradle Status](https://gradleupdate.appspot.com/centic9/cover-the-world/status.svg?branch=master)](https://gradleupdate.appspot.com/centic9/cover-the-world/status)
[![Release](https://img.shields.io/github/release/centic9/cover-the-world.svg)](https://github.com/centic9/cover-the-world/releases)
[![GitHub release](https://img.shields.io/github/release/centic9/cover-the-world.svg?label=changelog)](https://github.com/centic9/cover-the-world/releases/latest)
[![Tag](https://img.shields.io/github/tag/centic9/cover-the-world.svg)](https://github.com/centic9/cover-the-world/tags)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.dstadler/cover-the-world/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/org.dstadler/cover-the-world) 
[![Maven Central](https://img.shields.io/maven-central/v/org.dstadler/cover-the-world.svg)](https://maven-badges.herokuapp.com/maven-central/org.dstadler/cover-the-world)

This is a small library of code-pieces that can be used to read GPX files and produce a web-page which 
displays covered "tiles" on a world map.
 
## Usage

TBD...

## Using it

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
