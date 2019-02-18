# KompicsTesting 
[ ![Download](https://api.bintray.com/packages/kompics/Maven/kompics-testing/images/download.svg) ](https://bintray.com/kompics/Maven/kompics-testing/_latestVersion)
A unit testing framework for components in [Kompics](http://kompics.sics.se/).

# Documentation

The release documentation can be found [here](http://kompics.sics.se/current/tutorial/testing/basics/basics.html).

### Current Version
`0.3.0` for Kompics `1.0.+`

### Dependencies
Kompics testing artefacts are hosted on bintray.
To fecth them, add the following to either your maven `settings.xml` or your projects `pom.xml`:
```xml
<repositories>
    <repository>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
        <id>bintray-kompics-Maven</id>
        <name>bintray</name>
        <url>https://dl.bintray.com/kompics/Maven</url>
    </repository>
</repositories>
```
